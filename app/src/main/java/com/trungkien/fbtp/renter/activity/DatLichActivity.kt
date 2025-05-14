package com.trungkien.fbtp.renter.activity

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.Adapter.CalendarDay
import com.trungkien.fbtp.Adapter.CalendarDayAdapter
import com.trungkien.fbtp.Adapter.KhungGioAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ActivityDatLichBinding
import com.trungkien.fbtp.model.TimeFrame
import com.trungkien.fbtp.model.TimeSlot
import java.text.SimpleDateFormat
import java.util.*

class DatLichActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatLichBinding
    private lateinit var calendarDayAdapter: CalendarDayAdapter
    private lateinit var khungGioAdapter: KhungGioAdapter
    private var timeFrames: List<TimeFrame> = emptyList()
    private var selectedDate: String = ""
    private var selectedTimeSlot: TimeSlot? = null
    private var coSoID: String = ""
    private var courtID: String = ""
    private var courtType: String = ""
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "DatLichActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDatLichBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initDataFromIntent()
        setupToolbar()
        setupRecyclerViews()
        setupListeners()
        populateCalendar()
    }

    private fun initDataFromIntent() {
        coSoID = intent.getStringExtra("coSoID") ?: run {
            showErrorAndFinish("Thiếu coSoID")
            return
        }
        courtID = intent.getStringExtra("courtID") ?: run {
            showErrorAndFinish("Thiếu courtID")
            return
        }
        courtType = intent.getStringExtra("courtType") ?: run {
            showErrorAndFinish("Thiếu courtType")
            return
        }
        timeFrames = intent.getParcelableArrayListExtra<TimeFrame>("timeFrames") ?: emptyList()
        Log.d(TAG, "coSoID: $coSoID, courtID: $courtID, courtType: $courtType, timeFrames: $timeFrames")
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        // Setup rcv_day_order
        calendarDayAdapter = CalendarDayAdapter(emptyList()) { date ->
            selectedDate = date
            updateTimeSlots()
        }
        binding.rcvDayOrder.apply {
            adapter = calendarDayAdapter
            layoutManager = LinearLayoutManager(this@DatLichActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        // Setup rcv_time_order
        khungGioAdapter = KhungGioAdapter(emptyList()) { position ->
            selectedTimeSlot = khungGioAdapter.timeSlots[position]
            Toast.makeText(this, "Chọn khung giờ: ${selectedTimeSlot?.period}", Toast.LENGTH_SHORT).show()
        }
        binding.rcvTimeOrder.apply {
            adapter = khungGioAdapter
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@DatLichActivity, 3)
        }
    }

    private fun setupListeners() {
        binding.btnAcpDatlich.setOnClickListener {
            if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ngày", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedTimeSlot == null) {
                Toast.makeText(this, "Vui lòng chọn khung giờ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmBooking()
        }
    }

    private fun populateCalendar() {
        val calendarDays = mutableListOf<CalendarDay>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // Generate 7 days or use dates from timeFrames
        val dates = if (timeFrames.isNotEmpty()) {
            timeFrames.map { it.date }.distinct()
        } else {
            (0..6).map {
                val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, it) }
                dateFormat.format(date.time)
            }
        }

        dates.forEach { dateStr ->
            val date = dateFormat.parse(dateStr) ?: return@forEach
            calendar.time = date
            calendarDays.add(
                CalendarDay(
                    date = dateStr,
                    dayOfWeek = getDayOfWeek(calendar),
                    day = calendar.get(Calendar.DAY_OF_MONTH).toString(),
                    month = "Tháng ${calendar.get(Calendar.MONTH) + 1}"
                )
            )
        }

        calendarDayAdapter.updateData(calendarDays)
        if (calendarDays.isNotEmpty()) {
            selectedDate = calendarDays[0].date
            calendarDayAdapter.notifyItemChanged(0)
            updateTimeSlots()
        }
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

    private fun updateTimeSlots() {
        val timeSlots = mutableListOf<TimeSlot>()
        val timeFrame = timeFrames.find { it.date == selectedDate }
        timeFrame?.period?.forEach { period ->
            // Check Firestore for booking status
            firestore.collection("bookings")
                .whereEqualTo("coSoID", coSoID)
                .whereEqualTo("courtID", courtID)
                .whereEqualTo("date", selectedDate)
                .whereEqualTo("period", period)
                .get()
                .addOnSuccessListener { documents ->
                    val isBooked = !documents.isEmpty
                    timeSlots.add(
                        TimeSlot(
                            price = 0.0,
                            courtSize = courtType,
                            period = period,
                            session = "Sáng",
                            isTimeRange = true,
                            courtID = courtID,
                            coSoID = coSoID,
                            date = selectedDate,
                            isBooked = isBooked
                        )
                    )
                    khungGioAdapter.updateData(timeSlots.sortedBy { it.period })
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking booking status", e)
                    timeSlots.add(
                        TimeSlot(
                            price = 0.0,
                            courtSize = courtType,
                            period = period,
                            session = "Sáng",
                            isTimeRange = true,
                            courtID = courtID,
                            coSoID = coSoID,
                            date = selectedDate,
                            isBooked = false
                        )
                    )
                    khungGioAdapter.updateData(timeSlots.sortedBy { it.period })
                }
        } ?: run {
            khungGioAdapter.updateData(emptyList())
        }
    }

    private fun confirmBooking() {
        val bookingData = hashMapOf(
            "coSoID" to coSoID,
            "courtID" to courtID,
            "date" to selectedDate,
            "period" to selectedTimeSlot?.period,
            "courtSize" to courtType,
            "userID" to "user_id_placeholder", // Replace with actual user ID
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("bookings")
            .add(bookingData)
            .addOnSuccessListener {
                Toast.makeText(this, "Đặt lịch thành công!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error booking", e)
                Toast.makeText(this, "Đặt lịch thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}