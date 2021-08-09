package comviewaquahp.google.sites.youbimiku

import android.content.Context
import android.util.Log
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*

class DetectIntent(
        context: Context,
        private val session: String,
) {

    companion object {
        private const val TAG = "DetectIntent"
        const val PROJECT_ID = "youbimiku-oopulf"
        const val LANGUAGE_CODE = "ja"
        val SCOPE = listOf("https://www.googleapis.com/auth/cloud-platform")
    }

    private val sessionsClient: SessionsClient
    private val contextClient: ContextsClient

    init {
        val credentials = GoogleCredentials
                .fromStream(context.resources.openRawResource(R.raw.dialogflow_secret))
                .createScoped(SCOPE)
        sessionsClient = createSessions(credentials)
        contextClient = createContexts(credentials)
    }

    private fun createSessions(credentials: GoogleCredentials): SessionsClient {
        val sessionsSetting =
                SessionsSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build()
        return SessionsClient.create(sessionsSetting)
    }

    private fun createContexts(credentials: GoogleCredentials): ContextsClient {
        val contextsSettings =
                ContextsSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build()
        return ContextsClient.create(contextsSettings)
    }

    fun send(text: String): String {
        val request = DetectIntentRequest.newBuilder()
                .setQueryInput(
                        QueryInput.newBuilder()
                                .setText(TextInput
                                        .newBuilder()
                                        .setText(text)
                                        .setLanguageCode(LANGUAGE_CODE))
                                .build())
                .setSession(SessionName.format(PROJECT_ID, session))
                .build()

        val res = sessionsClient.detectIntent(request)
        Log.d(TAG, "response result : ${res.queryResult}")
        return res.queryResult.fulfillmentText
    }

    fun resetContexts() {
        contextClient.deleteAllContexts(SessionName.format(PROJECT_ID, session))
    }
}