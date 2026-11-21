package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.GroupItemDoc
import com.example.data.remote.StudyGroupDoc
import com.example.data.remote.StudyGroupRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StudyGroupsViewModel(
    private val repository: StudyGroupRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val groupsState: StateFlow<UiState<List<StudyGroupDoc>>> =
        repository.observeMyGroups(currentUserId)
            .map<List<StudyGroupDoc>, UiState<List<StudyGroupDoc>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing study groups", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load study groups."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val groupItemsState: StateFlow<UiState<List<GroupItemDoc>>> =
        _selectedGroupId.flatMapLatest { groupId ->
            if (groupId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeGroupItems(groupId, currentUserId)
                    .map<List<GroupItemDoc>, UiState<List<GroupItemDoc>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing group items", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "Failed to load group items."))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun selectGroup(groupId: String?) {
        _selectedGroupId.value = groupId
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun createGroup(
        name: String,
        subjectTag: String,
        description: String,
        privateGroup: Boolean,
        ownerDisplayName: String
    ) {
        viewModelScope.launch {
            repository.createGroup(name, subjectTag, description, privateGroup, ownerDisplayName)
                .onSuccess { code -> _feedbackMessage.value = "Group created! Invite code: $code" }
                .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "Could not create group." }
        }
    }

    fun joinByCode(code: String, memberName: String) {
        viewModelScope.launch {
            repository.joinGroupByCode(code, memberName)
                .onSuccess { msg -> _feedbackMessage.value = msg }
                .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "Could not join group." }
        }
    }

    fun postItem(
        group: StudyGroupDoc,
        authorName: String,
        itemType: String,
        title: String,
        body: String
    ) {
        viewModelScope.launch {
            repository.postGroupItem(group, authorName, itemType, title, body)
                .onSuccess { _feedbackMessage.value = "Shared with ${group.name}" }
                .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "Failed to post item." }
        }
    }

    fun toggleItemCompleted(groupId: String, item: GroupItemDoc) {
        viewModelScope.launch {
            repository.toggleGroupItemCompleted(groupId, item)
                .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "Update failed." }
        }
    }

    fun leaveGroup(group: StudyGroupDoc) {
        viewModelScope.launch {
            repository.leaveGroup(group)
                .onSuccess {
                    if (_selectedGroupId.value == group.id) _selectedGroupId.value = null
                    _feedbackMessage.value = if (group.ownerId == currentUserId) "Group deleted." else "Left ${group.name}."
                }
                .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "Action failed." }
        }
    }

    private companion object {
        const val TAG = "StudyGroupsVM"
    }
}
