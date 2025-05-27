package com.trungkien.fbtp.renter.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.trungkien.fbtp.Adapter.CalendarDay
import com.trungkien.fbtp.Adapter.CalendarDayAdapter
import com.trungkien.fbtp.Adapter.KhungGioAdapter
import com.trungkien.fbtp.MainActivity
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ActivityDatLichBinding
import com.trungkien.fbtp.model.Booking
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.TimeFrame
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class DatLichActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatLichBinding
    private lateinit var calendarDayAdapter: CalendarDayAdapter
    private lateinit var khungGioAdapter: KhungGioAdapter
    private var selectedDate: String = ""
    private val selectedTimeSlots = mutableListOf<TimeSlot>()
    private var coSoID: String = ""
    private var courtID: String = ""
    private var courtType: String = ""
    private var pricePerHour: Double = 0.0
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var referencePeriods: List<String>? = null

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

        coroutineScope.launch {
            try {
                val courtSnapshot = firestore.collection("courts")
                    .whereEqualTo("courtID", courtID)
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()
                val court = courtSnapshot.documents.firstOrNull()?.toObject(Court::class.java)
                if (court == null) {
                    showErrorAndFinish("Không tìm thấy sân với courtID: $courtID và coSoID: $coSoID")
                    return@launch
                }
                if (court.size != courtType) {
                    Log.w(TAG, "courtType từ intent ($courtType) không khớp với size từ Court (${court.size})")
                    courtType = court.size
                }
                pricePerHour = court.pricePerHour
                Log.d(TAG, "Initialized with coSoID: $coSoID, courtID: $courtID, courtType: $courtType, pricePerHour: $pricePerHour")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching court: ${e.message}", e)
                showErrorAndFinish("Lỗi khi tải thông tin sân: ${e.message}")
            }
        }
    }

    private fun showErrorAndFinish(message: String) {
        Log.e(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        calendarDayAdapter = CalendarDayAdapter(emptyList()) { date ->
            selectedDate = date
            selectedTimeSlots.clear()
            khungGioAdapter.setSelectedPositions(emptyList())
            updateTimeSlots()
            Log.d(TAG, "Selected date changed to: $date")
        }
        binding.rcvDayOrder.apply {
            adapter = calendarDayAdapter
            layoutManager =
                LinearLayoutManager(this@DatLichActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        khungGioAdapter = KhungGioAdapter(emptyList()) { position, isBooked, timeSlot ->
            if (isBooked) {
                // Cancel booking
                selectedTimeSlots.removeAll { it.period == timeSlot.period }
                val updatedTimeSlot = timeSlot.copy(isBooked = false)
                khungGioAdapter.updateTimeSlot(position, updatedTimeSlot)
                khungGioAdapter.setSelectedPositions(selectedTimeSlots.map { slot ->
                    khungGioAdapter.getData().indexOfFirst { it.period == slot.period }
                }.filter { it >= 0 })
                Toast.makeText(this, "Hủy chọn khung giờ ${timeSlot.period}", Toast.LENGTH_SHORT).show()
            } else {
                // Show confirmation dialog
                BookingDialogManager(this).showBookingConfirmationDialog(
                    timeSlot,
                    position
                ) { pos, updatedTimeSlot ->
                    if (!selectedTimeSlots.any { it.period == updatedTimeSlot.period }) {
                        selectedTimeSlots.add(updatedTimeSlot)
                    }
                    khungGioAdapter.updateTimeSlot(pos, updatedTimeSlot)
                    khungGioAdapter.setSelectedPositions(selectedTimeSlots.map { slot ->
                        khungGioAdapter.getData().indexOfFirst { it.period == slot.period }
                    }.filter { it >= 0 })
                }
                // Temporarily highlight
                val currentSelections = selectedTimeSlots.map { slot ->
                    khungGioAdapter.getData().indexOfFirst { it.period == slot.period }
                }.filter { it >= 0 } + position
                khungGioAdapter.setSelectedPositions(currentSelections)
            }
        }
        binding.rcvTimeOrder.apply {
            adapter = khungGioAdapter
            layoutManager = GridLayoutManager(this@DatLichActivity, 3)
        }
    }

    private fun notifyTimeSlotSelection() {
        khungGioAdapter.notifyDataSetChanged()
    }

    private fun setupListeners() {
        binding.btnAcpDatlich.setOnClickListener {
            if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ngày", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedTimeSlots.isEmpty() || selectedTimeSlots.none { it.isBooked == true }) {
                Toast.makeText(this, "Vui lòng xác nhận ít nhất một khung giờ trước khi đặt", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            coroutineScope.launch {
                try {
                    showLoading(true)
                    val periods = selectedTimeSlots.map { it.period }
                    Log.d(TAG, "Checking bookings for coSoID: $coSoID, courtID: $courtID, date: $selectedDate, periods: $periods")
                    for (period in periods) {
                        val bookingSnapshot = firestore.collection("bookings")
                            .whereEqualTo("coSoID", coSoID)
                            .whereEqualTo("courtID", courtID)
                            .whereEqualTo("bookingDate", selectedDate)
                            .whereEqualTo("period", period)
                            .whereEqualTo("booked", true)
                            .get()
                            .await()
                        if (!bookingSnapshot.isEmpty) {
                            Toast.makeText(this@DatLichActivity, "Khung giờ $period đã được đặt, vui lòng chọn khung giờ khác", Toast.LENGTH_SHORT).show()
                            selectedTimeSlots.clear()
                            khungGioAdapter.setSelectedPositions(emptyList())
                            updateTimeSlots()
                            return@launch
                        }
                    }
                    confirmBooking()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking booking status: ${e.message}", e)
                    Toast.makeText(this@DatLichActivity, "Lỗi khi kiểm tra trạng thái đặt lịch: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressContainer.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun populateCalendar() {
        coroutineScope.launch {
            showLoading(true)
            try {
                Log.d(TAG, "Fetching timeframes for calendar, coSoID: $coSoID, courtID: $courtID")
                val snapshot: QuerySnapshot = firestore.collection("time_frames")
                    .whereEqualTo("coSoID", coSoID)
                    .whereEqualTo("courtID", courtID)
                    .get()
                    .await()
                Log.d(TAG, "Fetched ${snapshot.documents.size} timeframes for calendar")
                val availableDates = snapshot.documents
                    .mapNotNull { it.toObject(TimeFrame::class.java)?.date }
                    .distinct()

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val calendarDays = mutableListOf<CalendarDay>()
                val today = Calendar.getInstance()
                for (i in 0 until 14) {
                    val date = Calendar.getInstance().apply {
                        time = today.time
                        add(Calendar.DAY_OF_YEAR, i)
                    }
                    val dateStr = dateFormat.format(date.time)
                    val calendarDay = CalendarDayAdapter.createCalendarDay(dateStr, isFutureOnly = false)?.copy(
                        isEnabled = true
                    ) ?: continue
                    calendarDays.add(calendarDay)
                }

                Log.d(TAG, "Updating rcv_day_order with ${calendarDays.size} days")
                calendarDayAdapter.updateData(calendarDays)
                if (calendarDays.isNotEmpty()) {
                    selectedDate = calendarDays[0].date
                    calendarDayAdapter.notifyItemChanged(0)
                    updateTimeSlots()
                } else {
                    Log.w(TAG, "No calendar days generated")
                    Toast.makeText(this@DatLichActivity, "Không thể tạo lịch do lỗi dữ liệu", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching calendar dates: ${e.message}", e)
                Toast.makeText(this@DatLichActivity, "Lỗi khi tải ngày: ${e.message}", Toast.LENGTH_SHORT).show()
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val calendarDays = mutableListOf<CalendarDay>()
                val today = Calendar.getInstance()
                for (i in 0 until 14) {
                    val date = Calendar.getInstance().apply {
                        time = today.time
                        add(Calendar.DAY_OF_YEAR, i)
                    }
                    val dateStr = dateFormat.format(date.time)
                    val calendarDay = CalendarDayAdapter.createCalendarDay(dateStr, isFutureOnly = false)?.copy(
                        isEnabled = true
                    ) ?: continue
                    calendarDays.add(calendarDay)
                }
                calendarDayAdapter.updateData(calendarDays)
                if (calendarDays.isNotEmpty()) {
                    selectedDate = calendarDays[0].date
                    calendarDayAdapter.notifyItemChanged(0)
                    updateTimeSlots()
                }
            } finally {
                showLoading(false)
            }
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

    private fun getDefaultPeriodsByCourtType(courtType: String): List<String> {
        return when (courtType) {
            "5v5" -> listOf(
                "08:00-10:00",
                "10:00-12:00",
                "14:00-16:00",
                "16:00-18:00",
                "18:00-20:00",
                "20:00-22:00"
            )
            "7v7" -> listOf(
                "09:00-11:00",
                "11:00-13:00",
                "15:00-17:00",
                "17:00-19:00",
                "19:00-21:00"
            )
            else -> listOf(
                "08:00-10:00",
                "10:00-12:00",
                "14:00-16:00",
                "16:00-18:00",
                "18:00-20:00",
                "20:00-22:00"
            )
        }
    }

    private fun updateTimeSlots() {
        coroutineScope.launch {
            showLoading(true)
            try {
                Log.d(TAG, "Fetching time_frames for date: $selectedDate")
                val snapshot = firestore.collection("time_frames")
                    .whereEqualTo("coSoID", coSoID)
                    .whereEqualTo("courtID", courtID)
                    .whereEqualTo("date", selectedDate)
                    .get()
                    .await()
                val timeSlots = mutableListOf<TimeSlot>()
                val timeFrame = snapshot.documents.firstOrNull()?.toObject(TimeFrame::class.java)

                if (referencePeriods == null) {
                    referencePeriods = if (timeFrame != null && timeFrame.period.isNotEmpty()) {
                        timeFrame.period
                    } else {
                        getDefaultPeriodsByCourtType(courtType)
                    }
                    Log.d(TAG, "Initialized referencePeriods: $referencePeriods")
                }

                val periods = referencePeriods ?: getDefaultPeriodsByCourtType(courtType)

                for (period in periods) {
                    val isBooked = timeFrame?.bookedPeriods?.get(period) ?: false
                    val isSelected = selectedTimeSlots.any { it.period == period && it.isBooked == true }
                    timeSlots.add(
                        TimeSlot(
                            price = pricePerHour,
                            courtSize = courtType,
                            period = period,
                            session = when {
                                period.startsWith("08") || period.startsWith("09") ||
                                        period.startsWith("10") || period.startsWith("11") -> "Sáng"
                                period.startsWith("12") || period.startsWith("13") ||
                                        period.startsWith("14") || period.startsWith("15") ||
                                        period.startsWith("16") -> "Chiều"
                                else -> "Tối"
                            },
                            isTimeRange = true,
                            courtID = courtID,
                            coSoID = coSoID,
                            date = selectedDate,
                            isBooked = isBooked || isSelected
                        )
                    )
                }

                Log.d(TAG, "Updating adapter with ${timeSlots.size} slots")
                khungGioAdapter.updateData(timeSlots)
                khungGioAdapter.setSelectedPositions(selectedTimeSlots.map { slot ->
                    timeSlots.indexOfFirst { it.period == slot.period }
                }.filter { it >= 0 })
                binding.rcvTimeOrder.visibility = View.VISIBLE
                binding.rcvTimeOrder.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching time slots: ${e.message}", e)
                val timeSlots = mutableListOf<TimeSlot>()
                val periods = referencePeriods ?: getDefaultPeriodsByCourtType(courtType)
                for (period in periods) {
                    val isSelected = selectedTimeSlots.any { it.period == period && it.isBooked == true }
                    timeSlots.add(
                        TimeSlot(
                            price = pricePerHour,
                            courtSize = courtType,
                            period = period,
                            session = when {
                                period.startsWith("08") || period.startsWith("09") ||
                                        period.startsWith("10") || period.startsWith("11") -> "Sáng"
                                period.startsWith("12") || period.startsWith("13") ||
                                        period.startsWith("14") || period.startsWith("15") ||
                                        period.startsWith("16") -> "Chiều"
                                else -> "Tối"
                            },
                            isTimeRange = true,
                            courtID = courtID,
                            coSoID = coSoID,
                            date = selectedDate,
                            isBooked = isSelected
                        )
                    )
                }
                Toast.makeText(this@DatLichActivity, "Lỗi khi tải khung giờ, hiển thị khung giờ mặc định", Toast.LENGTH_SHORT).show()
                khungGioAdapter.updateData(timeSlots)
                khungGioAdapter.setSelectedPositions(selectedTimeSlots.map { slot ->
                    timeSlots.indexOfFirst { it.period == slot.period }
                }.filter { it >= 0 })
                binding.rcvTimeOrder.visibility = View.VISIBLE
                binding.rcvTimeOrder.isEnabled = true
            } finally {
                showLoading(false)
            }
        }
    }

    private fun calculateTotalPrice(period: String, pricePerHour: Double): Double {
        try {
            val parts = period.split("-")
            if (parts.size != 2) return 0.0
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val start = timeFormat.parse(parts[0])?.time ?: return 0.0
            val end = timeFormat.parse(parts[1])?.time ?: return 0.0
            val durationHours = (end - start) / (1000.0 * 60 * 60)
            return durationHours * pricePerHour
        } catch (e: ParseException) {
            Log.e(TAG, "Error parsing period: $period, ${e.message}")
            return 0.0
        }
    }

    private fun confirmBooking() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để đặt lịch", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedTimeSlots.isEmpty()) {
            Log.w(TAG, "No time slots selected")
            Toast.makeText(this, "Không có khung giờ được chọn", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            try {
                showLoading(true)
                val periods = selectedTimeSlots.map { it.period }
                Log.d(TAG, "Confirming bookings for periods: $periods, date: $selectedDate, coSoID: $coSoID, courtID: $courtID, userID: ${auth.currentUser?.uid}")

                if (periods.any { it.isEmpty() } || selectedDate.isEmpty() || coSoID.isEmpty() || courtID.isEmpty()) {
                    throw IllegalArgumentException("Invalid booking data: periods=$periods, date=$selectedDate, coSoID=$coSoID, courtID=$courtID")
                }

                periods.forEach { period ->
                    if (!period.matches(Regex("\\d{2}:\\d{2}-\\d{2}:\\d{2}"))) {
                        throw IllegalArgumentException("Invalid period format: $period, expected HH:mm-HH:mm")
                    }
                }

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dateFormat.isLenient = false
                try {
                    dateFormat.parse(selectedDate)
                } catch (e: ParseException) {
                    throw IllegalArgumentException("Invalid date format: $selectedDate, expected dd/MM/yyyy")
                }

                Log.d(TAG, "Querying time_frames for coSoID: $coSoID, courtID: $courtID, date: $selectedDate")
                val timeFrameSnapshot: QuerySnapshot = firestore.collection("time_frames")
                    .whereEqualTo("coSoID", coSoID)
                    .whereEqualTo("courtID", courtID)
                    .whereEqualTo("date", selectedDate)
                    .get()
                    .await()
                Log.d(TAG, "Found ${timeFrameSnapshot.documents.size} time_frame documents")
                if (timeFrameSnapshot.documents.size > 1) {
                    Log.w(TAG, "Multiple time_frames found. Using first document.")
                }
                val timeFrameDoc = timeFrameSnapshot.documents.firstOrNull()
                val timeFrame = timeFrameDoc?.toObject(TimeFrame::class.java)
                Log.d(TAG, "TimeFrame: $timeFrame")

                firestore.runTransaction { transaction ->
                    selectedTimeSlots.forEach { timeSlot ->
                        val period = timeSlot.period
                        val totalPrice = calculateTotalPrice(period, pricePerHour)
                        val booking = Booking(
                            bookingID = UUID.randomUUID().toString(),
                            userID = auth.currentUser!!.uid,
                            courtID = courtID,
                            facilityID = coSoID,
                            bookingDate = selectedDate,
                            period = period,
                            status = "confirmed",
                            booked = true,
                            totalPrice = totalPrice,
                            notes = "",
                            createdAt = System.currentTimeMillis()
                        )
                        val bookingRef = firestore.collection("bookings").document(booking.bookingID)
                        Log.d(TAG, "Writing booking to ${bookingRef.path}: $booking")
                        transaction.set(bookingRef, booking)
                    }

                    if (timeFrameDoc != null && timeFrame != null) {
                        val timeFrameRef = timeFrameDoc.reference
                        Log.d(TAG, "Updating time_frame at ${timeFrameRef.path}")
                        val updatedPeriods = (timeFrame.period + periods).distinct()
                        val updatedBookedPeriods = timeFrame.bookedPeriods.toMutableMap().apply {
                            periods.forEach { put(it, true) }
                        }
                        transaction.update(timeFrameRef, mapOf(
                            "period" to updatedPeriods,
                            "bookedPeriods" to updatedBookedPeriods
                        ))
                    } else {
                        val newTimeFrame = TimeFrame(
                            timeFrameID = UUID.randomUUID().toString(),
                            courtID = courtID,
                            coSoID = coSoID,
                            date = selectedDate,
                            period = periods,
                            courtSize = courtType,
                            bookedPeriods = periods.associateWith { true }
                        )
                        val timeFrameRef = firestore.collection("time_frames").document(newTimeFrame.timeFrameID)
                        Log.d(TAG, "Creating new time_frame at ${timeFrameRef.path}: $newTimeFrame")
                        transaction.set(timeFrameRef, newTimeFrame)
                    }
                    null
                }.await()

                Log.d(TAG, "Bookings successful for ${selectedTimeSlots.size} time slots")
                Toast.makeText(this@DatLichActivity, "Đặt ${selectedTimeSlots.size} khung giờ thành công!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@DatLichActivity, MainActivity::class.java)
                intent.putExtra("navigate_to", "SecondFragment")
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {

                val userMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> {
                        "Không có quyền đặt lịch. Vui lòng kiểm tra quyền truy cập. Lỗi: ${e.message}"
                    }
                    e.message?.contains("network") == true -> "Lỗi kết nối mạng. Vui lòng thử lại."
                    e is IllegalArgumentException -> "Dữ liệu không hợp lệ: ${e.message}"
                    else -> "Đặt lịch thất bại: ${e.message ?: "Lỗi không xác định"}"
                }
                Toast.makeText(this@DatLichActivity, userMessage, Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }
}