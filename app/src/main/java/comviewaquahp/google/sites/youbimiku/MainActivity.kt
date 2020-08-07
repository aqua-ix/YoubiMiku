package comviewaquahp.google.sites.youbimiku

import ai.api.AIConfiguration.SupportedLanguages
import ai.api.AIConfiguration.SupportedLanguages.fromLanguageTag
import ai.api.AIServiceException
import ai.api.RequestExtras
import ai.api.android.AIConfiguration
import ai.api.android.AIDataService
import ai.api.android.GsonFactory
import ai.api.model.*
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.bassaer.chatmessageview.model.Message
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), View.OnClickListener, DialogListener {
    private lateinit var mUserAccount: User
    private lateinit var mMikuAccount: User
    private lateinit var mAiDataService: AIDataService
    private val gson = GsonFactory.getGson()

    data class AIConfig(val languageCode: String, val accessToken: String)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initChatView()
        if (getUserName(this).equals("")) {
            showUserNameDialog()
        } else {
            mUserAccount.setName(getUserName(this).toString())
            showGreet(getUserName(this))
        }
        val config = AIConfig(
                Constants.DEFAULT_LANGUAGE_CODE,
                Constants.DIALOG_FLOW_ACCESS_TOKEN)
        initAIService(config)
    }

    private fun showUserNameDialog() {
        val dialog = UserNameDialogFragment()
        dialog.setDialogListener(this)
        dialog.show(fragmentManager, "userNameDialog")
    }

    override fun doPositiveClick(){
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
                .setPositiveButton("OK", null)
                .show()
    }

    private fun openPlayStore() {
        val uri = Uri.parse("market://details?id=$packageName")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
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
            R.id.setting_feedback -> {
                openPlayStore()
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

    private fun onResult(response: AIResponse) {
        runOnUiThread {
            // Variables
            gson.toJson(response)
            val result = response.result
            val speech = result.fulfillment.speech

            //Update view to bot says
            val receivedMessage = Message.Builder()
                    .setUser(mMikuAccount)
                    .setRight(false)
                    .setText(speech)
                    .build()
            chat_view.receive(receivedMessage)
        }
    }

    private fun onError(error: AIError?) {
        runOnUiThread { Log.e(TAG, error.toString()) }
    }

    private fun sendRequest(text: String) {
        Log.d(TAG, text)
        if (TextUtils.isEmpty(text)) {
            onError(AIError(getString(R.string.logger_empty_query)))
            return
        }
        AiTask().execute(text, null, null)
    }

    private fun initAIService(config: AIConfig) {
        val lang: SupportedLanguages = fromLanguageTag(config.languageCode)
        val c = AIConfiguration(config.accessToken,
                lang,
                AIConfiguration.RecognitionEngine.System)
        mAiDataService = AIDataService(this, c)
    }

    private fun initChatView() {
        val size = FontSizeConfig.getSize(getFontSizeType(this))
        setFontSize(size, chat_view)

        val mikuFace = BitmapFactory.decodeResource(resources, R.drawable.normal)
        mUserAccount = User(0, null, null)
        mMikuAccount = User(1, getString(R.string.miku_name), mikuFace)
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
    inner class AiTask : AsyncTask<String?, Void?, AIResponse?>() {
        private var aiError: AIError? = null

        override fun doInBackground(vararg params: String?): AIResponse? {
            val request = AIRequest()
            val query = params[0]
            val event = params[1]
            val context = params[2]
            if (!TextUtils.isEmpty(query)) {
                request.setQuery(query)
            }
            if (!TextUtils.isEmpty(event)) {
                request.setEvent(AIEvent(event))
            }
            var requestExtras: RequestExtras? = null
            if (!TextUtils.isEmpty(context)) {
                val contexts = listOf(AIContext(context))
                requestExtras = RequestExtras(contexts, null)
            }
            return try {
                mAiDataService.request(request, requestExtras)
            } catch (e: AIServiceException) {
                aiError = AIError(e)
                null
            }
        }

        override fun onPostExecute(response: AIResponse?) {
            if (response != null) {
                onResult(response)
            } else {
                onError(aiError)
            }
        }

    }

    companion object {
        val TAG = MainActivity::class.java.name
    }
}
