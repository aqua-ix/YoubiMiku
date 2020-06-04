package comviewaquahp.google.sites.youbimiku;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.android.GsonFactory;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIEvent;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;
import ai.api.model.Status;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = MainActivity.class.getName();
    private ChatView mChatView;
    private User masterAccount;
    private User mikuAccount;
    private AIDataService aiDataService;
    private Gson gson = GsonFactory.getGson();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String userName = sharedPref.getString(getString(R.string.saved_user_name_key), "");

        initChatView();

        if (userName.isEmpty()) {
            showUserNameDialog(sharedPref);
        } else {
            masterAccount.setName(userName);
            showGreet(userName);
        }

        final LanguageConfig config =
                new LanguageConfig("ja", Constants.DIALOG_FLOW_ACCESS_TOKEN);
        initAIService(config);
    }

    @SuppressLint("ApplySharedPref")
    public void showUserNameDialog(SharedPreferences sharedPref) {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.tutorial_welcome))
                .setMessage(getString(R.string.tutorial_whats_your_name))
                .setView(editText)
                .setPositiveButton("OK", (dialog, which) -> {
                    SharedPreferences.Editor editor = sharedPref.edit();
                    String name = editText.getText().toString();
                    if (!name.isEmpty()) {
                        masterAccount.setName(name);
                    } else {
                        name = "";
                    }
                    showGreet(name);
                    editor.putString(getString(R.string.saved_user_name_key), name);
                    editor.commit();
                }).show();
    }

    @Override
    public void onClick(View v) {
        Message send = new Message.Builder()
                .setUser(masterAccount)
                .setRight(true)
                .setText(mChatView.getInputText())
                .hideIcon(true)
                .build();

        sendRequest(mChatView.getInputText());
        mChatView.send(send);
        mChatView.setInputText("");
    }

    private void onResult(final AIResponse response) {
        runOnUiThread(() -> {
            // Variables
            gson.toJson(response);
            final Status status = response.getStatus();
            final Result result = response.getResult();
            final String speech = result.getFulfillment().getSpeech();
            final Metadata metadata = result.getMetadata();
            final HashMap<String, JsonElement> params = result.getParameters();

            // Logging
            Log.d(TAG, "onResult");
            Log.i(TAG, "Received success response");
            Log.i(TAG, "Status code: " + status.getCode());
            Log.i(TAG, "Status type: " + status.getErrorType());
            Log.i(TAG, "Resolved query: " + result.getResolvedQuery());
            Log.i(TAG, "Action: " + result.getAction());
            Log.i(TAG, "Speech: " + speech);

            if (metadata != null) {
                Log.i(TAG, "Intent id: " + metadata.getIntentId());
                Log.i(TAG, "Intent name: " + metadata.getIntentName());
            }

            if (params != null && !params.isEmpty()) {
                Log.i(TAG, "Parameters: ");
                for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
                    Log.i(TAG, String.format("%s: %s",
                            entry.getKey(), entry.getValue().toString()));
                }
            }

            //Update view to bot says
            final Message receivedMessage = new Message.Builder()
                    .setUser(mikuAccount)
                    .setRight(false)
                    .setText(speech)
                    .build();
            mChatView.receive(receivedMessage);
        });
    }

    private void onError(final AIError error) {
        runOnUiThread(() -> Log.e(TAG, error.toString()));
    }

    /*
     * AIRequest should have query OR event
     */
    public void sendRequest(String text) {
        Log.d(TAG, text);
        if (TextUtils.isEmpty(text)) {
            onError(new AIError(getString(R.string.non_empty_query)));
            return;
        }

        new AiTask().execute(text, null, null);
    }

    private void initAIService(final LanguageConfig languageConfig) {
        final AIConfiguration.SupportedLanguages lang =
                AIConfiguration.SupportedLanguages.fromLanguageTag(languageConfig.getLanguageCode());
        final AIConfiguration config = new AIConfiguration(languageConfig.getAccessToken(),
                lang,
                AIConfiguration.RecognitionEngine.System);
        aiDataService = new AIDataService(this, config);
    }

    private void initChatView() {
        mChatView = findViewById(R.id.chat_view);
        mChatView.setMessageFontSize(Float.parseFloat(Constants.DEFAULT_MESSAGE_FONT_SIZE));
        mChatView.setUsernameFontSize(Float.parseFloat(Constants.DEFAULT_USERNAME_FONT_SIZE));
        mChatView.setTimeLabelFontSize(Float.parseFloat(Constants.DEFAULT_TIME_FONT_SIZE));

        Bitmap mikuFace = BitmapFactory.decodeResource(getResources(), R.drawable.normal);
        masterAccount = new User(0, null, null);
        mikuAccount = new User(1, getString(R.string.miku_name), mikuFace);

        mChatView.setOnClickSendButtonListener(this);
    }

    private void showGreet(String userName) {
        String greeting = getResources().getString(R.string.tutorial_nice_to_meet_you, userName);
        Message welcome = new Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(greeting)
                .build();

        mChatView.receive(welcome);
    }

    public class AiTask extends AsyncTask<String, Void, AIResponse> {
        private AIError aiError;

        @Override
        protected AIResponse doInBackground(final String... params) {
            final AIRequest request = new AIRequest();
            String query = params[0];
            String event = params[1];
            String context = params[2];

            if (!TextUtils.isEmpty(query)) {
                request.setQuery(query);
            }

            if (!TextUtils.isEmpty(event)) {
                request.setEvent(new AIEvent(event));
            }

            RequestExtras requestExtras = null;
            if (!TextUtils.isEmpty(context)) {
                final List<AIContext> contexts = Collections.singletonList(new AIContext(context));
                requestExtras = new RequestExtras(contexts, null);
            }

            try {
                return aiDataService.request(request, requestExtras);
            } catch (final AIServiceException e) {
                aiError = new AIError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(final AIResponse response) {
            if (response != null) {
                onResult(response);
            } else {
                onError(aiError);
            }
        }
    }
}