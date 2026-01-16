package com.kit.sms2mail

import android.content.Context
import android.util.Log
import androidx.work.*
import com.kit.sms2mail.data.GmailService
import kotlinx.io.IOException
import java.util.concurrent.TimeUnit

class SendEmailWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Get data from input
            val originatingAddress = inputData.getString(KEY_SMS_ADDRESS) ?: return Result.failure()
            val messageBody = inputData.getString(KEY_SMS_BODY) ?: return Result.failure()

            // Send email
            val result = gmailService.sendMail(
                originatingAddress = originatingAddress,
                messageBody = messageBody
            )

            if (result != null) {
                Log.d(TAG, "Email sent successfully: $result")
                Result.success()
            } else {
                Log.e(TAG, "Failed to send email")
                Result.retry() // Retry if failed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in worker", e)
            if (e is IOException) {
                Result.retry() // Retry on network errors
            } else {
                Result.failure() // Fail permanently on other errors
            }
        }
    }

    companion object {
        const val TAG = "SendEmailWorker"
        const val KEY_SMS_ADDRESS = "address"
        const val KEY_SMS_BODY = "body"
        const val WORK_TAG = "send_email_work"

        private val gmailService by lazy { GmailService() }

        fun enqueueOneTimeWork(
            context: Context,
            originatingAddress: String?,
            messageBody: String
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val inputData = workDataOf(
                KEY_SMS_ADDRESS to originatingAddress,
                KEY_SMS_BODY to messageBody
            )
            val sendEmailWork = OneTimeWorkRequestBuilder<SendEmailWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueue(sendEmailWork)

            Log.d(TAG, "Email work enqueued for SMS from $originatingAddress")
        }
    }
}