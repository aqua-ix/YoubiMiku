package com.aqua_ix.youbimiku

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.room.Room
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.exception.OpenAIIOException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aqua_ix.youbimiku.ads.AdController
import com.aqua_ix.youbimiku.ads.AdControllerFactory
import com.aqua_ix.youbimiku.config.AIModelConfig
import com.aqua_ix.youbimiku.config.FontSizeConfig
import com.aqua_ix.youbimiku.config.Key
import com.aqua_ix.youbimiku.config.LanguageConfig
import com.aqua_ix.youbimiku.config.RemoteConfigProvider
import com.aqua_ix.youbimiku.config.SharedPreferenceManager
import com.aqua_ix.youbimiku.config.UIModeConfig
import com.aqua_ix.youbimiku.config.getAIModel
import com.aqua_ix.youbimiku.config.getDisplayName
import com.aqua_ix.youbimiku.config.getFontSizeType
import com.aqua_ix.youbimiku.config.getLanguage
import com.aqua_ix.youbimiku.config.getMessageCountForAd
import com.aqua_ix.youbimiku.config.getSupportRequestCount
import com.aqua_ix.youbimiku.config.getUIMode
import com.aqua_ix.youbimiku.config.isSupporter
import com.aqua_ix.youbimiku.config.migrateMessageCountForAd
import com.aqua_ix.youbimiku.config.setAIModel
import com.aqua_ix.youbimiku.config.setFontSize
import com.aqua_ix.youbimiku.config.setMessageCountForAd
import com.aqua_ix.youbimiku.config.setSupporter
import com.aqua_ix.youbimiku.config.setSupportRequestCount
import com.aqua_ix.youbimiku.config.setUIMode
import com.aqua_ix.youbimiku.database.AppDatabase
import com.aqua_ix.youbimiku.database.MessageEntity
import com.aqua_ix.youbimiku.database.entityToMessage
import com.aqua_ix.youbimiku.database.messageToEntity
import com.aqua_ix.youbimiku.databinding.ActivityMainBinding
import com.github.bassaer.chatmessageview.model.Message
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.api.gax.rpc.UnavailableException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity(), View.OnClickListener, DialogListener {
    private lateinit var userAccount: User
    private lateinit var mikuAccount: User

    private lateinit var binding: ActivityMainBinding
    private lateinit var detectIntent: DetectIntent

    // 初期化はRemoteConfigとFirebaseの取得後になるため、未初期化を判別できるようにnullableで持つ。
    // メインスレッドで入れ替えてコルーチンからも読むため@Volatileにする
    @Volatile
    private var openAI: OpenAI? = null

    // 生成に使った認証情報。リスナは初回とキャッシュ・再同期で複数回呼ばれるので、
    // 同じ値での作り直しを避けるために覚えておく（メインスレッドからのみ触る）
    private var openAICredentials: Pair<String, String?>? = null
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var appDatabase: AppDatabase
    private lateinit var navMenu: Menu
    private val adController: AdController = AdControllerFactory.create()

    private var isAvatarMode = false

    // 広告の初期化はRemoteConfigの取得完了後になるため、onResume済みかどうかを保持する
    private var isActivityResumed = false

    // Firebaseのリスナは解除しないとActivityの破棄後も保持され続けるため、
    // onDestroyで外せるように参照を持つ
    private var openAIReference: DatabaseReference? = null
    private var openAIListener: ValueEventListener? = null
    private var avatarCredentialsReference: DatabaseReference? = null
    private var avatarCredentialsListener: ValueEventListener? = null

    private lateinit var webView: WebView

    // メインスレッドで受け取ってWebViewのスレッドから読むため@Volatileにする
    @Volatile
    private var avatarClientId = ""

    @Volatile
    private var avatarClientSecret = ""

    // 認証情報の到着を待っているアバターモードへの切り替え要求
    private var pendingAvatarMode = false

    // 認証情報の取得結果が空だったかどうか。値が変わらない限りリスナーは再発火しないので、
    // この場合は到着を待たずにエラーにする
    private var avatarCredentialsFailed = false
    private var pendingAudioPermissionRequest: PermissionRequest? = null

    // プロセス再生成前に開いていたアバターページ。次にアバターページを開くときに一度だけ使う
    private var savedAvatarUrl: String? = null

    private var openAIPreviousResponse = ""

    /**
     * ミクのアイコン。メッセージごとに読み込むと履歴の件数だけBitmapが作られ、
     * メモリを使い切ってプロセスが殺されてしまうため、1度だけ読み込んで全メッセージで共有する。
     */
    private val mikuIcon: Bitmap? by lazy { decodeMikuIcon() }

    // 読み込み済みのうち最も古いメッセージ。ここを起点にさらに古い履歴を読む
    private var oldestLoadedMessage: MessageEntity? = null

    // 古い履歴の読み込み中かどうか。判定と更新はメインスレッドに揃える
    private var isLoadingOlderMessages = false

    private val job = SupervisorJob()
    private val exceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            // AI応答の失敗はrunAITaskで受け止めて表示するので、ここに来るのは
            // 履歴の保存や報告など送信とは無関係な処理の失敗。応答待ちの状態を
            // 触ると実行中のリクエストの状態を壊すため、ログだけに留める
            Log.e(TAG, "Unhandled error", throwable)
        }
    // 履歴の読み書き・AI応答の取得・報告の送信はいずれもブロッキングI/Oなので、
    // スレッド数の少ないDispatchers.Defaultではなくディスク・通信向けのIOで実行する
    private val scope = CoroutineScope(Dispatchers.IO + job + exceptionHandler)

    // 応答待ちかどうか。判定と更新をメインスレッドに揃えて、連続送信で競合しないようにする
    private var isSending = false

    // 応答待ちを示すだけの吹き出し。履歴には残さないので消すために参照を持つ
    private var typingMessage: Message? = null

    private var actionBarSize = 0

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val request = pendingAudioPermissionRequest
            pendingAudioPermissionRequest = null
            if (isGranted) {
                if (request != null) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    webView.reload()
                }
            } else {
                request?.deny()
                showRecordAudioPermissionDeniedDialog()
            }
        }

    private val avatarModeBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val backForwardList = webView.copyBackForwardList()
            val previousIndex = backForwardList.currentIndex - 1
            val previousUrl = if (webView.canGoBack() && previousIndex >= 0) {
                backForwardList.getItemAtIndex(previousIndex)?.url
            } else {
                null
            }
            if (previousUrl?.startsWith(BuildConfig.AVATAR_BASE_URL) == true) {
                // アバターページ内の履歴を辿る
                webView.goBack()
            } else {
                toggleAvatarMode(false)
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setSupportActionBar(binding.toolbar)
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
        actionBarSize = android.util.TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        handleWindowInsets(view)

        // 認証情報の読み込みとgRPCクライアントの生成は初回送信時まで遅延されるため、
        // ここでの生成は起動時間に乗らない。Activityより長生きするので
        // applicationContextを渡して参照を残さないようにする
        detectIntent = DetectIntent(applicationContext, getDialogFlowSession())

        // 旧キーに残っている広告表示用のメッセージ数を引き継ぐ
        migrateMessageCountForAd(applicationContext)

        // プロセス再生成からの復帰時は、アバターモードだった場合だけ開いていたページを引き継ぐ
        savedAvatarUrl = if (savedInstanceState?.getBoolean(STATE_AVATAR_MODE) == true) {
            savedInstanceState.getString(STATE_AVATAR_URL)
        } else {
            null
        }

        initChatView()
        initDatabase()
        showInAppReviewIfNeeded()

        setupChat()
        setupWebView()

        // RemoteConfigに依存する初期化は取得完了後にまとめて行う
        initRemoteConfig()
    }

    private fun handleWindowInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = navBarInsets.left,
                right = navBarInsets.right,
                bottom = maxOf(navBarInsets.bottom, imeInsets.bottom),
            )
            windowInsets
        }
    }

    private fun initRemoteConfig() {
        RemoteConfigProvider.initialize { isFetched ->
            if (isDestroyed || isFinishing) {
                return@initialize
            }
            if (!isFetched) {
                // 取得に失敗してもデフォルト値・前回キャッシュで初期化を続行する
                Log.w(TAG, "Remote config is not fetched. Continue with default values.")
            }
            onRemoteConfigReady()
        }
    }

    private fun onRemoteConfigReady() {
        Log.d(TAG, "Remote config ready: adNetwork=${RemoteConfigProvider.adNetwork}, " +
                "adDisplayRequestTimes=${RemoteConfigProvider.adDisplayRequestTimes}, " +
                "openAIEnabled=${RemoteConfigProvider.isOpenAIEnabled}")
        setupOpenAI()
        setupAdNetwork()
        // RemoteConfigの値で表示を切り替えるメニューを作り直す
        invalidateOptionsMenu()
    }

    private fun initDatabase() {
        firebaseDatabase = FirebaseDatabase.getInstance()
        appDatabase = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "youbimiku"
        ).build()
    }

    private fun onOpenAIError() {
        openAI = null
        // 同じ認証情報でも作り直せるようにする
        openAICredentials = null
        // 応答待ちの状態はここでは触らない。実行中のリクエストはrunAITaskのfinallyが
        // 必ず解除するので、割り込んで解除するとあとから届いた応答が新しい会話に混ざる
        showErrorMessage(getString(R.string.message_error_openai))
        mikuAccount.setName(getString(R.string.miku_name))
        setAIModel(this, AIModelConfig.DIALOG_FLOW)
        if (::navMenu.isInitialized) {
            navMenu.findItem(R.id.setting_language).isVisible = true
        }
    }

    private fun initChatView() {
        val size = FontSizeConfig.getSize(getFontSizeType(this))
        setFontSize(size, binding.chatView)

        userAccount = User(USER_ID_ME, null, null)
        mikuAccount = getMikuAccountFromAIModel()
        setupHistoryPaging()
        binding.chatView.setDateSeparatorFontSize(0F)
        binding.chatView.setInputTextHint(getString(R.string.input_text_hint))
        binding.chatView.setOnClickSendButtonListener(this)
        binding.chatView.setMessageMaxWidth(640)

        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            binding.chatView.setOnBubbleLongClickListener(object :
                Message.OnBubbleLongClickListener {
                override fun onLongClick(message: Message) {
                    Log.d(TAG, "onLongClick: ${message.text}")
                    showActionSheet(message)
                }
            })
        }
    }

    private fun showActionSheet(message: Message) {
        val isUserMessage = message.user.getId() == userAccount.getId()
        val options = if (isUserMessage) {
            arrayOf(getString(R.string.copy_message), getString(R.string.resend_message))
        } else {
            arrayOf(getString(R.string.copy_message), getString(R.string.report_message))
        }
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                val text = message.text ?: return@setItems
                when {
                    which == 0 -> copyMessageToClipboard(text)
                    isUserMessage -> resendMessage(text)
                    else -> ReportUtil.showReportReasonDialog(
                        this,
                        text,
                        userAccount.getName() ?: "",
                        scope
                    )
                }
            }
            .show()
    }

    private fun copyMessageToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Message", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
    }

    /**
     * 直近の履歴を読み込んで表示する。
     *
     * 毎日使うアプリなので件数は増え続ける。全件を読むと起動が遅くなるだけでなく
     * メモリも食いつぶすため、まずは[HISTORY_PAGE_SIZE]件だけ読み、
     * それより古いものは引っ張って読み込む。
     */
    private fun restoreMessages() {
        scope.launch {
            // 新しい順に返るので、表示する古い順に並べ替える
            val entities = appDatabase.messageDao().getLatest(HISTORY_PAGE_SIZE).asReversed()
            val messages = toMessages(entities)

            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                binding.chatView.getMessageView().init(messages)
                oldestLoadedMessage = entities.firstOrNull()
                // 読み切っていない場合だけ、引っ張って遡れるようにする
                binding.chatView.setEnableSwipeRefresh(entities.size >= HISTORY_PAGE_SIZE)
            }

            if (entities.isEmpty()) {
                showGreet(userAccount.getName())
            }
        }
    }

    /**
     * 履歴のレコードを吹き出しに変換する。
     * 送信者ごとに[User]を作り回さず使い回して、件数分のオブジェクトを増やさない。
     */
    private fun toMessages(entities: List<MessageEntity>): List<Message> {
        val users = mutableMapOf<Int, User>()
        return entities.map { entity ->
            val user = users.getOrPut(entity.userId) {
                if (entity.userId == USER_ID_ME) userAccount else createMikuAccount(entity.userId)
            }
            entityToMessage(entity, user)
        }
    }

    /**
     * 上端で引っ張ったときに、表示しているより古い履歴を読み込めるようにする。
     */
    private fun setupHistoryPaging() {
        // 遡れる履歴があるか分かるまでは引っ張れないようにする
        binding.chatView.setEnableSwipeRefresh(false)
        binding.chatView.setOnRefreshListener { loadOlderMessages() }
    }

    private fun loadOlderMessages() {
        val oldest = oldestLoadedMessage
        if (oldest == null || isLoadingOlderMessages) {
            binding.chatView.setRefreshing(false)
            return
        }
        isLoadingOlderMessages = true
        scope.launch {
            val entities = try {
                appDatabase.messageDao()
                    .getOlderThan(oldest.sendTime, oldest.id, HISTORY_PAGE_SIZE)
                    .asReversed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load older messages", e)
                emptyList()
            }
            val messages = toMessages(entities)

            withContext(NonCancellable + Dispatchers.Main) {
                // 失敗しても読み込み中のまま固まらないよう、先に解除する
                isLoadingOlderMessages = false
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                binding.chatView.setRefreshing(false)
                if (entities.isEmpty()) {
                    binding.chatView.setEnableSwipeRefresh(false)
                    Toast.makeText(
                        this@MainActivity,
                        R.string.message_history_no_more,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }
                prependMessages(messages)
                oldestLoadedMessage = entities.first()
                if (entities.size < HISTORY_PAGE_SIZE) {
                    // 読み切ったので、これ以上引っ張れないようにする
                    binding.chatView.setEnableSwipeRefresh(false)
                }
            }
        }
    }

    /**
     * 古いメッセージを先頭に足す。
     *
     * ライブラリのMessageViewには先頭への追加も、再描画だけを行うAPIも無い。
     * init()で作り直すとアダプタが差し替わってリスナと表示位置を失うため、
     * 公開されているメッセージのリストに直接足したうえで、一時的なメッセージを
     * 出し入れして並べ替えと再描画を行わせる。
     */
    private fun prependMessages(messages: List<Message>) {
        val messageView = binding.chatView.getMessageView()
        val topPosition = messageView.firstVisiblePosition
        val topOffset = messageView.getChildAt(0)?.top ?: 0
        val countBefore = messageView.count

        messageView.messageList.addAll(0, messages)
        val trigger = Message.Builder()
            .setUser(userAccount)
            .setText("")
            .build()
        messageView.setMessage(trigger)
        messageView.remove(trigger)

        // 日付の区切りも増えるので、増えた行数を数えて読んでいた場所を画面に残す
        val added = messageView.count - countBefore
        messageView.setSelectionFromTop(topPosition + added, topOffset)
    }

    private fun setupAdNetwork() {
        val adNetwork = RemoteConfigProvider.adNetwork
        if (adNetwork == null) {
            Log.w(TAG, "Ad network is not configured.")
            return
        }
        adController.setup(
            this,
            adNetwork,
            actionBarSize,
            // 初期化がonResumeより後になる場合があるため、表示中であることをSDKに伝えられるようにする
            isActivityResumed,
        ) { height ->
            val mlp = binding.chatView.layoutParams as ViewGroup.MarginLayoutParams
            mlp.topMargin = height
            binding.chatView.requestLayout()
        }
    }

    private fun getMikuAccountFromAIModel(): User {
        return if (getAIModel(this) == (AIModelConfig.OPEN_AI.name)) {
            createMikuAccount(MIKU_USER_ID_OPEN_AI)
        } else {
            createMikuAccount(MIKU_USER_ID_DIALOG_FLOW)
        }
    }

    /**
     * 履歴に記録されたユーザーIDからミクのアカウントを作る。
     * アイコンは[mikuIcon]を共有するので、作られるのはUserオブジェクトだけ。
     */
    private fun createMikuAccount(id: Int): User {
        return if (id == MIKU_USER_ID_OPEN_AI) {
            User(id, "${getString(R.string.miku_name)}(GPT)", mikuIcon)
        } else {
            User(MIKU_USER_ID_DIALOG_FLOW, getString(R.string.miku_name), mikuIcon)
        }
    }

    private fun decodeMikuIcon(): Bitmap? {
        return decodeSampledBitmap(
            resources,
            R.drawable.normal,
            resources.getDimensionPixelSize(R.dimen.chat_icon_size)
        )
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
        if (RemoteConfigProvider.isOpenAIEnabled != true) {
            Log.e(TAG, "OpenAI is disabled by remote config.")
            onOpenAIError()
            return
        }

        val reference = firebaseDatabase.getReference("secrets/openai")
        val listener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val apiKey = dataSnapshot.child("apiKey").getValue(String::class.java)
                val orgId = dataSnapshot.child("orgId").getValue(String::class.java)

                val key = apiKey
                if (key == null) {
                    Log.e(TAG, "apiKey is null.")
                    onOpenAIError()
                    return
                }
                createOpenAI(key, orgId)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database error: ${databaseError.message}")
                onOpenAIError()
            }
        }

        // 呼び出しが複数回になっても重ねて登録しないよう、前のリスナを外してから登録する
        removeOpenAIListener()
        openAIReference = reference
        openAIListener = listener
        reference.addValueEventListener(listener)
    }

    /**
     * OpenAIのクライアントを生成する。
     *
     * 生成時にHTTPエンジンの探索（APKの走査）が走り数百msかかるため、
     * メインスレッドでは行わない。差し替えはメインスレッドに揃えて、
     * 送信可否の判定（canSendRequest）と食い違わないようにする。
     */
    private fun createOpenAI(apiKey: String, orgId: String?) {
        val credentials = apiKey to orgId
        if (openAICredentials == credentials) {
            // 同じ認証情報で作り直しても意味がないので、重い生成を繰り返さない
            return
        }
        openAICredentials = credentials
        scope.launch {
            val client = try {
                OpenAI(OpenAIConfig(token = apiKey, organization = orgId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ログだけに留めると準備中のまま送信できなくなるので、
                // apiKeyが無い場合と同じようにエラーとして扱う
                Log.e(TAG, "Failed to create the OpenAI client.", e)
                withContext(Dispatchers.Main) {
                    if (isDestroyed || isFinishing) {
                        return@withContext
                    }
                    // onOpenAIErrorが記録した認証情報を消すので、次の通知で作り直せる
                    onOpenAIError()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                if (openAICredentials != credentials) {
                    // 新しい認証情報での生成が始まっているので、古い結果は捨てる
                    Log.w(TAG, "The OpenAI credentials changed while creating the client.")
                    return@withContext
                }
                openAI = client
                Log.d(TAG, "OpenAI is ready.")
            }
        }
    }

    private fun removeOpenAIListener() {
        openAIListener?.let { openAIReference?.removeEventListener(it) }
        openAIListener = null
        openAIReference = null
    }

    private fun setupChat() {
        if (getUserName(this).equals("")) {
            showUserNameDialog(false)
        } else {
            userAccount.setName(getUserName(this).toString())
            restoreMessages()

            if (getUIMode(this) == "") {
                // 非初回起動ユーザー向けのアバターモード案内ダイアログ
                showAvatarModeInfoDialog()
            } else if (getAIModel(this) == "") {
                // 非初回起動ユーザー向けのAIモデル選択ダイアログ
                showAIModelDialog(false)
            }
        }

        val cfReference = firebaseDatabase.getReference("secrets/cloudflare")
        val cfListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                avatarClientId = dataSnapshot.child("clientId").getValue(String::class.java) ?: ""
                avatarClientSecret =
                    dataSnapshot.child("clientSecret").getValue(String::class.java) ?: ""

                if (avatarClientId.isEmpty() || avatarClientSecret.isEmpty()) {
                    Log.e(TAG, "Avatar credentials are missing.")
                    avatarCredentialsFailed = true
                    onAvatarCredentialsUnavailable()
                    return
                }

                avatarCredentialsFailed = false
                if (pendingAvatarMode) {
                    // 認証情報の到着を待っていた切り替え要求を再開する
                    pendingAvatarMode = false
                    toggleAvatarMode(true)
                } else if (getUIMode(this@MainActivity) != "") {
                    toggleAvatarMode(getUIMode(this@MainActivity) == UIModeConfig.AVATAR.name)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e(TAG, "Database error: ${databaseError.message}")
                avatarCredentialsFailed = true
                onAvatarCredentialsUnavailable()
            }
        }

        removeAvatarCredentialsListener()
        avatarCredentialsReference = cfReference
        avatarCredentialsListener = cfListener
        cfReference.addValueEventListener(cfListener)
    }

    private fun removeAvatarCredentialsListener() {
        avatarCredentialsListener?.let { avatarCredentialsReference?.removeEventListener(it) }
        avatarCredentialsListener = null
        avatarCredentialsReference = null
    }

    private fun onAvatarCredentialsUnavailable() {
        if (!pendingAvatarMode) {
            return
        }
        pendingAvatarMode = false
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, R.string.avatar_mode_error, Toast.LENGTH_SHORT).show()
    }

    private fun showAvatarModeInfoDialog() {
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.avatar_mode_message_title))
            .setMessage(getString(R.string.avatar_mode_message_text))
            .setPositiveButton(R.string.avatar_mode_message_accept) { _, _ ->
                toggleAvatarMode(true)

                // 初回起動時にアバターモードを選択した場合はチャットモードをOpenAIに設定
                setAIModel(this, AIModelConfig.OPEN_AI)
            }
            .setNegativeButton(R.string.avatar_mode_message_cancel) { _, _ ->
                setUIMode(this, UIModeConfig.CHAT)

                // 初回起動時にアバターモードを選択しなかった場合はAIモデル選択ダイアログを表示
                if (getAIModel(this).equals("")) {
                    showAIModelDialog(false)
                }
            }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = binding.webView
        webView.visibility = View.GONE

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // Add WebView client to handle page loading
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url.toString()
                if (url == BuildConfig.AVATAR_BASE_URL || !url.startsWith(BuildConfig.AVATAR_BASE_URL)) {
                    return super.shouldInterceptRequest(view, request)
                }

                val method = request?.method ?: METHOD_GET
                if (!method.equals(METHOD_GET, ignoreCase = true)) {
                    // インターセプトではリクエストボディを受け取れないため、
                    // メソッドを変えて壊してしまわないようWebViewにそのまま任せる
                    return super.shouldInterceptRequest(view, request)
                }

                var connection: HttpURLConnection? = null
                return try {
                    // connect()が失敗した場合もcatchで閉じられるよう、設定より先に代入する
                    connection = URL(url).openConnection() as HttpURLConnection
                    connection.apply {
                        requestMethod = METHOD_GET
                        // 応答が返らないまま読み込みが終わらなくならないよう上限を設ける
                        connectTimeout = WEB_REQUEST_CONNECT_TIMEOUT_MS
                        readTimeout = WEB_REQUEST_READ_TIMEOUT_MS
                        // 元のリクエストのヘッダを引き継いだうえで認証ヘッダを足す
                        request?.requestHeaders?.forEach { (name, value) ->
                            if (SKIPPED_REQUEST_HEADERS.none { it.equals(name, true) }) {
                                setRequestProperty(name, value)
                            }
                        }
                        setRequestProperty(HEADER_CF_CLIENT_ID, avatarClientId)
                        setRequestProperty(HEADER_CF_CLIENT_SECRET, avatarClientSecret)
                        connect()
                    }

                    // レスポンスヘッダーには Cloudflare Access の認証トークンが含まれるため出力しない
                    Log.d(
                        TAG,
                        "WebResourceResponse: ${connection.responseCode}, ${connection.contentType}"
                    )

                    // Content-Typeは "text/html; charset=utf-8" の形で返るため、
                    // MIMEタイプと文字コードに分けて渡す（そのまま渡すと種別が判別されない）
                    val contentType = connection.contentType
                    val mimeType = parseMimeType(contentType)
                    val charset = parseCharset(contentType) ?: DEFAULT_CHARSET
                    val statusCode = connection.responseCode
                    if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                        // エラー応答ではinputStreamが例外になる。認証ヘッダを付けずに
                        // WebViewへ取り直させても同じく失敗するので、ステータスコードと
                        // 本文をそのまま返して結果を伝える
                        WebResourceResponse(
                            mimeType,
                            charset,
                            statusCode,
                            connection.responseMessage?.takeIf { it.isNotBlank() }
                                ?: DEFAULT_REASON_PHRASE,
                            emptyMap(),
                            connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
                        )
                    } else {
                        WebResourceResponse(mimeType, charset, connection.inputStream)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebResourceResponse error", e)
                    // 応答を返せない場合は接続を残さない（成功時はWebViewが
                    // ストリームを読み終えるまで閉じられないのでここでは閉じない）
                    connection?.disconnect()
                    return super.shouldInterceptRequest(view, request)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) {
                    // サブリソース（計測用スクリプトなど）の失敗は無視する
                    Log.w(TAG, "Ignore loading error for sub resource: ${request?.url}")
                    return
                }
                Log.e(
                    TAG, "Avatar mode loading error: ${error?.errorCode}, ${error?.description}"
                )
                super.onReceivedError(view, request, error)
                // 一時的な失敗でモードを解除せず、再読み込みか戻るキーで復帰できるようにする
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, R.string.avatar_mode_error, Toast.LENGTH_SHORT)
                    .show()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val requestedResources = request.resources
                for (resource in requestedResources) {
                    if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            requestRecordAudioPermission(request)
                        }
                        return
                    }
                }
                request.deny()
            }
        }

        onBackPressedDispatcher.addCallback(this, avatarModeBackCallback)
    }

    private fun requestRecordAudioPermission(request: PermissionRequest? = null) {
        pendingAudioPermissionRequest = request
        // システムの権限ダイアログの前に、マイクを何のために使うのかを説明する
        AlertDialog.Builder(this)
            .setTitle(R.string.avatar_mode_record_audio_rationale_title)
            .setMessage(R.string.avatar_mode_record_audio_rationale_message)
            .setPositiveButton(R.string.avatar_mode_record_audio_continue) { _, _ ->
                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(R.string.setting_dialog_cancel) { _, _ ->
                denyPendingAudioPermissionRequest()
            }
            .setOnCancelListener { denyPendingAudioPermissionRequest() }
            .show()
    }

    private fun denyPendingAudioPermissionRequest() {
        pendingAudioPermissionRequest?.deny()
        pendingAudioPermissionRequest = null
    }

    private fun showRecordAudioPermissionDeniedDialog() {
        // 一度の拒否ならもう一度要求できる。二度拒否されると権限ダイアログが出ないので設定へ誘導する
        val canRequestAgain =
            shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.avatar_mode_needs_record_audio_permission)
            .setNegativeButton(R.string.setting_dialog_cancel, null)
        if (canRequestAgain) {
            builder.setMessage(R.string.avatar_mode_record_audio_denied_message)
                .setPositiveButton(R.string.avatar_mode_record_audio_retry) { _, _ ->
                    requestRecordAudioPermission()
                }
        } else {
            builder.setMessage(R.string.avatar_mode_record_audio_blocked_message)
                .setPositiveButton(R.string.avatar_mode_record_audio_open_settings) { _, _ ->
                    openAppPermissionSettings()
                }
        }
        builder.show()
    }

    private fun openAppPermissionSettings() {
        try {
            val uri = Uri.fromParts("package", packageName, null)
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open the app settings: $e")
        }
    }

    private fun loadAvatarPage() {
        val restoredUrl = savedAvatarUrl
        savedAvatarUrl = null
        if (restoredUrl?.startsWith(BuildConfig.AVATAR_BASE_URL) == true) {
            Log.d(TAG, "Restore the avatar page: $restoredUrl")
            loadAvatarUrl(restoredUrl)
            return
        }
        loadAvatarUrl(BuildConfig.AVATAR_BASE_URL)
    }

    private fun loadAvatarUrl(url: String) {
        val headers = mapOf(
            "CF-Access-Client-Id" to avatarClientId,
            "CF-Access-Client-Secret" to avatarClientSecret
        )
        webView.loadUrl(url, headers)
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

        if (getUIMode(this) == "") {
            showAvatarModeInfoDialog()
        }
    }

    private fun showAIModelDialog(cancelable: Boolean = true) {
        // RemoteConfig未取得のうちは選択肢を誤って隠さないようにtrue扱いにする
        val isOpenAIEnabled = RemoteConfigProvider.isOpenAIEnabled ?: true
        val aiModels = AIModelConfig.entries.filter {
            it != AIModelConfig.OPEN_AI || isOpenAIEnabled
        }.toTypedArray()
        val aiModelNames = aiModels.map { getDisplayName(this, it) }.toTypedArray()
        val currentIndex = aiModels.indexOfFirst { it.name == getAIModel(this) }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_ai_model))
            .setSingleChoiceItems(aiModelNames, currentIndex) { dialog, which ->
                setAIModel(this, aiModels[which])
                mikuAccount = getMikuAccountFromAIModel()
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                invalidateOptionsMenu()
            }
            .setPositiveButton(R.string.setting_dialog_accept, null)
            .setCancelable(cancelable)

        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = cancelable
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
        // 遡る先が無くなるので、読み込み位置と引っ張っての読み込みを戻す
        oldestLoadedMessage = null
        binding.chatView.setEnableSwipeRefresh(false)
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

    private fun openUrl(url: String) {
        try {
            val uri = Uri.parse(url)
            if (uri.scheme != "http" && uri.scheme != "https") return
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.support_url_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSupportDialog(requireUrls: Boolean = false) {
        val links = parseSupportLinks(RemoteConfigProvider.supportLinksJson)

        if (requireUrls && links.isEmpty()) return

        val view = layoutInflater.inflate(R.layout.dialog_support, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(R.string.support_later, null)
            .setNeutralButton(R.string.support_already) { _, _ ->
                setSupporter(applicationContext)
                invalidateOptionsMenu()
            }
            .create()

        val container = view.findViewById<android.widget.LinearLayout>(R.id.support_buttons_container)
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.support_button_height)
        ).also { it.bottomMargin = resources.getDimensionPixelSize(R.dimen.support_button_margin) }

        links.forEach { (name, url) ->
            val button = android.widget.Button(this).apply {
                layoutParams = params
                text = getString(R.string.support_button_label, name)
                isAllCaps = false
                setTextColor(android.graphics.Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.colorPrimary)
                )
                setOnClickListener { openUrl(url); dialog.dismiss() }
            }
            container.addView(button)
        }

        dialog.show()
    }

    private fun parseSupportLinks(json: String): List<Pair<String, String>> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val name = obj.optString("name")
                val url = obj.optString("url")
                if (name.isNotEmpty() && url.isNotEmpty()) name to url else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse support_links: ${e.message}")
            emptyList()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        getAIModel(this)?.let {
            menu.findItem(R.id.setting_language).isVisible =
                it == AIModelConfig.DIALOG_FLOW.name && !isAvatarMode
        }

        menu.findItem(R.id.setting_user_name).isVisible = !isAvatarMode
        menu.findItem(R.id.setting_ai_model).isVisible = !isAvatarMode
        menu.findItem(R.id.setting_font_size).isVisible = !isAvatarMode
        menu.findItem(R.id.clear_message_history).isVisible = !isAvatarMode

        menu.findItem(R.id.avatar_mode_reload).isVisible = isAvatarMode

        val hasSupportLinks = parseSupportLinks(RemoteConfigProvider.supportLinksJson).isNotEmpty()
        menu.findItem(R.id.support_developer).isVisible = hasSupportLinks

        menu.add(Menu.NONE, 1, Menu.NONE, R.string.avatar_mode)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        if (isAvatarMode) {
            menu.findItem(1)
                .setTitle(R.string.chat_mode)
                .setIcon(R.drawable.ic_chat)
        } else {
            menu.findItem(1)
                .setTitle(R.string.avatar_mode)
                .setIcon(R.drawable.ic_cube)
        }

        navMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                toggleAvatarMode()
                true
            }

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

            R.id.avatar_mode_reload -> {
                loadAvatarPage()
                true
            }

            R.id.setting_official_account -> {
                openOfficialAccountIntent()
                true
            }

            R.id.support_developer -> {
                showSupportDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleAvatarMode(enable: Boolean = !isAvatarMode) {
        if (enable && (avatarClientId.isEmpty() || avatarClientSecret.isEmpty())) {
            if (avatarCredentialsFailed) {
                // 取得結果が空だった場合は待っても届かないので、その場でエラーにする
                Log.e(TAG, "Avatar credentials are unavailable.")
                Toast.makeText(this, R.string.avatar_mode_error, Toast.LENGTH_SHORT).show()
                return
            }

            // 認証情報がまだ届いていないので、待っていることを示して到着後に切り替える
            Log.w(TAG, "Avatar credentials are not ready yet.")
            pendingAvatarMode = true
            binding.progressBar.visibility = View.VISIBLE
            Toast.makeText(this, R.string.avatar_mode_preparing, Toast.LENGTH_SHORT).show()
            return
        }

        isAvatarMode = enable
        setUIMode(this, if (isAvatarMode) UIModeConfig.AVATAR else UIModeConfig.CHAT)
        avatarModeBackCallback.isEnabled = isAvatarMode
        invalidateOptionsMenu()

        if (isAvatarMode) {
            // Switch to Avatar mode
            binding.chatView.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
            webView.visibility = View.VISIBLE
            loadAvatarPage()
        } else {
            // Switch back to chat mode
            binding.chatView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            webView.visibility = View.GONE
            webView.loadUrl("about:blank")
        }
    }


    override fun onClick(v: View) {
        val text = binding.chatView.inputText
        if (text.isBlank()) {
            // 空白だけの入力はリクエストにならないので、吹き出しも履歴も増やさない
            return
        }
        if (!canSendRequest()) {
            // 送信できない場合は入力内容を消さず、打ち直さずに再送信できるようにする
            return
        }

        val send = Message.Builder()
            .setUser(userAccount)
            .setRight(true)
            .setText(text)
            .hideIcon(true)
            .build()
        binding.chatView.send(send)
        binding.chatView.inputText = ""

        scope.launch {
            appDatabase.messageDao().insert(messageToEntity(send))
        }

        // 応答待ちの吹き出しが送信した吹き出しより先に並ばないよう、表示のあとに送る
        sendRequest(text)

        // 送信したメッセージ数を数えるのはこのタイミングだけ。
        // 送信されなかったメッセージや再送信を数えないようにする
        showInterstitialIfNeeded()
        showSupportDialogIfNeeded()
    }

    /**
     * 送信できる状態かどうかを返す。送信できない場合はその理由を画面に表示する。
     */
    private fun canSendRequest(): Boolean {
        if (isSending) {
            // 応答待ちの間に送ると、応答が来ないメッセージが履歴に残ってしまう
            Log.w(TAG, "The previous request is still running.")
            Toast.makeText(this, R.string.message_waiting_response, Toast.LENGTH_SHORT).show()
            return false
        }
        if (getAIModel(this) == AIModelConfig.OPEN_AI.name && openAI == null) {
            // RemoteConfigとFirebaseからの初期化がまだ終わっていない
            Log.w(TAG, "OpenAI is not initialized yet.")
            Toast.makeText(this, R.string.message_preparing, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * 応答が返らなかったメッセージを、履歴に増やさずもう一度送り直す。
     */
    private fun resendMessage(text: String) {
        if (!canSendRequest()) {
            return
        }
        sendRequest(text)
    }

    private fun sendRequest(text: String) {
        Log.d(TAG, "request: $text")
        if (text.isBlank()) {
            Log.e(TAG, "Text should not be empty.")
            return
        }

        // 応答待ちの判定と開始をメインスレッドで済ませてから起動する。
        // コルーチンの中で判定すると、連続タップでどちらもすり抜けることがある
        val aiModel = getAIModel(this)
        setSendingState(true)
        scope.launch {
            runAITask {
                when (aiModel) {
                    AIModelConfig.OPEN_AI.name -> openAITask(text)
                    else -> dialogFlowTask(text)
                }
            }
        }
    }

    /**
     * 応答待ちかどうかを切り替える。メインスレッドからのみ呼ぶ。
     */
    private fun setSendingState(isSending: Boolean) {
        this.isSending = isSending
        binding.chatView.setEnableSendButton(!isSending)
        if (isSending) {
            showTypingIndicator()
        } else {
            hideTypingIndicator()
        }
    }

    /**
     * ミクが応答を考えていることを示す吹き出しを表示する。
     * 応答待ちの間だけのものなので履歴（DB）には保存しない。
     */
    private fun showTypingIndicator() {
        if (typingMessage != null) {
            return
        }
        val typing = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(getString(R.string.message_typing))
            .build()
        typingMessage = typing
        binding.chatView.receive(typing)
    }

    private fun hideTypingIndicator() {
        val typing = typingMessage ?: return
        typingMessage = null
        binding.chatView.getMessageView().remove(typing)
    }

    /**
     * AI応答の取得を実行し、失敗した場合はエラーを会話上に表示する。
     * ここで受け止めないとCoroutineExceptionHandlerでログに落ちるだけになり、
     * ユーザーには応答が来ないことしか分からない。
     */
    private suspend fun runAITask(task: suspend () -> Unit) {
        try {
            task()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI request error", e)
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                // 応答待ちの吹き出しを消してからエラーを並べる。finallyでも解除するが、
                // 表示のたびに待ち状態が残らないようここで済ませる
                setSendingState(false)
                showErrorMessage(getErrorMessage(e))
            }
        } finally {
            // 失敗しても応答待ちのまま固まらないように必ず解除する。
            // scopeのキャンセル後でも実行できるようNonCancellableにする
            withContext(NonCancellable + Dispatchers.Main) {
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                setSendingState(false)
            }
        }
    }

    /**
     * ミク側の吹き出しとしてエラーを表示する。
     * 再起動後には意味を持たないため、履歴（DB）には保存しない。
     *
     * 応答待ちの吹き出しはsetSendingStateだけが出し入れする。ここで消すと、
     * リクエストと無関係なエラー（onOpenAIErrorなど）でも消えてしまい、
     * 応答待ちなのに何も起きていないように見える。
     */
    private fun showErrorMessage(text: String) {
        if (isDestroyed || isFinishing) {
            // Firebaseのリスナは破棄後にも呼ばれ得るので、ここでまとめて弾く
            return
        }
        val error = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(text)
            .build()
        binding.chatView.receive(error)
    }

    /**
     * 例外の種別に応じたエラー文言を返す。
     * 通信エラーはライブラリ独自の例外に包まれるため、causeを辿って判定する。
     */
    private fun getErrorMessage(throwable: Throwable): String {
        val causes = generateSequence(throwable) { if (it.cause === it) null else it.cause }
            .take(MAX_CAUSE_DEPTH)
        val isNetworkError = causes.any {
            it is IOException || it is OpenAIIOException || it is UnavailableException
        }
        return if (isNetworkError) {
            getString(R.string.message_error)
        } else {
            getString(R.string.message_error_ai_response)
        }
    }

    /**
     * 送信されたメッセージ数を数え、設定回数に達したらインタースティシャルを表示する。
     * AIモデルによって数え方が変わらないよう、加算・判定・リセットをここでまとめて行う。
     */
    private fun showInterstitialIfNeeded() {
        // 未取得・未設定・不正値の場合はnullになり、インタースティシャルを表示しない
        val times = RemoteConfigProvider.adDisplayRequestTimes
        val storedCount = getMessageCountForAd(applicationContext)
        if (times == null) {
            setMessageCountForAd(applicationContext, storedCount + 1)
            return
        }

        // 設定回数より多く数える意味はないので、上限は設定回数に留める
        val count = (storedCount + 1).coerceAtMost(times)
        if (count < times) {
            setMessageCountForAd(applicationContext, count)
            return
        }

        Log.d(TAG, "Ad display message count: $count")
        // ロードが終わっていない場合はカウントを持ち越して次の送信で表示し直す
        val isShown = adController.showInterstitial(this)
        setMessageCountForAd(applicationContext, if (isShown) 0 else count)
    }

    private fun showSupportDialogIfNeeded() {
        val supportTimes = RemoteConfigProvider.supportDisplayRequestTimes
        if (supportTimes == null || isSupporter(applicationContext)) return

        val supportCount = getSupportRequestCount(applicationContext) + 1
        setSupportRequestCount(applicationContext, supportCount)
        if (supportCount >= supportTimes) {
            Log.d(TAG, "Support display request count: $supportCount")
            setSupportRequestCount(applicationContext, 0)
            showSupportDialog(requireUrls = true)
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
        receiveMessage(response)
    }

    /**
     * ミクの応答を表示して履歴に保存する。
     * 応答が空の場合は黙って捨てず、状態が伝わるようにエラーとして扱う。
     * 改行や空白だけの応答も吹き出しが空に見えるだけなので空として扱う。
     */
    private suspend fun receiveMessage(text: String?) {
        if (text.isNullOrBlank()) {
            Log.e(TAG, "Response is empty.")
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                showErrorMessage(getString(R.string.message_error_empty_response))
            }
            return
        }

        val receivedMessage = Message.Builder()
            .setUser(mikuAccount)
            .setRight(false)
            .setText(text)
            .build()
        withContext(Dispatchers.Main) {
            if (isDestroyed || isFinishing) {
                return@withContext
            }
            // 応答の吹き出しの下に応答待ちの吹き出しが残らないよう先に消す
            hideTypingIndicator()
            binding.chatView.receive(receivedMessage)
        }
        // 表示できなくても会話は成立しているので履歴には残す
        appDatabase.messageDao().insert(messageToEntity(receivedMessage))
    }

    private suspend fun openAITask(text: String) {
        val client = openAI
        if (client == null) {
            // 送信前にも確認しているが、初期化が外れた場合にも黙って終わらないようにする
            Log.e(TAG, "OpenAI is not initialized.")
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) {
                    return@withContext
                }
                showErrorMessage(getString(R.string.message_preparing))
            }
            return
        }

        val maxLength = RemoteConfigProvider.maxUserTextLength
        val sendText = if (text.length <= maxLength) text else text.substring(0, maxLength)
        Log.d(TAG, "sendText: $sendText")

        val maxTokens = RemoteConfigProvider.maxTokens

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
        val completion = client.chatCompletion(chatCompletionRequest)
        Log.d(TAG, "completion: $completion")
        val choice = completion.choices.first()
        val response = choice.message.content
        Log.d(TAG, "response: $response")
        val result = when {
            // 応答が空のまま末尾に記号を足すと空でなくなってしまうので先に判定する
            response.isNullOrBlank() -> null
            choice.finishReason == FinishReason.Length -> "$response…"
            else -> response
        }
        Log.d(TAG, "result: $result")
        if (result != null) {
            openAIPreviousResponse = result
        }
        receiveMessage(result)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AVATAR_MODE, isAvatarMode)
        if (!isAvatarMode || !::webView.isInitialized) {
            return
        }
        // 復帰させられるのは開いていたページまで。ページ内のJS状態（アバターの姿勢や会話の途中）は戻らない。
        // WebView.saveState() は使わない。モード切り替えで積まれた about:blank まで含む
        // 前後の履歴が丸ごと戻り、戻るキーでチャットモードに帰れなくなるため。
        outState.putString(STATE_AVATAR_URL, webView.url)
    }

    public override fun onPause() {
        isActivityResumed = false
        if (::webView.isInitialized) {
            // バックグラウンドで音声再生や3D描画が続かないように止める。
            // WebView.pauseTimers() はプロセス内の全WebViewのタイマーを止めてしまうので使わない。
            // インタースティシャル広告のWebViewまで凍結され、閉じるボタンが出なくなる。
            webView.onPause()
        }
        adController.onPause(this)
        super.onPause()
    }

    public override fun onResume() {
        isActivityResumed = true
        adController.onResume(this)
        if (::webView.isInitialized) {
            webView.onResume()
        }
        super.onResume()
    }

    public override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        removeOpenAIListener()
        removeAvatarCredentialsListener()
        scope.coroutineContext.cancel()
        if (::detectIntent.isInitialized) {
            // コンテキストの破棄はgRPCの通信になるためメインスレッドでは行えない。
            // Activityの終了後にアプリスコープで実行される
            detectIntent.shutdown()
        }
        adController.onDestroy(this)
        super.onDestroy()
    }

    /** "text/html; charset=utf-8" のようなContent-TypeからMIMEタイプだけを取り出す */
    private fun parseMimeType(contentType: String?): String? {
        return contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Content-Typeから文字コードを取り出す。指定がない場合はnull */
    private fun parseCharset(contentType: String?): String? {
        return contentType?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', ' ')
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        val TAG = MainActivity::class.java.name.toString()

        // 履歴に記録される送信者のID
        private const val USER_ID_ME = 0
        private const val MIKU_USER_ID_DIALOG_FLOW = 1
        private const val MIKU_USER_ID_OPEN_AI = 2

        // 一度に読み込む履歴の件数
        private const val HISTORY_PAGE_SIZE = 100

        private const val STATE_AVATAR_MODE = "state_avatar_mode"
        private const val STATE_AVATAR_URL = "state_avatar_url"

        // 例外の原因を辿る深さの上限。cause が循環していても止まるようにする
        private const val MAX_CAUSE_DEPTH = 10

        // アバターページのアセット取得に使うタイムアウト
        private const val WEB_REQUEST_CONNECT_TIMEOUT_MS = 15_000
        private const val WEB_REQUEST_READ_TIMEOUT_MS = 30_000

        private const val METHOD_GET = "GET"

        // WebResourceResponseは空のreason phraseを受け付けないため、応答に無い場合に使う
        private const val DEFAULT_REASON_PHRASE = "Error"
        private const val DEFAULT_CHARSET = "utf-8"
        private const val HEADER_CF_CLIENT_ID = "CF-Access-Client-Id"
        private const val HEADER_CF_CLIENT_SECRET = "CF-Access-Client-Secret"

        /**
         * 引き継がないリクエストヘッダ。
         *
         * Accept-EncodingはHttpURLConnectionが自分で付けたときだけ透過的に解凍されるため、
         * こちらから指定すると圧縮されたままWebViewに渡ってしまう。
         * 条件付きリクエストのヘッダは304・206をWebResourceResponseで表現できず、
         * 中身のない応答になってしまうため送らない。
         */
        private val SKIPPED_REQUEST_HEADERS = setOf(
            "Accept-Encoding",
            "If-Modified-Since",
            "If-None-Match",
            "Range",
        )
    }
}
