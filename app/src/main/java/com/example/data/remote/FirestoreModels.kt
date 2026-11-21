package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class StudyGroupDoc(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val subjectTag: String = "",
    val description: String = "",
    val inviteCode: String = "",
    val privateGroup: Boolean = true,
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "ownerId" to ownerId,
            "name" to name.take(80),
            "subjectTag" to subjectTag.take(40),
            "description" to description.take(300),
            "inviteCode" to inviteCode.take(12),
            "privateGroup" to privateGroup,
            "memberUids" to memberUids.take(30),
            "memberNames" to memberNames.take(30),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class GroupInviteDoc(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val groupId: String = "",
    val ownerId: String = "",
    val groupName: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "inviteCode" to inviteCode.take(12),
        "groupId" to groupId,
        "ownerId" to ownerId,
        "groupName" to groupName.take(80),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class GroupItemDoc(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val itemType: String = "TASK", // GOAL, NOTE, TASK, QUIZ
    val title: String = "",
    val body: String = "",
    val completed: Boolean = false,
    val score: Long = 0L,
    val memberUids: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "groupId" to groupId,
            "authorId" to authorId,
            "authorName" to authorName.take(60),
            "itemType" to itemType,
            "title" to title.take(120),
            "body" to body.take(1500),
            "completed" to completed,
            "score" to score.coerceIn(0L, 1000L),
            "memberUids" to memberUids.take(30),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}
