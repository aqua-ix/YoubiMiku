package com.aqua_ix.youbimiku

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ReportUtil {

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
        scope.launch {
            try {
                val url = URL(BuildConfig.REPORT_END_POINT)
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.setRequestProperty("Content-Type", "application/json")
                urlConnection.doOutput = true

                val data =
                    """{"timestamp": "${System.currentTimeMillis()}", "userName": "$userName", "text": "$text", "reason": "$reason"}"""
                urlConnection.outputStream.use { it.write(data.toByteArray()) }

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.message_reported),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.message_reported_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.message_reported_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}