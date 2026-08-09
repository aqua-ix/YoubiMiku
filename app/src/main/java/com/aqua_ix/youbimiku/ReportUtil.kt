package com.aqua_ix.youbimiku

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 通報の送信。
 *
 * 通報する本文と通信先のURLはログに出さない（[TranslateUtil]と同じ理由で、
 * Ktorのタイムアウト例外がメッセージにURLを埋め込むため[AppLog]を通す）。
 */
object ReportUtil {

    private const val TAG = "ReportUtil"

    private const val FIELD_TIMESTAMP = "timestamp"
    private const val FIELD_USER_NAME = "userName"
    private const val FIELD_TEXT = "text"
    private const val FIELD_REASON = "reason"

    fun showReportReasonDialog(
        context: Context,
        text: String,
        userName: String,
        scope: CoroutineScope
    ) {
        val editText = EditText(context).apply {
            hint = context.getString(R.string.report_message_reason)
            setPadding(64, 32, 64, 32)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.report_message))
            .setView(editText)
            .setPositiveButton(context.getString(R.string.report_message_send)) { _, _ -> }
            .setNegativeButton(context.getString(R.string.report_message_cancel), null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val reason = editText.text.toString()
                    if (reason.isNotBlank()) {
                        reportMessage(context, userName, text, reason, scope)
                        dismiss()
                    } else {
                        editText.error = context.getString(R.string.report_message_reason_required)
                    }
                }
            }
    }

    private fun reportMessage(
        context: Context,
        userName: String,
        text: String,
        reason: String,
        scope: CoroutineScope
    ) {
        // 文字列連結では本文の " や \ 、改行でJSONが壊れて送信できず、
        // 中身次第では他の項目を差し替えられてしまうため、JSONObjectで組み立てる
        val payload = JSONObject()
            .put(FIELD_TIMESTAMP, System.currentTimeMillis().toString())
            .put(FIELD_USER_NAME, userName)
            .put(FIELD_TEXT, text)
            .put(FIELD_REASON, reason)
            .toString()

        scope.launch {
            val isReported = send(payload)
            withContext(Dispatchers.Main) {
                val message =
                    if (isReported) R.string.message_reported else R.string.message_reported_error
                Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun send(payload: String): Boolean = try {
        // doOutputによる暗黙のPOSTに頼らず、メソッドを明示する
        val response = HttpClientProvider.client.post(BuildConfig.REPORT_END_POINT) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val isSuccess = response.status.isSuccess()
        if (!isSuccess) {
            AppLog.e(TAG, "Unexpected status code: ${response.status.value}")
        }
        isSuccess
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.e(TAG, "Failed to report the message.", e)
        false
    }
}
