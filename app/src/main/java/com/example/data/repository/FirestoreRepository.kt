package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AppLanguage
import com.example.data.model.AutomationRun
import com.example.data.model.ChatMessageRecord
import com.example.data.model.CitationItem
import com.example.data.model.PackageFormat
import com.example.data.model.StudioProject
import com.example.data.model.UserPreferenceProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud storage.")
    }

    fun observePreferences(): Flow<UserPreferenceProfile> = flow {
        val uid = requireUserId()
        val path = "users/$uid/preferences/profile"
        emitAll(
            db.collection("users").document(uid)
                .collection("preferences").document("profile")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserPreferenceProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPreferenceProfile(userId = uid)
                    } else {
                        UserPreferenceProfile(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun savePreferences(profile: UserPreferenceProfile): Result<Unit> {
        var docPath = "users/unauthenticated/preferences/profile"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("preferences").document("profile")
            docPath = docRef.path

            val sanitizedLang = AppLanguage.fromRaw(profile.defaultLanguage).name
            val sanitizedFmt = PackageFormat.fromRaw(profile.defaultPackageFormat).name
            val sanitizedNotes = profile.memoryNotes.map { it.trim().take(280) }
                .filter { it.isNotEmpty() }.take(40)
            val sanitizedProxy = profile.backendProxyUrl.trim().take(500)
            val sanitizedBeat = profile.automationBeat.trim().take(80)
                .ifEmpty { "History, Science & Current Affairs" }
            val hour = profile.automationHour.coerceIn(0, 23)
            val minute = profile.automationMinute.coerceIn(0, 59)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "defaultLanguage" to sanitizedLang,
                        "defaultPackageFormat" to sanitizedFmt,
                        "memoryNotes" to sanitizedNotes,
                        "backendProxyUrl" to sanitizedProxy,
                        "automationEnabled" to profile.automationEnabled,
                        "automationHour" to hour,
                        "automationMinute" to minute,
                        "automationBeat" to sanitizedBeat,
                        "economyModeEnabled" to profile.economyModeEnabled,
                        "chatWebSearchDefault" to profile.chatWebSearchDefault,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "defaultLanguage" to sanitizedLang,
                        "defaultPackageFormat" to sanitizedFmt,
                        "memoryNotes" to sanitizedNotes,
                        "backendProxyUrl" to sanitizedProxy,
                        "automationEnabled" to profile.automationEnabled,
                        "automationHour" to hour,
                        "automationMinute" to minute,
                        "automationBeat" to sanitizedBeat,
                        "economyModeEnabled" to profile.economyModeEnabled,
                        "chatWebSearchDefault" to profile.chatWebSearchDefault,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeChatMessages(): Flow<List<ChatMessageRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/chatMessages"
        emitAll(
            db.collection("users").document(uid)
                .collection("chatMessages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessageRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addChatMessage(
        role: String,
        content: String,
        language: AppLanguage,
        citations: List<CitationItem> = emptyList(),
        certaintyTag: String = if (role == "user") "USER" else "MIXED",
        threadId: String = "default_thread"
    ): Result<String> {
        var docPath = "users/unauthenticated/chatMessages"
        return try {
            val uid = requireUserId()
            val trimmed = content.trim().take(20000)
            require(trimmed.isNotEmpty()) { "Message content cannot be empty." }
            val docRef = db.collection("users").document(uid)
                .collection("chatMessages").document()
            docPath = docRef.path

            val safeRole = if (role == "assistant") "assistant" else "user"
            val safeTag = when (certaintyTag) {
                "VERIFIED", "MIXED", "OPINION", "UNCERTAIN", "USER" -> certaintyTag
                else -> "MIXED"
            }

            docRef.set(
                mapOf(
                    "userId" to uid,
                    "threadId" to threadId.take(128).ifBlank { "default_thread" },
                    "role" to safeRole,
                    "content" to trimmed,
                    "language" to language.name,
                    "citations" to citations.take(20).map { it.toMap() },
                    "certaintyTag" to safeTag,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(messageIds: List<String>): Result<Unit> {
        var docPath = "users/unauthenticated/chatMessages"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/chatMessages"
            val col = db.collection("users").document(uid).collection("chatMessages")
            for (id in messageIds) {
                if (id.isNotBlank()) {
                    col.document(id).delete().await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeStudioProjects(): Flow<List<StudioProject>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/studioProjects"
        emitAll(
            db.collection("users").document(uid)
                .collection("studioProjects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createStudioProject(project: StudioProject): Result<String> {
        var docPath = "users/unauthenticated/studioProjects"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("studioProjects").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "topicTitle" to project.topicTitle.trim().take(300).ifBlank { "Untitled Documentary" },
                "categoryBeat" to project.categoryBeat.trim().take(80).ifBlank { "Current Affairs" },
                "language" to AppLanguage.fromRaw(project.language).name,
                "packageFormat" to PackageFormat.fromRaw(project.packageFormat).name,
                "angle" to project.angle.take(1500),
                "hook" to project.hook.take(1500),
                "whyCoverNow" to project.whyCoverNow.take(2000),
                "stage" to project.stage,
                "topicApproved" to project.topicApproved,
                "finalApproved" to project.finalApproved,
                "verifiedFacts" to project.verifiedFacts.take(40),
                "disputedClaims" to project.disputedClaims.take(25),
                "assumptions" to project.assumptions.take(25),
                "shortScript" to project.shortScript.take(20000),
                "longScript" to project.longScript.take(60000),
                "visualDirections" to project.visualDirections.take(25000),
                "onScreenText" to project.onScreenText.take(50),
                "titleOptions" to project.titleOptions.take(15),
                "thumbnailConcepts" to project.thumbnailConcepts.take(15),
                "descriptionAndChapters" to project.descriptionAndChapters.take(15000),
                "projectOverview" to project.projectOverview,
                "storyStructure" to project.storyStructure,
                "blueprintScenes" to project.blueprintScenes.take(40),
                "footageAssets" to project.footageAssets.take(40),
                "voiceoverVoice" to project.voiceoverVoice.take(80),
                "voiceoverStatus" to project.voiceoverStatus,
                "voiceoverLocalPath" to project.voiceoverLocalPath.take(500),
                "voiceoverDirectionNotes" to project.voiceoverDirectionNotes.take(15000),
                "subtitlesSrt" to project.subtitlesSrt.take(40000),
                "capcutPlan" to project.capcutPlan.take(35000),
                "capcutChecklist" to project.capcutChecklist.take(30),
                "auditChecklist" to project.auditChecklist.take(25),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateStudioProject(projectId: String, fields: Map<String, Any>): Result<Unit> {
        var docPath = "users/unauthenticated/studioProjects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Project ID must not be blank." }
            val docRef = db.collection("users").document(uid)
                .collection("studioProjects").document(projectId)
            docPath = docRef.path
            val sanitized = fields.toMutableMap().apply {
                remove("id")
                remove("userId")
                remove("createdAt")
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(sanitized).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudioProject(projectId: String): Result<Unit> {
        var docPath = "users/unauthenticated/studioProjects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank()) { "Project ID must not be blank." }
            val docRef = db.collection("users").document(uid)
                .collection("studioProjects").document(projectId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeAutomationRuns(): Flow<List<AutomationRun>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/automationRuns"
        emitAll(
            db.collection("users").document(uid)
                .collection("automationRuns")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AutomationRun::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logAutomationRun(
        triggerType: String,
        status: String,
        attemptCount: Int,
        summary: String,
        projectId: String = "",
        errorMessage: String = ""
    ): Result<String> {
        var docPath = "users/unauthenticated/automationRuns"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("automationRuns").document()
            docPath = docRef.path

            docRef.set(
                mapOf(
                    "userId" to uid,
                    "triggerType" to if (triggerType == "SCHEDULED") "SCHEDULED" else "MANUAL",
                    "status" to when (status) {
                        "COMPLETED", "RETRYING", "FAILED" -> status
                        else -> "FAILED"
                    },
                    "attemptCount" to attemptCount.coerceIn(0, 10),
                    "summary" to summary.take(2000),
                    "projectId" to projectId.take(128),
                    "errorMessage" to errorMessage.take(1000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
