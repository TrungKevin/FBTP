package com.trungkien.fbtp.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {
    private val _profileImageUpdated = MutableLiveData<Unit>()
    val profileImageUpdated: LiveData<Unit> get() = _profileImageUpdated

    fun notifyProfileImageUpdated() {
        _profileImageUpdated.postValue(Unit)
    }
}