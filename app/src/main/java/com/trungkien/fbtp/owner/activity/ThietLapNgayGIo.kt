package com.trungkien.fbtp.owner.activity

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.Adapter.KhungGioAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ActivityThietLapNgayGioBinding
import com.trungkien.fbtp.model.TimeFrame
import com.trungkien.fbtp.model.TimeSlot
import java.text.SimpleDateFormat
import java.util.*

class ThietLapNgayGio : AppCompatActivity() {

    private lateinit var binding: ActivityThietLapNgayGioBinding
    private lateinit var khungGioAdapter: KhungGioAdapter
    private val timeSlotsByDate = mutableMapOf<String, MutableList<TimeSlot>>()
    private var coSoID: String = ""
    private var courtID: String = ""
    private var courtType: String = ""
    private var selectedDate: String = ""
    private val calendarDates = mutableListOf<String>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val RESULT_TIME_FRAME = "result_time_frames"
        private const val TAG = "ThietLapNgayGio"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThietLapNgayGioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAuthentication()
        initDataFromIntent()
        setupRecyclerView()
        setupListeners()
        setupCalendar()
    }

    private fun checkAuthentication() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để tiếp tục", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AccountActivity::class.java))
            finish()
        }
    }

    private fun initDataFromIntent() {
        coSoID = intent.getStringExtra("coSoID") ?: run {
            showErrorAndFinish("Thiếu coSoID")
            return
        }
        courtType = intent.getStringExtra("courtType") ?: run {
            showErrorAndFinish("Thiếu courtType")
            return
        }
        courtID = intent.getStringExtra("courtID") ?: run {
            showErrorAndFinish("Thiếu courtID")
            return
        }
        Log.d(TAG, "coSoID: $coSoID, courtType: $courtType, courtID: $courtID, userUID: ${auth.currentUser?.uid}")
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupRecyclerView() {
        khungGioAdapter = KhungGioAdapter(emptyList()) { position, _, _ ->
            if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ngày trước khi chỉnh sửa hoặc xóa", Toast.LENGTH_SHORT).show()
                binding.scVCalendaThietLap.smoothScrollTo(0, 0)
            } else {
                val timeSlot = timeSlotsByDate[selectedDate]?.get(position)
                timeSlot?.let { showEditOrDeleteDialog(position, it) }
            }
        }
        binding.rvKhungGio.apply {
            adapter = khungGioAdapter
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@ThietLapNgayGio, 3)
        }
        // Initialize with empty list until a date is selected
        updateRecyclerView()
    }

    private fun setupListeners() {
        binding.btnAddKhungGio.setOnClickListener {
            if (calendarDates.isEmpty()) {
                Toast.makeText(this, "Không có ngày nào để chọn", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSelectDateDialog(calendarDates.firstOrNull() ?: "") { date ->
                selectedDate = date
                showTimePickerDialog { period ->
                    timeSlotsByDate.getOrPut(selectedDate) { mutableListOf() }.add(createTimeSlot(period))
                    updateRecyclerView()
                }
            }
        }

        binding.btnXacNhan.setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun createTimeSlot(period: String): TimeSlot {
        return TimeSlot(
            price = 0.0,
            courtSize = courtType,
            period = period,
            session = when {
                period.startsWith("08") || period.startsWith("09") || period.startsWith("10") || period.startsWith("11") -> "Sáng"
                period.startsWith("12") || period.startsWith("13") || period.startsWith("14") || period.startsWith("15") || period.startsWith("16") -> "Chiều"
                else -> "Tối"
            },
            isTimeRange = true,
            courtID = courtID,
            coSoID = coSoID
        )
    }

    private fun validateAndSubmit() {
        if (timeSlotsByDate.isEmpty()) {
            Toast.makeText(this, "Vui lòng thêm ít nhất một khung giờ", Toast.LENGTH_SHORT).show()
            return
        }

        submitData()
    }

    private fun submitData() {
        val timeFrames = timeSlotsByDate.map { (date, slots) ->
            TimeFrame(
                timeFrameID = UUID.randomUUID().toString(),
                courtID = courtID,
                coSoID = coSoID,
                date = date,
                period = slots.map { it.period }
            )
        }
        Log.d(TAG, "Returning TimeFrames: $timeFrames")

        setResult(RESULT_OK, Intent().apply {
            putParcelableArrayListExtra(RESULT_TIME_FRAME, ArrayList(timeFrames))
        })
        finish()
    }

    private fun populateDefaultTimeSlots() {
        timeSlotsByDate.clear()
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        val intervalMinutes = if (courtType.equals("Football", ignoreCase = true)) 90 else 30

        calendarDates.forEach { date ->
            val slots = mutableListOf<TimeSlot>()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
            }
            val endTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 0)
            }

            while (calendar.before(endTime)) {
                val start = format.format(calendar.time)
                calendar.add(Calendar.MINUTE, intervalMinutes)
                if (calendar.after(endTime)) break
                val end = format.format(calendar.time)
                slots.add(createTimeSlot("$start-$end"))
            }
            timeSlotsByDate[date] = slots
        }
        updateRecyclerView()
    }

    private fun setupCalendar() {
        binding.calendarContainer.removeAllViews()
        calendarDates.clear()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Display 14 consecutive days
        (0..13).forEach { i ->
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val view = LayoutInflater.from(this).inflate(R.layout.item_calendar_day, binding.calendarContainer, false)

            view.findViewById<TextView>(R.id.tv_day).text = getDayOfWeek(date)
            view.findViewById<TextView>(R.id.tv_date).text = date.get(Calendar.DAY_OF_MONTH).toString()
            view.findViewById<TextView>(R.id.tv_month).text = "Tháng ${date.get(Calendar.MONTH) + 1}"
            val dateStr = dateFormat.format(date.time)
            calendarDates.add(dateStr)

            view.setOnClickListener {
                binding.calendarContainer.children.forEach { child ->
                    child.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                }
                view.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryLight))
                selectedDate = dateStr
                updateRecyclerView()
            }

            binding.calendarContainer.addView(view)
        }
        // Initialize default time slots for all 14 days
        populateDefaultTimeSlots()
    }

    private fun getDayOfWeek(calendar: Calendar): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            Calendar.SUNDAY -> "CN"
            else -> ""
        }
    }

    private fun showSelectDateDialog(defaultDate: String, onDateSelected: (String) -> Unit) {
        if (defaultDate.isNotEmpty() && calendarDates.contains(defaultDate)) {
            onDateSelected(defaultDate)
            return
        }

        Toast.makeText(this, "Vui lòng chọn ngày", Toast.LENGTH_SHORT).show()
        binding.scVCalendaThietLap.smoothScrollTo(0, 0)
    }

    private fun showEditOrDeleteDialog(position: Int, timeSlot: TimeSlot) {
        AlertDialog.Builder(this)
            .setTitle("Tùy chọn khung giờ")
            .setMessage("Bạn muốn chỉnh sửa hay xóa khung giờ ${timeSlot.period} cho ngày $selectedDate?")
            .setPositiveButton("Chỉnh sửa") { _, _ -> editTimeSlot(position, timeSlot) }
            .setNegativeButton("Xóa") { _, _ -> showDeleteConfirmation(position, timeSlot) }
            .setNeutralButton("Hủy", null)
            .show()
    }

    private fun showDeleteConfirmation(position: Int, timeSlot: TimeSlot) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn chắc chắn muốn xóa khung giờ ${timeSlot.period} cho ngày $selectedDate?")
            .setPositiveButton("Có") { _, _ ->
                timeSlotsByDate[selectedDate]?.removeAt(position)
                updateRecyclerView()
            }
            .setNegativeButton("Không", null)
            .show()
    }

    private fun showTimePickerDialog(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = if (calendar.get(Calendar.MINUTE) < 30) 0 else 30

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val normalizedStartMinute = if (selectedMinute < 30) 0 else 30
            val startTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, normalizedStartMinute)
            }

            TimePickerDialog(this, { _, endHour, endMinute ->
                val normalizedEndMinute = if (endMinute < 30) 0 else 30
                val endTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, endHour)
                    set(Calendar.MINUTE, normalizedEndMinute)
                }

                if (endTime.after(startTime)) {
                    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val period = "${format.format(startTime.time)}-${format.format(endTime.time)}"
                    onTimeSelected(period)
                } else {
                    Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show()
                }
            }, selectedHour, normalizedStartMinute, true).show()
        }, hour, minute, true).show()
    }

    private fun editTimeSlot(position: Int, oldTimeSlot: TimeSlot) {
        showTimePickerDialog { period ->
            val slots = timeSlotsByDate[selectedDate] ?: mutableListOf()
            slots.removeAt(position)
            slots.add(createTimeSlot(period))
            timeSlotsByDate[selectedDate] = slots
            updateRecyclerView()
        }
    }

    private fun updateRecyclerView() {
        val timeSlots = timeSlotsByDate[selectedDate] ?: emptyList()
        khungGioAdapter.updateData(timeSlots.sortedBy { it.period })
    }
}