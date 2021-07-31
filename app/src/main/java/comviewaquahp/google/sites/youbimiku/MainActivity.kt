package comviewaquahp.google.sites.youbimiku

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.bassaer.chatmessageview.model.Message
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception

class MainActivity : AppCompatActivity(), View.OnClickListener, DialogListener {
    private lateinit var mUserAccount: User
    private lateinit var mMikuAccount: User

    private lateinit var detectIntent: DetectIntent

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 起動回数カウント
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

        detectIntent = DetectIntent(this)

        initChatView()
        if (getUserName(this).equals("")) {
            showUserNameDialog(false)
        } else {
            mUserAccount.setName(getUserName(this).toString())
            showGreet(getUserName(this))
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        detectIntent.resetContexts()
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
        mUserAccount.setName(getUserName(this).toString())
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
        }
        catch (e: Exception) {
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
        }catch (e: Exception){
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
                .setUser(mUserAccount)
                .setRight(true)
                .setText(chat_view.inputText)
                .hideIcon(true)
                .build()
        sendRequest(chat_view.inputText)
        chat_view.send(send)
        chat_view.inputText = ""
    }

    private fun onResult(response: String) {
        runOnUiThread {
            //Update view to bot says
            val receivedMessage = Message.Builder()
                    .setUser(mMikuAccount)
                    .setRight(false)
                    .setText(response)
                    .build()
            chat_view.receive(receivedMessage)
        }
    }

    private fun onError(error: String?) {
        runOnUiThread { Log.e(TAG, error.toString()) }
    }

    private fun sendRequest(text: String) {
        Log.d(TAG, text)
        if (TextUtils.isEmpty(text)) {
            onError(Constants.LOGGER_EMPTY_QUERY)
            return
        }
        AiTask().execute(text, null, null)
    }

    private fun initChatView() {
        val size = FontSizeConfig.getSize(getFontSizeType(this))
        setFontSize(size, chat_view)

        val mikuFace = BitmapFactory.decodeResource(resources, R.drawable.normal)
        mUserAccount = User(0, null, null)
        mMikuAccount = User(1, getString(R.string.miku_name), mikuFace)
        chat_view.setDateSeparatorFontSize(0F)
        chat_view.setInputTextHint(getString(R.string.input_text_hint))
        chat_view.setOnClickSendButtonListener(this)
    }

    private fun showGreet(userName: String?) {
        val greeting = resources.getString(R.string.miku_nice_to_meet_you, userName)
        val welcome = Message.Builder()
                .setUser(mMikuAccount)
                .setRight(false)
                .setText(greeting)
                .build()
        chat_view.receive(welcome)
    }

    @SuppressLint("StaticFieldLeak")
    inner class AiTask : AsyncTask<String, Void?, String?>() {
        var error = ""
        override fun doInBackground(vararg params: String): String {
            val query = params[0]
            return try {
                detectIntent.send(query)
            } catch (e: Exception) {
                error = e.toString()
                ""
            }
        }

        override fun onPostExecute(response: String?) {
            response?.let {
                onResult(it)
            } ?: onError(error)
        }
    }

    companion object {
        val TAG = MainActivity::class.java.name.toString()
    }
}
