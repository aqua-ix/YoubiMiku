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
import kotlinx.android.synthetic.main.user_name_dialog.view.*

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var mUserAccount: User
    private lateinit var mMikuAccount: User
    private lateinit var mAiDataService: AIDataService
    private val gson = GsonFactory.getGson()

    data class AIConfig(val languageCode: String, val accessToken: String)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val userName = SharedPreferenceManager.get(
                this,
                Key.USER_NAME.name,
                ""
        )
        initChatView()
        if (userName.equals("")) {
            showUserNameDialog()
        } else {
            mUserAccount.setName(userName.toString())
            showGreet(userName)
        }
        val config = AIConfig(
                Constants.DEFAULT_LANGUAGE_CODE,
                Constants.DIALOG_FLOW_ACCESS_TOKEN)
        initAIService(config)
    }

    @SuppressLint("InflateParams")
    private fun showUserNameDialog() {
        val view = layoutInflater.inflate(R.layout.user_name_dialog, null)
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.setting_name_title))
                .setMessage(getString(R.string.setting_name_message))
                .setView(view)
                .setPositiveButton("OK") { _, _ ->
                    var name = view.editText.text.toString()
                    if (name.isNotEmpty()) {
                        mUserAccount.setName(name)
                    } else {
                        name = "User"
                    }
                    showGreet(name)
                    SharedPreferenceManager.put(
                            this,
                            Key.USER_NAME.name,
                            name
                    )
                }.create().show()
    }

    private fun showFontSizeDialog() {
        val current = SharedPreferenceManager.get(
                this,
                Key.FONT_SIZE.name,
                FontSizeConfig.FONT_SIZE_MEDIUM.name
        )
        val index = FontSizeConfig.getType(current).ordinal
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.setting_font_size))
                .setSingleChoiceItems(R.array.font_size_config, index) { _, which ->
                    setFontSize(FontSizeConfig.getSize(which))
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

    private fun setFontSize(size: Float) {
        chat_view.setMessageFontSize(size)
        chat_view.setUsernameFontSize(size - 10)
        chat_view.setTimeLabelFontSize(size - 10)
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
        val current = SharedPreferenceManager.get(
                this,
                Key.FONT_SIZE.name,
                FontSizeConfig.FONT_SIZE_MEDIUM.name
        )
        setFontSize(FontSizeConfig.getSize(current))
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
