package comviewaquahp.google.sites.youbimiku

import android.content.Context
import android.util.Log
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.dialogflow.v2.*
import java.lang.Exception

class DetectIntent(
        context: Context
) {

    companion object {
        private const val TAG = "DetectIntent"
        const val PROJECT_ID = "youbimiku-oopulf"
        const val LANGUAGE_CODE = "ja"
        val SCOPE = listOf("https://www.googleapis.com/auth/cloud-platform")

        private fun getSession(): String {
            return "youbimiku"
        }
    }

    private val sessionsClient: SessionsClient
    private val contextClient: ContextsClient

    init {
        val credentials = GoogleCredentials
                .fromStream(context.resources.openRawResource(R.raw.credentials))
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
                .setSession(SessionName.format(PROJECT_ID, getSession()))
                .build()

        val res = sessionsClient.detectIntent(request)
        Log.d(TAG, "response result : ${res.queryResult}")
        return res.queryResult.fulfillmentText
    }

    fun send(text: String, contexts: List<String>): String {
        val queryParametersBuilder = QueryParameters.newBuilder()
        contexts.forEach {
            queryParametersBuilder
                    .addContexts(
                            com.google.cloud.dialogflow.v2.Context.newBuilder()
                                    .setName(ContextName.format(PROJECT_ID, getSession(), it))
                                    .setLifespanCount(5) // TODO: context の Lifespan を動的にする。
                                    .build()
                    )
        }

        try{
            val request = DetectIntentRequest.newBuilder()
                    .setQueryParams(queryParametersBuilder.build())
                    .setQueryInput(
                            QueryInput.newBuilder()
                                    .setText(
                                            TextInput.newBuilder()
                                                    .setText(text)
                                                    .setLanguageCode(LANGUAGE_CODE)
                                    )
                                    .build())
                    .setSession(SessionName.format(PROJECT_ID, getSession()))
                    .build()

            val res = sessionsClient.detectIntent(request)
            Log.d(TAG, "response result : ${res.queryResult}")
            return res.queryResult.fulfillmentText
        }
        catch (ex: Exception){
            Log.e(TAG, "Error : $ex")
            return ""
        }
    }

    fun resetContexts() {
        contextClient.deleteAllContexts(SessionName.format(PROJECT_ID, getSession()))
    }
}