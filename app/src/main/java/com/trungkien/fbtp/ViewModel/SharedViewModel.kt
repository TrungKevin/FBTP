package com.trungkien.fbtp.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.trungkien.fbtp.model.TimeSlot

class TimeSlotViewModel : ViewModel() {
    private val _timeSlots = MutableLiveData<List<TimeSlot>>(emptyList())
    val timeSlots: LiveData<List<TimeSlot>> get() = _timeSlots

    fun updateTimeSlots(newTimeSlots: List<TimeSlot>) {
        _timeSlots.value = newTimeSlots
    }

    fun addTimeSlot(timeSlot: TimeSlot) {
        val currentList = _timeSlots.value.orEmpty().toMutableList()
        currentList.add(timeSlot)
        _timeSlots.value = currentList
    }

    fun removeTimeSlot(position: Int) {
        val currentList = _timeSlots.value.orEmpty().toMutableList()
        if (position in currentList.indices) {
            currentList.removeAt(position)
            _timeSlots.value = currentList
        }
    }

    fun clearTimeSlots() {
        _timeSlots.value = emptyList()
    }
}