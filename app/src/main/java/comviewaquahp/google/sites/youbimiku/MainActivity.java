package comviewaquahp.google.sites.youbimiku;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;
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
    private boolean startup;
    private AIDataService aiDataService;
    private Gson gson = GsonFactory.getGson();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initChatView();

        final LanguageConfig config =
                new LanguageConfig("ja", Constants.DIALOGFLOW_ACCESS_TOKEN);

        initAIService(config);

    }

    @Override
    public void onClick(View v) {
        if (startup) {
            setName();
            return;
        }
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
        final String queryString = String.valueOf(text);
        final String eventString = null;
        final String contextString = null;

        if (TextUtils.isEmpty(queryString) && TextUtils.isEmpty(eventString)) {
            onError(new AIError(getString(R.string.non_empty_query)));
            return;
        }

        new AiTask().execute(queryString, eventString, contextString);
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
        startup = true;
        mChatView = findViewById(R.id.chat_view);
        mChatView.setMessageFontSize(Float.valueOf(Constants.DEFAULT_MESSAGE_FONT_SIZE));
        mChatView.setUsernameFontSize(Float.valueOf(Constants.DEFAULT_USERNAME_FONT_SIZE));
        mChatView.setTimeLabelFontSize(Float.valueOf(Constants.DEFAULT_TIME_FONT_SIZE));

        Bitmap mikuFace = BitmapFactory.decodeResource(getResources(), R.drawable.normal);
        masterAccount = new User(0, null, null);
        mikuAccount = new User(1, getString(R.string.miku_name), mikuFace);

        Message welcome = new Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(getString(R.string.tutorial_welcome))
                .build();

        mChatView.receive(welcome);

        Message yourname = new Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(getString(R.string.tutorial_whats_your_name))
                .build();

        mChatView.receive(yourname);

        mChatView.setOnClickSendButtonListener(this);
    }

    private void setName() {

        Message send = new Message.Builder()
                .setUser(masterAccount)
                .setRight(true)
                .setText(mChatView.getInputText())
                .hideIcon(true)
                .build();


        mChatView.send(send);
        if ("".equals(mChatView.getInputText())) {
            Message receive = new Message.Builder()
                    .setUser(mikuAccount)
                    .setRight(false)
                    .setText(getString(R.string.tutorial_name_is_empty))
                    .build();

            mChatView.receive(receive);
            return;
        } else {
            mChatView.getInputText();
        }
        masterAccount.setName(mChatView.getInputText());
        mChatView.setInputText("");

        String receiveText = masterAccount.getName() +
                getString(R.string.tutorial_nice_to_meet_you);
        Message receive = new Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(receiveText)
                .build();

        mChatView.receive(receive);

        Message attention = new Message.Builder()
                .setUser(mikuAccount)
                .setRight(false)
                .setText(getString(R.string.tutorial_how_to_reset_name))
                .build();

        mChatView.receive(attention);

        startup = false;
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