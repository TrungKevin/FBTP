package com.trungkien.fbtp.ViewModel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserRoleViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var roleListener: ListenerRegistration? = null
    private var isRoleFetched = false

    private val _roleState = MutableLiveData<RoleState>(RoleState.Loading)
    val roleState: LiveData<RoleState> get() = _roleState

    // Expose role directly for compatibility
    private val _userRole = MutableLiveData<String>("renter")
    val userRole: LiveData<String> get() = _userRole

    companion object {
        private const val TAG = "UserRoleViewModel"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    init {
        // Monitor auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            val userId = firebaseAuth.currentUser?.uid
            Log.d(TAG, "Auth state changed: userId=$userId")
            if (userId != null) {
                isRoleFetched = false
                fetchUserRole()
            } else {
                _roleState.postValue(RoleState.Error("No authenticated user"))
                _userRole.postValue("renter")
                isRoleFetched = false
                roleListener?.remove()
                roleListener = null
            }
        }
    }

    fun fetchUserRole() {
        if (isRoleFetched && roleListener != null) {
            Log.d(TAG, "Role listener already active: ${_userRole.value}")
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            Log.w(TAG, "No authenticated user")
            _roleState.postValue(RoleState.Error("No authenticated user"))
            _userRole.postValue("renter")
            isRoleFetched = false
            return
        }

        _roleState.postValue(RoleState.Loading)
        roleListener?.remove() // Remove any existing listener

        viewModelScope.launch {
            var attempt = 0
            while (attempt < MAX_RETRIES) {
                try {
                    Log.d(TAG, "Attempt $attempt: Querying userID: $userId")
                    roleListener = db.collection("users")
                        .whereEqualTo("userID", userId)
                        .limit(1)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                                _roleState.postValue(RoleState.Error("Failed to fetch role: ${error.message}"))
                                _userRole.postValue("renter")
                                return@addSnapshotListener
                            }

                            val role = snapshot?.documents?.firstOrNull()?.getString("role") ?: "renter"
                            Log.d(TAG, "Fetched user role: $role")
                            _userRole.postValue(role)
                            _roleState.postValue(RoleState.Success(role))
                            isRoleFetched = true
                        }
                    break // Exit retry loop on successful listener setup
                } catch (e: Exception) {
                    attempt++
                    Log.e(TAG, "Error fetching user role (attempt $attempt): ${e.message}", e)
                    if (attempt >= MAX_RETRIES) {
                        _roleState.postValue(RoleState.Error("Max retries reached: ${e.message}"))
                        _userRole.postValue("renter")
                        isRoleFetched = true
                        break
                    }
                    delay(INITIAL_BACKOFF_MS * (1 shl attempt)) // Exponential backoff
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, removing role listener")
        roleListener?.remove()
        roleListener = null
    }

    sealed class RoleState {
        object Loading : RoleState()
        data class Success(val role: String) : RoleState()
        data class Error(val message: String) : RoleState()
    }
}