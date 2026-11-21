package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StudyGroupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to use Study Groups.")

    fun observeMyGroups(userId: String): Flow<List<StudyGroupDoc>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "study_groups"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyGroupDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeGroupItems(groupId: String, userId: String): Flow<List<GroupItemDoc>> = flow {
        if (groupId.isBlank() || userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "study_groups/$groupId/group_items"
        emitAll(
            db.collection("study_groups")
                .document(groupId)
                .collection("group_items")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GroupItemDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createGroup(
        name: String,
        subjectTag: String,
        description: String,
        privateGroup: Boolean,
        ownerDisplayName: String
    ): Result<String> {
        val groupId = "grp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim()
            require(cleanName.length in 2..80) { "Group name must be 2 to 80 characters." }

            val inviteCode = UUID.randomUUID().toString()
                .replace("-", "")
                .uppercase()
                .take(6)

            val groupDoc = StudyGroupDoc(
                ownerId = uid,
                name = cleanName,
                subjectTag = subjectTag.trim().take(40),
                description = description.trim().take(300),
                inviteCode = inviteCode,
                privateGroup = privateGroup,
                memberUids = listOf(uid),
                memberNames = listOf(ownerDisplayName.ifBlank { "Student" }.take(40))
            )
            val inviteDoc = GroupInviteDoc(
                inviteCode = inviteCode,
                groupId = groupId,
                ownerId = uid,
                groupName = cleanName
            )

            val batch = db.batch()
            batch.set(db.collection("study_groups").document(groupId), groupDoc.toCreateMap())
            batch.set(db.collection("group_invites").document(inviteCode), inviteDoc.toCreateMap())
            batch.commit().await()
            Result.success(inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "study_groups/$groupId")
            Result.failure(e)
        }
    }

    suspend fun joinGroupByCode(rawCode: String, memberName: String): Result<String> {
        val code = rawCode.trim().uppercase()
        return try {
            val uid = requireUserId()
            require(code.length in 6..12 && code.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
                "Enter a valid 6-character invite code."
            }
            val inviteSnap = db.collection("group_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("No study group found for code $code."))
            }
            val groupId = inviteSnap.getString("groupId")
                ?: return Result.failure(IllegalStateException("Invalid invite record."))
            val groupRef = db.collection("study_groups").document(groupId)
            val groupSnap = groupRef.get().await()
            val existingGroup = groupSnap.toObject(StudyGroupDoc::class.java)
                ?: return Result.failure(IllegalStateException("Study group no longer exists."))

            if (uid in existingGroup.memberUids) {
                return Result.success("Already a member of ${existingGroup.name}.")
            }
            if (existingGroup.memberUids.size >= 30) {
                return Result.failure(IllegalStateException("This study group is full (30 members max)."))
            }

            val updatedUids = existingGroup.memberUids + uid
            val updatedNames = existingGroup.memberNames + memberName.ifBlank { "Student" }.take(40)
            groupRef.update(
                mapOf(
                    "memberUids" to updatedUids,
                    "memberNames" to updatedNames,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success("Joined ${existingGroup.name}!")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "group_invites/$code")
            Result.failure(e)
        }
    }

    suspend fun postGroupItem(
        group: StudyGroupDoc,
        authorName: String,
        itemType: String,
        title: String,
        body: String,
        score: Int = 0
    ): Result<Unit> {
        val itemId = "item_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        return try {
            val uid = requireUserId()
            require(title.trim().isNotEmpty()) { "Title cannot be empty." }
            val item = GroupItemDoc(
                groupId = group.id,
                authorId = uid,
                authorName = authorName.ifBlank { "Student" }.take(60),
                itemType = itemType,
                title = title.trim().take(120),
                body = body.trim().take(1500),
                completed = false,
                score = score.toLong(),
                memberUids = group.memberUids
            )
            db.collection("study_groups")
                .document(group.id)
                .collection("group_items")
                .document(itemId)
                .set(item.toCreateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "study_groups/${group.id}/group_items/$itemId")
            Result.failure(e)
        }
    }

    suspend fun toggleGroupItemCompleted(groupId: String, item: GroupItemDoc): Result<Unit> {
        return try {
            requireUserId()
            db.collection("study_groups")
                .document(groupId)
                .collection("group_items")
                .document(item.id)
                .update(
                    mapOf(
                        "completed" to !item.completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "study_groups/$groupId/group_items/${item.id}")
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(group: StudyGroupDoc): Result<Unit> {
        return try {
            val uid = requireUserId()
            val groupRef = db.collection("study_groups").document(group.id)
            if (group.ownerId == uid) {
                val batch = db.batch()
                batch.delete(groupRef)
                if (group.inviteCode.isNotBlank()) {
                    batch.delete(db.collection("group_invites").document(group.inviteCode))
                }
                batch.commit().await()
            } else {
                val idx = group.memberUids.indexOf(uid)
                if (idx >= 0) {
                    val newUids = group.memberUids.toMutableList().apply { removeAt(idx) }
                    val newNames = group.memberNames.toMutableList().apply {
                        if (idx < size) removeAt(idx)
                    }
                    groupRef.update(
                        mapOf(
                            "memberUids" to newUids,
                            "memberNames" to newNames,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "study_groups/${group.id}")
            Result.failure(e)
        }
    }
}
