package com.example.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.PackageFormat
import com.example.data.model.ProjectStage
import com.example.data.model.StudioProject
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Android WorkManager background worker for Shubham Files Daily Research Automation.
 *
 * IMPORTANT ANDROID SCHEDULING REALITY:
 * WorkManager enforces NetworkType.CONNECTED and exponential backoff retries,
 * but Android Doze mode, App Standby Buckets, and OEM battery optimization mean
 * exact-minute execution is NOT guaranteed on-device. Terminal failures are always
 * logged to Firestore and surfaced via a Failure Notification so runs never fail silently.
 */
class DailyResearchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DailyResearchWorker"
        const val UNIQUE_PERIODIC_WORK = "shubham_daily_research_periodic"
        const val UNIQUE_IMMEDIATE_WORK = "shubham_daily_research_manual"
        const val CHANNEL_ID = "shubham_daily_research_channel"
        const val KEY_BEAT = "beat"
        const val KEY_LANGUAGE = "language"
        const val KEY_TRIGGER_TYPE = "trigger_type"
        const val MAX_RETRY_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        val triggerType = inputData.getString(KEY_TRIGGER_TYPE) ?: "SCHEDULED"
        val beat = inputData.getString(KEY_BEAT).orEmpty().ifBlank { "History, Science & Current Affairs" }
        val lang = AppLanguage.fromRaw(inputData.getString(KEY_LANGUAGE))
        val attempt = runAttemptCount + 1

        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser == null) {
            postNotification(
                title = "Shubham Files Research Skipped",
                body = "Sign in with Google in Shubham AI so scheduled research can save to your cloud archive."
            )
            return Result.failure(Data.Builder().putString("error", "User not signed in").build())
        }

        val firestoreRepo = FirestoreRepository(applicationContext)
        val aiRepo = GeminiAiRepository()

        return try {
            val topics = aiRepo.discoverTopics(beat = beat, language = lang)
            val top = topics.firstOrNull()
                ?: throw IllegalStateException("No verifiable topics returned for beat: $beat")

            val project = StudioProject(
                userId = currentUser.uid,
                topicTitle = top.title,
                categoryBeat = top.category,
                language = lang.name,
                packageFormat = PackageFormat.DUAL.name,
                angle = top.angle,
                hook = top.hook,
                whyCoverNow = top.whyCoverNow,
                stage = ProjectStage.DISCOVERED.name,
                topicApproved = false,
                finalApproved = false
            )
            val createResult = firestoreRepo.createStudioProject(project)
            val projectId = createResult.getOrThrow()

            val summary = "Prepared ${topics.size} topics for $beat. Top draft: \"${top.title}\" (awaiting your Topic Approval)."
            firestoreRepo.logAutomationRun(
                triggerType = triggerType,
                status = "COMPLETED",
                attemptCount = attempt,
                summary = summary,
                projectId = projectId
            )
            postNotification(
                title = "Shubham Files Research Ready",
                body = "Draft ready for review: ${top.title}"
            )
            Result.success(Data.Builder().putString("projectId", projectId).build())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Daily research attempt $attempt failed", e)
            val errMsg = e.localizedMessage ?: "Network or AI generation failure"
            if (attempt < MAX_RETRY_ATTEMPTS) {
                firestoreRepo.logAutomationRun(
                    triggerType = triggerType,
                    status = "RETRYING",
                    attemptCount = attempt,
                    summary = "Attempt $attempt failed; WorkManager will retry with exponential backoff.",
                    errorMessage = errMsg
                )
                Result.retry()
            } else {
                firestoreRepo.logAutomationRun(
                    triggerType = triggerType,
                    status = "FAILED",
                    attemptCount = attempt,
                    summary = "Research automation failed after $attempt attempts.",
                    errorMessage = errMsg
                )
                postNotification(
                    title = "Shubham Files Research Failed",
                    body = "Failed after $attempt attempts: $errMsg. Open Connected Services to retry."
                )
                Result.failure(Data.Builder().putString("error", errMsg).build())
            }
        }
    }

    private fun postNotification(title: String, body: String) {
        try {
            ensureNotificationChannel(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping status bar alert.")
                return
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post daily automation notification", e)
        }
    }
}

fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            DailyResearchWorker.CHANNEL_ID,
            "Shubham Files Daily Research",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when scheduled Shubham Files research completes or fails."
        }
        manager.createNotificationChannel(channel)
    }
}

object DailyAutomationScheduler {

    fun scheduleDaily(
        context: Context,
        hour: Int,
        minute: Int,
        beat: String,
        language: AppLanguage
    ) {
        val initialDelayMinutes = computeInitialDelayMinutes(hour, minute)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(DailyResearchWorker.KEY_BEAT, beat)
            .putString(DailyResearchWorker.KEY_LANGUAGE, language.name)
            .putString(DailyResearchWorker.KEY_TRIGGER_TYPE, "SCHEDULED")
            .build()

        val request = PeriodicWorkRequestBuilder<DailyResearchWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyResearchWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun triggerRunNow(
        context: Context,
        beat: String,
        language: AppLanguage
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val input = Data.Builder()
            .putString(DailyResearchWorker.KEY_BEAT, beat)
            .putString(DailyResearchWorker.KEY_LANGUAGE, language.name)
            .putString(DailyResearchWorker.KEY_TRIGGER_TYPE, "MANUAL")
            .build()
        val request = OneTimeWorkRequestBuilder<DailyResearchWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DailyResearchWorker.UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun pauseOrStop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DailyResearchWorker.UNIQUE_PERIODIC_WORK)
    }

    fun observePeriodicWork(context: Context): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(DailyResearchWorker.UNIQUE_PERIODIC_WORK)
    }

    internal fun computeInitialDelayMinutes(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour.coerceIn(0, 23))
            set(Calendar.MINUTE, targetMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val diffMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
        return TimeUnit.MILLISECONDS.toMinutes(diffMs)
    }
}
