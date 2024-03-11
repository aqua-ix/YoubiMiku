package com.aqua_ix.youbimiku

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aqua_ix.youbimiku.BuildConfig.*
import com.aqua_ix.youbimiku.config.*
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
    private lateinit var database: FirebaseDatabase
    private lateinit var navMenu: Menu

    private var openAIPreviousResponse = ""

    private val job = SupervisorJob()
    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { value, throwable ->
            Log.e(TAG, throwable.message.toString())
        }
    private val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler)
    var openAITaskJob: Job? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        detectIntent = DetectIntent(this, getDialogFlowSession())

        initChatView()
        initBanner()
        initInterstitial()
        initRemoteConfig()
        initDatabase()
        showInAppReviewIfNeeded()

        setupOpenAI()
        setupChat()
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
        database = FirebaseDatabase.getInstance()
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
        mikuAccount = getMikuAccount()
        binding.chatView.setDateSeparatorFontSize(0F)
        binding.chatView.setInputTextHint(getString(R.string.input_text_hint))
        binding.chatView.setOnClickSendButtonListener(this)
        binding.chatView.setMessageMaxWidth(640)
    }

    private fun initBanner() {
        if (FLAVOR == "noAds") {
            return
        }
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

    private fun initInterstitial() {
        if (FLAVOR == "noAds") {
            return
        }
        ImobileSdkAd.registerSpotFullScreen(
            this,
            IMOBILE_PID,
            IMOBILE_MID,
            IMOBILE_INTERSTITIAL_SID
        )
        ImobileSdkAd.start(IMOBILE_INTERSTITIAL_SID)
    }

    private fun getMikuAccount(): User {
        return if (getAIModel(this) == (AIModelConfig.OPEN_AI.name)) {
            val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
            val name = "${getString(R.string.miku_name)}(GPT)"
            User(2, name, face)
        } else {
            val face = BitmapFactory.decodeResource(resources, R.drawable.normal)
            User(1, getString(R.string.miku_name), face)
        }
    }

    private fun showGreet(userName: String?) {
        val greeting = resources.getString(R.string.miku_nice_to_meet_you, userName)
        val welcome = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(greeting)
            .build()
        binding.chatView.receive(welcome)
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
        val reference = database.getReference("secrets/openai")

        reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val apiKey = dataSnapshot.child("apiKey").getValue(String::class.java)
                val orgId = dataSnapshot.child("orgId").getValue(String::class.java)

                apiKey?.let {
                    val config = OpenAIConfig(token = it, organization = orgId)
                    openAI = OpenAI(config)
                } ?: run {
                    Log.e("MainActivity", "apiKey is null.")
                    onOpenAIError()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("MainActivity", "Database error: ${databaseError.message}")
                onOpenAIError()
            }
        })
    }

    private fun setupChat() {
        if (getUserName(this).equals("")) {
            showUserNameDialog(false)
        } else {
            userAccount.setName(getUserName(this).toString())
            showGreet(getUserName(this))
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
        showGreet(getUserName(this))
    }

    private fun showAIModelDialog(cancelable: Boolean = true) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_ai_model))
            .setMessage(getString(R.string.setting_ai_model_message))
            .setPositiveButton(getString(R.string.setting_ai_model_openai)) { _, _ ->
                setAIModel(this, AIModelConfig.OPEN_AI)
                mikuAccount = getMikuAccount()
                if (::navMenu.isInitialized) {
                    navMenu.findItem(R.id.setting_language).isVisible = false
                }
            }
            .setNegativeButton(getString(R.string.setting_ai_model_dialogflow)) { _, _ ->
                setAIModel(this, AIModelConfig.DIALOG_FLOW)
                mikuAccount = getMikuAccount()
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

    private fun openInAppReview() {
        try {
            val reviewManager = ReviewManagerFactory.create(this)
            reviewManager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
                reviewManager.launchReviewFlow(this, reviewInfo)
                    .addOnSuccessListener {
                    }
            }
        } catch (e: Exception) {
        }
    }

    private fun getVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
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
            ImobileSdkAd.showAd(this, IMOBILE_INTERSTITIAL_SID)
            setOpenAIRequestCount(applicationContext, 0)
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
            if (result == null || result.isEmpty()) {
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
        }
    }

    public override fun onPause() {
        ImobileSdkAd.stop(IMOBILE_BANNER_SID)
        super.onPause()
    }

    public override fun onResume() {
        ImobileSdkAd.start(IMOBILE_BANNER_SID)
        super.onResume()
    }

    public override fun onDestroy() {
        detectIntent.resetContexts()
        scope.coroutineContext.cancel()
        ImobileSdkAd.activityDestroy()
        super.onDestroy()
    }

    companion object {
        val TAG = MainActivity::class.java.name.toString()
    }
}
