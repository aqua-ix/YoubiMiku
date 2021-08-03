package comviewaquahp.google.sites.youbimiku

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.bassaer.chatmessageview.model.Message
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.lang.Exception

class MainActivity : AppCompatActivity(), View.OnClickListener, DialogListener {
    private lateinit var userAccount: User
    private lateinit var mikuAccount: User

    private lateinit var detectIntent: DetectIntent
    private lateinit var adView: AdManagerAdView
    private var initialLayoutComplete = false
    private val scope = CoroutineScope(Dispatchers.Default)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        detectIntent = DetectIntent(this)

        initChatView(adSize.getHeightInPixels(this))
        initBanner()
        
        showUserNameDialogIfNeeded()
        showInAppReviewIfNeeded()
    }

    private val adSize: AdSize
        get() {
            val display = windowManager.defaultDisplay
            val outMetrics = DisplayMetrics()
            display.getMetrics(outMetrics)

            val density = outMetrics.density

            var adWidthPixels = ad_view_container.width.toFloat()
            if (adWidthPixels == 0f) {
                adWidthPixels = outMetrics.widthPixels.toFloat()
            }

            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
        }


    private fun initBanner() {
        MobileAds.initialize(this) {}
        MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf("emulator-5554"))
                        .build()
        )

        ad_placeholder.layoutParams.height = adSize.getHeightInPixels(this)
        adView = AdManagerAdView(this)
        ad_view_container.addView(adView)
        ad_view_container.viewTreeObserver.addOnGlobalLayoutListener {
            if (!initialLayoutComplete) {
                initialLayoutComplete = true
                loadBanner(adSize)
            }
        }
    }

    private fun loadBanner(adSize: AdSize) {
        adView.adUnitId = BuildConfig.AD_UNIT_ID
        adView.setAdSizes(adSize)
        val adRequest = AdManagerAdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    private fun initChatView(height: Int) {
        val size = FontSizeConfig.getSize(getFontSizeType(this))
        setFontSize(size, chat_view)

        val mikuFace = BitmapFactory.decodeResource(resources, R.drawable.normal)
        userAccount = User(0, null, null)
        mikuAccount = User(1, getString(R.string.miku_name), mikuFace)
//        val mlp = chat_view.layoutParams as ViewGroup.MarginLayoutParams
//        mlp.topMargin = height
        chat_view.setDateSeparatorFontSize(0F)
        chat_view.setInputTextHint(getString(R.string.input_text_hint))
        chat_view.setOnClickSendButtonListener(this)
    }

    private fun showGreet(userName: String?) {
        val greeting = resources.getString(R.string.miku_nice_to_meet_you, userName)
        val welcome = Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(greeting)
                .build()
        chat_view.receive(welcome)
    }

    private fun showInAppReviewIfNeeded() {
        val pref = SharedPreferenceManager
        pref.get(this, Key.LAUNCH_COUNT.name, 0)?.let {
            val current = it + 1
            pref.put(this, Key.LAUNCH_COUNT.name, current)

            // 起動回数が5回のときにInAppReviewを表示しカウントをリセット
            if (current >= 5) {
                openInAppReview()
                pref.put(this, Key.LAUNCH_COUNT.name, 0)
            }
        }
    }

    private fun showUserNameDialogIfNeeded() {
        if (getUserName(this).equals("")) {
            showUserNameDialog(false)
        } else {
            userAccount.setName(getUserName(this).toString())
            showGreet(getUserName(this))
        }
    }

    private fun showUserNameDialog(cancelable: Boolean = true) {
        val dialog = UserNameDialogFragment()
        val args = Bundle()
        args.putBoolean(Constants.ARGUMENT_CANCELABLE, cancelable)
        dialog.arguments = args
        dialog.setDialogListener(this)
        dialog.show(fragmentManager, UserNameDialogFragment::class.java.name)
    }

    override fun doPositiveClick() {
        userAccount.setName(getUserName(this).toString())
        showGreet(getUserName(this))
    }

    private fun showFontSizeDialog() {
        val index = FontSizeConfig.getType(getFontSizeType(this)).ordinal
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.setting_font_size))
                .setSingleChoiceItems(R.array.font_size_config, index) { _, which ->
                    setFontSize(FontSizeConfig.getSize(which), chat_view)
                    SharedPreferenceManager.put(
                            this,
                            Key.FONT_SIZE.name,
                            FontSizeConfig.getType(which).name
                    )
                }
                .setPositiveButton(getString(R.string.setting_dialog_accept), null)
                .show()
    }

    private fun openPlayStore() {
        try {
            val uri = Uri.parse("market://details?id=$packageName")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this,
                    getString(R.string.setting_submit_review_error),
                    Toast.LENGTH_SHORT)
                    .show()
        }
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

    private fun openMailer() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(Constants.FEEDBACK_ADDRESS))
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject))
                val text = buildString {
                    append("Model Name: " + Build.MODEL)
                    append("\nOS Version: " + Build.VERSION.SDK_INT)
                    append("\n=================\n")
                }
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this,
                    getString(R.string.setting_send_feedback_error),
                    Toast.LENGTH_SHORT)
                    .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.setting_user_name -> {
                showUserNameDialog()
                true
            }
            R.id.setting_font_size -> {
                showFontSizeDialog()
                true
            }
            R.id.setting_submit_review -> {
                openPlayStore()
                true
            }
            R.id.setting_send_feedback -> {
                openMailer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(v: View) {
        if (chat_view.inputText.isEmpty()) {
            return
        }
        val send = Message.Builder()
                .setUser(userAccount)
                .setRight(true)
                .setText(chat_view.inputText)
                .hideIcon(true)
                .build()
        sendRequest(chat_view.inputText)
        chat_view.send(send)
        chat_view.inputText = ""
    }

    private fun sendRequest(text: String) {
        Log.d(TAG, text)
        if (TextUtils.isEmpty(text)) {
            Log.e(TAG, Constants.LOGGER_EMPTY_QUERY)
            return
        }
        scope.launch {
            dialogFlowTask(text)
        }
    }

    private suspend fun dialogFlowTask(text: String) {
        try {
            val response = detectIntent.send(text)
            val receivedMessage = Message.Builder()
                    .setUser(mikuAccount)
                    .setRight(false)
                    .setText(response)
                    .build()
            withContext(Dispatchers.Main) {
                chat_view.receive(receivedMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            scope.coroutineContext.cancel()
        }
    }

    public override fun onPause() {
        adView.pause()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        adView.resume()
    }

    public override fun onDestroy() {
        adView.destroy()
        detectIntent.resetContexts()
        scope.coroutineContext.cancel()
        super.onDestroy()
    }

    companion object {
        val TAG = MainActivity::class.java.name.toString()
    }
}
