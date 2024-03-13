package com.aqua_ix.youbimiku

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aqua_ix.youbimiku.BuildConfig.*
import com.aqua_ix.youbimiku.config.*
import com.aqua_ix.youbimiku.database.*
import com.aqua_ix.youbimiku.databinding.ActivityMainBinding
import com.github.bassaer.chatmessageview.model.Message
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.ironsource.mediationsdk.*
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo
import com.ironsource.mediationsdk.integration.IntegrationHelper
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.sdk.LevelPlayBannerListener
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener
import jp.co.imobile.sdkads.android.FailNotificationReason
import jp.co.imobile.sdkads.android.ImobileSdkAd
import jp.co.imobile.sdkads.android.ImobileSdkAdListener
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity(), View.OnClickListener, DialogListener {
    private lateinit var userAccount: User
    private lateinit var mikuAccount: User

    private lateinit var binding: ActivityMainBinding
    private lateinit var detectIntent: DetectIntent
    private lateinit var openAI: OpenAI
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var appDatabase: AppDatabase
    private lateinit var navMenu: Menu
    private lateinit var ironSourceBannerLayout: IronSourceBannerLayout

    private var openAIPreviousResponse = ""

    private val job = SupervisorJob()
    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, throwable.message.toString())
        }
    private val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler)
    private var openAITaskJob: Job? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        detectIntent = DetectIntent(this, getDialogFlowSession())

        initChatView()
        initRemoteConfig()
        initDatabase()
        showInAppReviewIfNeeded()

        setupOpenAI()
        setupChat()
        setupAdNetwork()
    }

    private fun initRemoteConfig() {
        remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate()
    }

    private fun initDatabase() {
        firebaseDatabase = FirebaseDatabase.getInstance()
        appDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "youbimiku"
        ).build()
    }

    private fun onOpenAIError() {
        val error = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(getString(R.string.message_error_openai))
            .build()
        binding.chatView.receive(error)
        mikuAccount.setName(getString(R.string.miku_name))
        setAIModel(this, AIModelConfig.DIALOG_FLOW)
        if (::navMenu.isInitialized) {
            navMenu.findItem(R.id.setting_language).isVisible = true
        }
    }

    private fun initChatView() {
        val size = FontSizeConfig.getSize(getFontSizeType(this))
        setFontSize(size, binding.chatView)

        userAccount = User(0, null, null)
        mikuAccount = getMikuAccountFromAIModel()
        binding.chatView.setDateSeparatorFontSize(0F)
        binding.chatView.setInputTextHint(getString(R.string.input_text_hint))
        binding.chatView.setOnClickSendButtonListener(this)
        binding.chatView.setMessageMaxWidth(640)
    }

    private fun restoreMessages() {
        scope.launch {
            val messages = appDatabase.messageDao().getAll().map {
                return@map entityToMessage(
                    it, when (it.userId) {
                        0 -> userAccount
                        else -> getMikuAccountFromId(it.userId)
                    }
                )
            }

            withContext(Dispatchers.Main) {
                binding.chatView.getMessageView().init(messages)
            }

            if (messages.isEmpty()) {
                showGreet(userAccount.getName())
            }
        }
    }

    private fun setupAdNetwork() {
        if (FLAVOR == "noAds") {
            Log.d(TAG, "Ad network is disabled by flavor.")
            return
        }
        when (remoteConfig.getString(RemoteConfigKey.AD_NETWORK)) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                initImobileBanner()
                initImobileInterstitial()
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                initIronSource()
            }
        }
    }

    private fun initImobileBanner() {
        ImobileSdkAd.registerSpotInline(
            this,
            IMOBILE_PID,
            IMOBILE_MID,
            IMOBILE_BANNER_SID
        )
        ImobileSdkAd.start(IMOBILE_BANNER_SID)

        val imobileBannerLayout = FrameLayout(this)
        val imobileBannerLayoutParam: FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        imobileBannerLayoutParam.gravity = Gravity.TOP or Gravity.CENTER
        imobileBannerLayout.visibility = View.INVISIBLE
        addContentView(imobileBannerLayout, imobileBannerLayoutParam)
        ImobileSdkAd.showAd(this, IMOBILE_BANNER_SID, imobileBannerLayout, true)

        val mlp = binding.chatView.layoutParams as ViewGroup.MarginLayoutParams

        ImobileSdkAd.setImobileSdkAdListener(IMOBILE_BANNER_SID, object : ImobileSdkAdListener() {
            override fun onAdShowCompleted() {
                Log.d(TAG, "ImobileSdkAd($IMOBILE_BANNER_SID) onAdReadyCompleted")
                imobileBannerLayout.visibility = View.VISIBLE
                mlp.topMargin = imobileBannerLayout.height
            }

            override fun onFailed(reason: FailNotificationReason) {
                Log.d(TAG, "ImobileSdkAd($IMOBILE_BANNER_SID) onFailed: $reason")
                imobileBannerLayout.visibility = View.INVISIBLE
                mlp.topMargin = 0
            }
        })
    }

    private fun initImobileInterstitial() {
        ImobileSdkAd.registerSpotFullScreen(
            this,
            IMOBILE_PID,
            IMOBILE_MID,
            IMOBILE_INTERSTITIAL_SID
        )
        ImobileSdkAd.start(IMOBILE_INTERSTITIAL_SID)
    }

    private fun initIronSource() {
        initIronSourceBanner()
        initIronSourceInterstitial()
        IronSource.init(
            this,
            IRONSOURCE_APP_KEY,
            IronSource.AD_UNIT.BANNER,
            IronSource.AD_UNIT.INTERSTITIAL
        )
        IronSource.loadBanner(ironSourceBannerLayout)
        IronSource.loadInterstitial()
    }

    private fun initIronSourceBanner() {
        val size = ISBannerSize.BANNER
        ironSourceBannerLayout = IronSource.createBanner(this, size)
        ironSourceBannerLayout.apply {
            ironSourceBannerLayout.levelPlayBannerListener = object : LevelPlayBannerListener {
                override fun onAdLoaded(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner loaded: $adInfo")
                    val mlp = binding.chatView.layoutParams as ViewGroup.MarginLayoutParams
                    mlp.topMargin = ironSourceBannerLayout.height
                }

                override fun onAdLoadFailed(error: IronSourceError) {
                    Log.e(TAG, "IronSource banner load failed: $error")
                }

                override fun onAdClicked(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner clicked: $adInfo")
                }

                override fun onAdScreenPresented(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner screen presented: $adInfo")
                }

                override fun onAdScreenDismissed(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner screen dismissed: $adInfo")
                }

                override fun onAdLeftApplication(adInfo: AdInfo) {
                    Log.d(TAG, "IronSource banner left application: $adInfo")
                }
            }

            val layoutParams = FrameLayout.LayoutParams(
                WRAP_CONTENT, WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER
            }
            addContentView(ironSourceBannerLayout, layoutParams)
            if (BUILD_TYPE == "debug") {
                IntegrationHelper.validateIntegration(context);
            }
        }
    }

    private fun initIronSourceInterstitial() {
        IronSource.setLevelPlayInterstitialListener(object : LevelPlayInterstitialListener {
            override fun onAdReady(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial ready: $adInfo")
            }

            override fun onAdLoadFailed(error: IronSourceError?) {
                Log.e(TAG, "IronSource interstitial load failed: $error")
            }

            override fun onAdOpened(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial opened: $adInfo")
            }

            override fun onAdShowSucceeded(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial show succeeded: $adInfo")
            }

            override fun onAdShowFailed(error: IronSourceError?, adInfo: AdInfo) {
                Log.e(TAG, "IronSource interstitial show failed: $error, $adInfo")
            }

            override fun onAdClicked(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial clicked: $adInfo")
            }

            override fun onAdClosed(adInfo: AdInfo) {
                Log.d(TAG, "IronSource interstitial closed: $adInfo")
            }
        })
    }

    private fun getMikuAccountFromAIModel(): User {
        return if (getAIModel(this) == (AIModelConfig.OPEN_AI.name)) {
            val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
            val name = "${getString(R.string.miku_name)}(GPT)"
            User(2, name, face)
        } else {
            val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
            User(1, getString(R.string.miku_name), face)
        }
    }

    private fun getMikuAccountFromId(id: Int): User {
        return when (id) {
            1 -> {
                val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
                User(1, getString(R.string.miku_name), face)
            }

            2 -> {
                val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
                val name = "${getString(R.string.miku_name)}(GPT)"
                User(2, name, face)
            }

            else -> {
                val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
                User(1, getString(R.string.miku_name), face)
            }
        }
    }

    private fun showGreet(userName: String?) {
        val greeting = resources.getString(R.string.miku_nice_to_meet_you, userName)
        val welcome = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(greeting)
            .build()

        scope.launch {
            withContext(Dispatchers.Main) {
                binding.chatView.receive(welcome)
            }
            appDatabase.messageDao().insert(messageToEntity(welcome))
        }
    }

    private fun showInAppReviewIfNeeded() {
        val pref = SharedPreferenceManager
        pref.get(this, Key.LAUNCH_COUNT.name, 0).let {
            val current = it + 1
            pref.put(this, Key.LAUNCH_COUNT.name, current)

            // 起動回数が5回のときにInAppReviewを表示しカウントをリセット
            if (current >= 5) {
                openInAppReview()
                pref.put(this, Key.LAUNCH_COUNT.name, 0)
            }
        }
    }

    private fun setupOpenAI() {
        if (!remoteConfig.getBoolean(RemoteConfigKey.OPENAI_ENABLED)) {
            Log.e(TAG, "OpenAI is disabled by remote config.")
            onOpenAIError()
            return
        }

        val reference = firebaseDatabase.getReference("secrets/openai")
        reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val apiKey = dataSnapshot.child("apiKey").getValue(String::class.java)
                val orgId = dataSnapshot.child("orgId").getValue(String::class.java)

                apiKey?.let {
                    val config = OpenAIConfig(token = it, organization = orgId)
                    openAI = OpenAI(config)
                } ?: run {
                    Log.e(TAG, "apiKey is null.")
                    onOpenAIError()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database error: ${databaseError.message}")
                onOpenAIError()
            }
        })
    }

    private fun setupChat() {
        if (getUserName(this).equals("")) {
            showUserNameDialog(false)
        } else {
            userAccount.setName(getUserName(this).toString())
            restoreMessages()
        }

        if (getAIModel(this).equals("")) {
            showAIModelDialog(false)
        }
    }

    private fun showUserNameDialog(cancelable: Boolean = true) {
        val dialog = UserNameDialogFragment()
        val args = Bundle()
        args.putBoolean(Constants.ARGUMENT_CANCELABLE, cancelable)
        dialog.arguments = args
        dialog.setDialogListener(this)
        dialog.show(supportFragmentManager, UserNameDialogFragment::class.java.name)
    }

    override fun doPositiveClick() {
        userAccount.setName(getUserName(this).toString())
    }

    private fun showAIModelDialog(cancelable: Boolean = true) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_ai_model))
            .setMessage(getString(R.string.setting_ai_model_message))
            .setPositiveButton(getString(R.string.setting_ai_model_openai)) { _, _ ->
                setAIModel(this, AIModelConfig.OPEN_AI)
                mikuAccount = getMikuAccountFromAIModel()
                if (::navMenu.isInitialized) {
                    navMenu.findItem(R.id.setting_language).isVisible = false
                }
            }
            .setNegativeButton(getString(R.string.setting_ai_model_dialogflow)) { _, _ ->
                setAIModel(this, AIModelConfig.DIALOG_FLOW)
                mikuAccount = getMikuAccountFromAIModel()
                if (::navMenu.isInitialized) {
                    navMenu.findItem(R.id.setting_language).isVisible = true
                }
            }
            .setCancelable(cancelable)
            .show()
    }

    private fun showFontSizeDialog() {
        val index = FontSizeConfig.getType(getFontSizeType(this)).ordinal
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_font_size))
            .setSingleChoiceItems(R.array.font_size_config, index) { _, which ->
                setFontSize(FontSizeConfig.getSize(which), binding.chatView)
                SharedPreferenceManager.put(
                    this,
                    Key.FONT_SIZE.name,
                    FontSizeConfig.getType(which).name
                )
            }
            .setPositiveButton(getString(R.string.setting_dialog_accept), null)
            .show()
    }

    private fun showLanguageDialog() {
        val index = LanguageConfig.getType(getLanguage(this)).ordinal

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_language))
            .setSingleChoiceItems(R.array.language_config, index) { _, which ->
                SharedPreferenceManager.put(
                    this,
                    Key.LANGUAGE.name,
                    LanguageConfig.getType(which).name
                )
            }
            .setPositiveButton(getString(R.string.setting_dialog_accept), null)
            .show()
    }

    private fun clearMessageHistory() {
        scope.launch {
            appDatabase.messageDao().deleteAll()
        }

        binding.chatView.getMessageView().removeAll()
    }

    private fun openInAppReview() {
        try {
            val reviewManager = ReviewManagerFactory.create(this)
            reviewManager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
                reviewManager.launchReviewFlow(this, reviewInfo)
                    .addOnSuccessListener {
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "InAppReview error: ${e.message}")
        }
    }

    private fun openOfficialAccountIntent() {
        try {
            val uri = Uri.parse("https://twitter.com/youbimiku")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.official_account_error),
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        getAIModel(this)?.let {
            menu.findItem(R.id.setting_language).isVisible = it == AIModelConfig.DIALOG_FLOW.name
        }
        navMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.setting_user_name -> {
                showUserNameDialog()
                true
            }

            R.id.setting_ai_model -> {
                showAIModelDialog()
                true
            }

            R.id.setting_font_size -> {
                showFontSizeDialog()
                true
            }

            R.id.setting_language -> {
                showLanguageDialog()
                true
            }

            R.id.clear_message_history -> {
                clearMessageHistory()
                true
            }

            R.id.setting_official_account -> {
                openOfficialAccountIntent()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(v: View) {
        if (binding.chatView.inputText.isEmpty()) {
            return
        }
        val send = Message.Builder()
            .setUser(userAccount)
            .setRight(true)
            .setText(binding.chatView.inputText)
            .hideIcon(true)
            .build()
        sendRequest(binding.chatView.inputText)
        binding.chatView.send(send)
        binding.chatView.inputText = ""

        scope.launch {
            appDatabase.messageDao().insert(messageToEntity(send))
        }
    }

    private fun sendRequest(text: String) {
        Log.d(TAG, "request: $text")
        if (text.isBlank()) {
            Log.e(TAG, "Text should not be empty.")
            return
        }

        var count = getOpenAIRequestCount(applicationContext)

        when (getAIModel(this)) {
            AIModelConfig.OPEN_AI.name ->
                scope.launch {
                    if (openAITaskJob?.isActive == true) {
                        return@launch
                    }
                    openAITaskJob = launch {
                        openAITask(text)
                    }
                    setOpenAIRequestCount(applicationContext, ++count)
                }

            else ->
                scope.launch {
                    dialogFlowTask(text)
                }
        }

        if (count >= remoteConfig.getDouble(RemoteConfigKey.AD_DISPLAY_REQUEST_TIMES)) {
            Log.d(TAG, "Ad display request count: $count")
            setOpenAIRequestCount(applicationContext, 0)
            when (remoteConfig.getString(RemoteConfigKey.AD_NETWORK)) {
                RemoteConfigKey.AdNetwork.IMOBILE -> {
                    Log.d(TAG, "ImobileSdkAd.showAd")
                    ImobileSdkAd.showAd(this, IMOBILE_INTERSTITIAL_SID)
                }

                RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                    Log.d(TAG, "IronSource.showInterstitial")
                    IronSource.showInterstitial()
                    setOpenAIRequestCount(applicationContext, 0)
                }
            }
        }
    }

    private fun getDialogFlowSession(): String {
        val session = "youbimiku" + System.currentTimeMillis()
        Log.d(TAG, "getDialogFlowSession(): $session")
        return session
    }

    private suspend fun dialogFlowTask(text: String) {
        val response = detectIntent.send(text)
        Log.d(TAG, "response: $response")
        val receivedMessage = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(response)
            .build()
        withContext(Dispatchers.Main) {
            binding.chatView.receive(receivedMessage)
        }
        appDatabase.messageDao().insert(messageToEntity(receivedMessage))
    }

    @OptIn(BetaOpenAI::class)
    private suspend fun openAITask(text: String) {
        val maxLength = remoteConfig.getDouble(RemoteConfigKey.MAX_USER_TEXT_LENGTH).toInt()
        val sendText = if (text.length <= maxLength) text else text.substring(0, maxLength)
        Log.d(TAG, "sendText: $sendText")

        val configTokens = remoteConfig.getDouble(RemoteConfigKey.MAX_TOKENS).toInt()
        val maxTokens = if (configTokens == 0) null else configTokens

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(Constants.OPENAI_MODEL),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = getString(R.string.openai_system_prompt, userAccount.getName()),
                ),
                ChatMessage(
                    role = ChatRole.Assistant,
                    content = openAIPreviousResponse,
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = sendText
                )
            ),
            maxTokens = maxTokens
        )
        val completion = openAI.chatCompletion(chatCompletionRequest)
        Log.d(TAG, "completion: $completion")
        val choice = completion.choices.first()
        choice.message?.content.let {
            val response = it?.replace("^$|\n", "")
            Log.d(TAG, "response: $response")
            val result = if (choice.finishReason == "length") {
                "$response…"
            } else {
                response
            }
            Log.d(TAG, "result: $result")
            if (result.isNullOrEmpty()) {
                return
            }
            openAIPreviousResponse = result
            val receivedMessage = Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(result)
                .build()
            withContext(Dispatchers.Main) {
                binding.chatView.receive(receivedMessage)
            }
            appDatabase.messageDao().insert(messageToEntity(receivedMessage))
        }
    }

    public override fun onPause() {
        when (remoteConfig.getString(RemoteConfigKey.AD_NETWORK)) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                ImobileSdkAd.stop(IMOBILE_BANNER_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                IronSource.onPause(this)
            }
        }
        super.onPause()
    }

    public override fun onResume() {
        when (remoteConfig.getString(RemoteConfigKey.AD_NETWORK)) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                ImobileSdkAd.start(IMOBILE_BANNER_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                IronSource.onResume(this)
            }
        }
        super.onResume()
    }

    public override fun onDestroy() {
        detectIntent.resetContexts()
        scope.coroutineContext.cancel()
        when (remoteConfig.getString(RemoteConfigKey.AD_NETWORK)) {
            RemoteConfigKey.AdNetwork.IMOBILE -> {
                ImobileSdkAd.stop(IMOBILE_BANNER_SID)
                ImobileSdkAd.stop(IMOBILE_INTERSTITIAL_SID)
            }

            RemoteConfigKey.AdNetwork.IRONSOURCE -> {
                IronSource.destroyBanner(ironSourceBannerLayout)
            }
        }
        super.onDestroy()
    }

    companion object {
        val TAG = MainActivity::class.java.name.toString()
    }
}
