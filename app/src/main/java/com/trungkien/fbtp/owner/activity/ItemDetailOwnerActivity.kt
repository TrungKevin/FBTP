package com.trungkien.fbtp.owner.activity

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.trungkien.fbtp.Adapter.CalendarDay
import com.trungkien.fbtp.AddPriceBoardAdapter
import com.trungkien.fbtp.Adapter.CalendarDayAdapter
import com.trungkien.fbtp.Adapter.KhungGioAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.DetailItemOwnerBinding
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.model.TimeFrame
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ItemDetailOwnerActivity : AppCompatActivity() {

    private lateinit var binding: DetailItemOwnerBinding
    private lateinit var priceBoardAdapter: AddPriceBoardAdapter
    private lateinit var khungGioAdapter: KhungGioAdapter
    private val timeSlots = mutableListOf<TimeSlot>()
    private var coSoID: String = ""
    private var isEditing = false
    private var isUpdateSanPressed = false
    private var hasShownEmptyToast = false
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val ownerID: String by lazy { auth.currentUser?.uid ?: "" }
    private val timeFramesByDate = mutableMapOf<String, TimeFrame>()
    private var selectedDate: String? = null
    private var lastSnapshotUpdateTime = 0L
    private var lastTimePickerUpdateTime = 0L
    private var loadingOverlay: View? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageUri(it) }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { handleImageBitmap(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true

        if (cameraGranted && storageGranted) {
            showImageSourceDialog()
        } else {
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)

            if (shouldShowRationale) {
                showPermissionRationaleDialog()
            } else {
                Toast.makeText(this, "Quyền camera hoặc thư viện ảnh bị từ chối. Vui lòng cấp quyền trong cài đặt ứng dụng.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DetailItemOwnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        coSoID = intent.getStringExtra("coSoID") ?: ""
        if (coSoID.isEmpty() || ownerID.isEmpty()) {
            Toast.makeText(this, "Dữ liệu không hợp lệ", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupLoadingOverlay()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val migrationCompleted = prefs.getBoolean("firestore_migration_completed", false)
        if (!migrationCompleted) {
            migrateFirestoreFields()
        }

        val image = intent.getStringExtra("image")
        if (!image.isNullOrEmpty()) {
            try {
                if (image.startsWith("http")) {
                    Glide.with(this)
                        .load(image)
                        .placeholder(R.drawable.sanbong)
                        .error(R.drawable.sanbong)
                        .into(binding.imgDetailOwner)
                } else {
                    val decodedBytes = Base64.decode(image, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding.imgDetailOwner.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error loading image", e)
                binding.imgDetailOwner.setImageResource(R.drawable.sanbong)
            }
        }

        setupRecyclerView()
        setupListeners()
        setupCalendar()
        loadFacilityDetails()
        listenToFirestoreChanges()
        loadTimeFrames()
    }

    private fun setupLoadingOverlay() {
        loadingOverlay = View(this).apply {
            layoutParams = binding.root.layoutParams
            setBackgroundColor(ContextCompat.getColor(this@ItemDetailOwnerActivity, android.R.color.black))
            alpha = 0.5f
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }
        (binding.root as? ViewGroup)?.addView(loadingOverlay)
    }

    private fun showLoading() {
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
            loadingOverlay?.visibility = View.VISIBLE
        }
    }

    private fun hideLoading() {
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            loadingOverlay?.visibility = View.GONE
        }
    }

    private fun setupCalendar() {
        binding.rcvCalendaOwner.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val calendarDays = mutableListOf<CalendarDay>()

        for (i in 0 until 14) {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val dateStr = dateFormat.format(date.time)
            val calendarDay = CalendarDayAdapter.createCalendarDay(dateStr, isFutureOnly = false)
            calendarDay?.let { calendarDays.add(it) }
        }

        val calendarAdapter = CalendarDayAdapter(calendarDays) { date ->
            selectedDate = date
            updateTimeSlotsForDate()
        }
        binding.rcvCalendaOwner.adapter = calendarAdapter
    }

    private suspend fun getCourtSize(courtID: String): String? {
        return try {
            val courtDoc = db.collection("courts").document(courtID).get().await()
            courtDoc.toObject(Court::class.java)?.size ?: "Unknown"
        } catch (e: Exception) {
            Log.e("ItemDetailOwnerActivity", "Error fetching court size for courtID: $courtID, ${e.message}", e)
            "Unknown"
        }
    }

    private fun loadTimeFrames() {
        lifecycleScope.launch {
            try {
                showLoading()
                val snapshot = db.collection("time_frames")
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()
                timeFramesByDate.clear()
                var validTimeFrames = 0
                for (doc in snapshot.documents) {
                    val timeFrame = doc.toObject(TimeFrame::class.java)
                    if (timeFrame != null && timeFrame.date.isNotEmpty() && timeFrame.period.isNotEmpty()) {
                        timeFramesByDate[timeFrame.date] = timeFrame
                        validTimeFrames++
                    } else {
                        Log.w("ItemDetailOwnerActivity", "Invalid time frame data: ${doc.id}, data: ${doc.data}")
                    }
                }
                Log.d("ItemDetailOwnerActivity", "Loaded $validTimeFrames valid time frames for coSoID: $coSoID")
                updateTimeSlotsForDate()
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error loading time frames for coSoID: $coSoID, ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải khung giờ: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.rcvKhungGioOwner.visibility = View.GONE
                    binding.emptyStateView.visibility = View.VISIBLE
                }
                updateTimeSlotsForDate()
            } finally {
                hideLoading()
            }
        }
    }

    private fun generateDefaultTimeSlots(courtSize: String, courtID: String): List<TimeSlot> {
        val defaultPeriods = listOf(
            "08:00-09:00", "09:00-10:00", "10:00-11:00", "11:00-12:00",
            "12:00-13:00", "13:00-14:00", "14:00-15:00", "15:00-16:00",
            "16:00-17:00", "17:00-18:00", "18:00-19:00", "19:00-20:00",
            "20:00-21:00", "21:00-22:00"
        )
        return defaultPeriods.map { period ->
            TimeSlot(
                price = 0.0,
                courtSize = courtSize,
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
        }.sortedBy { it.period }
    }

    private fun updateTimeSlotsForDate() {
        lifecycleScope.launch {
            val selectedTimeFrame = selectedDate?.let { timeFramesByDate[it] }
            val courtID = selectedTimeFrame?.courtID ?: run {
                try {
                    val courtSnapshot = db.collection("courts")
                        .whereEqualTo("coSoID", coSoID)
                        .limit(1)
                        .get()
                        .await()
                    courtSnapshot.documents.firstOrNull()?.toObject(Court::class.java)?.courtID ?: ""
                } catch (e: Exception) {
                    Log.e("ItemDetailOwnerActivity", "Error fetching default courtID for coSoID: $coSoID, ${e.message}", e)
                    ""
                }
            }
            val courtSize = courtID.takeIf { it.isNotEmpty() }?.let { getCourtSize(it) } ?: "Unknown"

            val timeSlots = if (selectedTimeFrame != null && selectedTimeFrame.period.isNotEmpty()) {
                selectedTimeFrame.period.map { period ->
                    TimeSlot(
                        price = 0.0,
                        courtSize = courtSize,
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
                }.sortedBy { it.period }
            } else {
                generateDefaultTimeSlots(courtSize, courtID)
            }

            khungGioAdapter.updateData(timeSlots)
            binding.rcvKhungGioOwner.visibility = if (timeSlots.isEmpty()) View.GONE else View.VISIBLE
            binding.emptyStateView.visibility = if (timeSlots.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupRecyclerView() {
        priceBoardAdapter = AddPriceBoardAdapter(
            this,
            timeSlots,
            onUpdateClick = { position, updatedTimeSlot ->
                lifecycleScope.launch {
                    try {
                        if (updatedTimeSlot.pricingID.isNullOrEmpty() ||
                            updatedTimeSlot.coSoID.isNullOrEmpty() ||
                            updatedTimeSlot.ownerID.isNullOrEmpty()) {
                            Log.e("ItemDetailOwnerActivity", "Invalid time slot data for update: $updatedTimeSlot")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Dữ liệu khung giờ không hợp lệ", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (updatedTimeSlot.session.isEmpty() ||
                            updatedTimeSlot.courtSize.isEmpty() ||
                            updatedTimeSlot.period.isEmpty() ||
                            updatedTimeSlot.price <= 0) {
                            Log.e("ItemDetailOwnerActivity", "Invalid time slot fields: $updatedTimeSlot")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng điền đầy đủ thông tin khung giờ", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        showLoading()
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            Log.e("ItemDetailOwnerActivity", "No authenticated user")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val currentUid = currentUser.uid

                        val facilityDoc = db.collection("sport_facilities").document(updatedTimeSlot.coSoID).get().await()
                        if (!facilityDoc.exists()) {
                            Log.w("ItemDetailOwnerActivity", "Sport facility does not exist: coSoID=${updatedTimeSlot.coSoID}")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại trên Firestore", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val facilityOwnerID = facilityDoc.getString("ownerID")
                        if (facilityOwnerID != currentUid) {
                            Log.e("ItemDetailOwnerActivity", "Owner mismatch: facilityOwnerID=$facilityOwnerID, currentUid=$currentUid")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền cập nhật", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        val batch = db.batch()
                        batch.set(
                            db.collection("timeSlots").document(updatedTimeSlot.pricingID),
                            updatedTimeSlot
                        )

                        val facilitiesSnapshot = db.collection("sport_facilities")
                            .whereEqualTo("ownerID", currentUid)
                            .get()
                            .await()

                        for (facility in facilitiesSnapshot.documents) {
                            val otherCoSoID = facility.id
                            if (otherCoSoID != updatedTimeSlot.coSoID) {
                                val timeSlotsSnapshot = db.collection("timeSlots")
                                    .whereEqualTo("coSoID", otherCoSoID)
                                    .whereEqualTo("ownerID", currentUid)
                                    .whereEqualTo("session", timeSlots[position].session)
                                    .whereEqualTo("courtSize", timeSlots[position].courtSize)
                                    .whereEqualTo("period", timeSlots[position].period)
                                    .whereEqualTo("price", timeSlots[position].price)
                                    .get()
                                    .await()

                                for (timeSlotDoc in timeSlotsSnapshot.documents) {
                                    val newTimeSlot = timeSlotDoc.toObject(TimeSlot::class.java)?.copy(
                                        session = updatedTimeSlot.session,
                                        courtSize = updatedTimeSlot.courtSize,
                                        period = updatedTimeSlot.period,
                                        price = updatedTimeSlot.price
                                    )
                                    if (newTimeSlot != null) {
                                        batch.set(
                                            db.collection("timeSlots").document(timeSlotDoc.id),
                                            newTimeSlot
                                        )
                                    }
                                }
                            }
                        }

                        withTimeoutOrNull(10000) {
                            batch.commit().await()
                        } ?: throw Exception("Timeout when updating time slots")

                        timeSlots[position] = updatedTimeSlot
                        runOnUiThread {
                            priceBoardAdapter.notifyItemChanged(position)
                            updateRecyclerViewVisibility()
                            Toast.makeText(this@ItemDetailOwnerActivity, "Cập nhật khung giờ thành công", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ItemDetailOwnerActivity", "Error updating time slot: ${e.message}", e)
                        Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi cập nhật khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        hideLoading()
                    }
                }
            },
            onDeleteClick = { position ->
                if (position < 0 || position >= timeSlots.size) {
                    Log.e("ItemDetailOwnerActivity", "Invalid position: $position")
                    Toast.makeText(this, "Lỗi: Vị trí không hợp lệ", Toast.LENGTH_SHORT).show()
                    return@AddPriceBoardAdapter
                }

                val timeSlot = timeSlots[position]
                AlertDialog.Builder(this)
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn chắc chắn muốn xóa khung giờ này?")
                    .setPositiveButton("Có") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                showLoading()
                                val currentUser = auth.currentUser
                                if (currentUser == null) {
                                    Log.e("ItemDetailOwnerActivity", "No authenticated user")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                                val currentUid = currentUser.uid

                                val facilityDoc = withTimeoutOrNull(10000) {
                                    db.collection("sport_facilities").document(timeSlot.coSoID).get().await()
                                }
                                if (facilityDoc == null) {
                                    Log.w("ItemDetailOwnerActivity", "Failed to fetch facility due to timeout, coSoID: ${timeSlot.coSoID}")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Không thể tải dữ liệu sân do timeout.", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                                if (!facilityDoc.exists()) {
                                    Log.w("ItemDetailOwnerActivity", "Sport facility does not exist: coSoID=${timeSlot.coSoID}")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại trên Firestore", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                                val facilityOwnerID = facilityDoc.getString("ownerID")
                                if (facilityOwnerID != currentUid) {
                                    Log.e("ItemDetailOwnerActivity", "Owner mismatch: facilityOwnerID=$facilityOwnerID, currentUid=$currentUid")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền xóa", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }

                                val batch = db.batch()
                                batch.delete(db.collection("timeSlots").document(timeSlot.pricingID))

                                val facilitiesSnapshot = db.collection("sport_facilities")
                                    .whereEqualTo("ownerID", currentUid)
                                    .get()
                                    .await()

                                for (facility in facilitiesSnapshot.documents) {
                                    val otherCoSoID = facility.id
                                    if (otherCoSoID != timeSlot.coSoID) {
                                        val timeSlotsSnapshot = db.collection("timeSlots")
                                            .whereEqualTo("coSoID", otherCoSoID)
                                            .whereEqualTo("ownerID", currentUid)
                                            .whereEqualTo("session", timeSlot.session)
                                            .whereEqualTo("courtSize", timeSlot.courtSize)
                                            .whereEqualTo("period", timeSlot.period)
                                            .whereEqualTo("price", timeSlot.price)
                                            .get()
                                            .await()

                                        for (timeSlotDoc in timeSlotsSnapshot.documents) {
                                            batch.delete(timeSlotDoc.reference)
                                        }
                                    }
                                }

                                withTimeoutOrNull(10000) {
                                    batch.commit().await()
                                } ?: throw Exception("Timeout when deleting time slot")

                                withContext(Dispatchers.Main) {
                                    timeSlots.removeAt(position)
                                    priceBoardAdapter.notifyItemRemoved(position)
                                    updateRecyclerViewVisibility()
                                    Toast.makeText(this@ItemDetailOwnerActivity, "Xóa khung giờ thành công", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("ItemDetailOwnerActivity", "Error deleting time slot: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi xóa khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                hideLoading()
                            }
                        }
                    }
                    .setNegativeButton("Không", null)
                    .show()
            },
            hideButtons = true
        )
        binding.priceDetailListOwner.apply {
            adapter = priceBoardAdapter
            layoutManager = LinearLayoutManager(this@ItemDetailOwnerActivity)
        }

        khungGioAdapter = KhungGioAdapter(emptyList()) { position ->
            if (!isEditing) {
                Toast.makeText(this, "Chọn khung giờ: ${khungGioAdapter.timeSlots[position].period}", Toast.LENGTH_SHORT).show()
            } else {
                if (selectedDate.isNullOrEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn ngày trước khi chỉnh sửa hoặc xóa", Toast.LENGTH_SHORT).show()
                    return@KhungGioAdapter
                }
                val timeSlot = khungGioAdapter.timeSlots.getOrNull(position)
                if (timeSlot == null) {
                    Log.e("ItemDetailOwnerActivity", "Invalid time slot at position: $position")
                    Toast.makeText(this, "Lỗi: Khung giờ không hợp lệ", Toast.LENGTH_SHORT).show()
                    return@KhungGioAdapter
                }
                showEditOrDeleteDialog(position, timeSlot)
            }
        }
        binding.rcvKhungGioOwner.apply {
            adapter = khungGioAdapter
            layoutManager = GridLayoutManager(this@ItemDetailOwnerActivity, 3)
        }
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
            .setPositiveButton("Có") { _, _ -> deleteTimeSlot(position, timeSlot) }
            .setNegativeButton("Không", null)
            .show()
    }

    private fun editTimeSlot(position: Int, oldTimeSlot: TimeSlot) {
        showTimePickerDialog { newPeriod ->
            lifecycleScope.launch {
                try {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTimePickerUpdateTime < 1000) {
                        Log.d("ItemDetailOwnerActivity", "Debouncing TimePicker update")
                        return@launch
                    }
                    lastTimePickerUpdateTime = currentTime

                    showLoading()
                    val newTimeSlot = oldTimeSlot.copy(
                        period = newPeriod,
                        session = when {
                            newPeriod.startsWith("08") || newPeriod.startsWith("09") || newPeriod.startsWith("10") || newPeriod.startsWith("11") -> "Sáng"
                            newPeriod.startsWith("12") || newPeriod.startsWith("13") || newPeriod.startsWith("14") || newPeriod.startsWith("15") || newPeriod.startsWith("16") -> "Chiều"
                            else -> "Tối"
                        }
                    )

                    val currentSlots = khungGioAdapter.timeSlots.toMutableList()
                    if (position >= 0 && position < currentSlots.size) {
                        currentSlots[position] = newTimeSlot
                        updateTimeFrameInFirestore(currentSlots, oldTimeSlot.courtID)
                        runOnUiThread {
                            khungGioAdapter.updateData(currentSlots.sortedBy { it.period })
                            Toast.makeText(this@ItemDetailOwnerActivity, "Cập nhật khung giờ thành công", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("ItemDetailOwnerActivity", "Invalid position for updating time slot: $position")
                        runOnUiThread {
                            Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi cập nhật khung giờ", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ItemDetailOwnerActivity", "Error updating time slot: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi cập nhật khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    hideLoading()
                }
            }
        }
    }

    private fun deleteTimeSlot(position: Int, timeSlot: TimeSlot) {
        lifecycleScope.launch {
            try {
                showLoading()
                val currentSlots = khungGioAdapter.timeSlots.toMutableList()
                if (position >= 0 && position < currentSlots.size) {
                    currentSlots.removeAt(position)
                    updateTimeFrameInFirestore(currentSlots, timeSlot.courtID)
                    runOnUiThread {
                        khungGioAdapter.updateData(currentSlots.sortedBy { it.period })
                        binding.rcvKhungGioOwner.visibility = if (currentSlots.isEmpty()) View.GONE else View.VISIBLE
                        binding.emptyStateView.visibility = if (currentSlots.isEmpty()) View.VISIBLE else View.GONE
                        Toast.makeText(this@ItemDetailOwnerActivity, "Xóa khung giờ thành công", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("ItemDetailOwnerActivity", "Invalid position for deleting time slot: $position")
                    runOnUiThread {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi xóa khung giờ", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error deleting time slot: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi xóa khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                hideLoading()
            }
        }
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
                    runOnUiThread {
                        Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show()
                    }
                }
            }, selectedHour, normalizedStartMinute, true).show()
        }, hour, minute, true).show()
    }

    private fun updateTimeFrameInFirestore(slots: List<TimeSlot>, courtID: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                showLoading()
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("ItemDetailOwnerActivity", "No authenticated user for Firestore update")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val currentUid = currentUser.uid

                val facilityDoc = withTimeoutOrNull(10000) {
                    db.collection("sport_facilities").document(coSoID).get().await()
                }
                if (facilityDoc == null || !facilityDoc.exists()) {
                    Log.w("ItemDetailOwnerActivity", "Sport facility does not exist or timeout: coSoID=$coSoID")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại trên Firestore", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val facilityOwnerID = facilityDoc.getString("ownerID")
                if (facilityOwnerID != currentUid) {
                    Log.e("ItemDetailOwnerActivity", "Owner mismatch: facilityOwnerID=$facilityOwnerID, currentUid=$currentUid")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền cập nhật khung giờ", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                if (selectedDate.isNullOrEmpty()) {
                    Log.e("ItemDetailOwnerActivity", "No selected date for updating time frame")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng chọn ngày", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val timeFrameQuery = db.collection("time_frames")
                    .whereEqualTo("coSoID", coSoID)
                    .whereEqualTo("courtID", courtID)
                    .whereEqualTo("date", selectedDate)
                    .get()
                    .await()

                val batch = db.batch()
                if (timeFrameQuery.documents.isNotEmpty()) {
                    val timeFrameDoc = timeFrameQuery.documents.first()
                    val periods = slots.map { it.period }
                    batch.update(timeFrameDoc.reference, "period", periods)
                    Log.d("ItemDetailOwnerActivity", "Updating existing time frame: timeFrameID=${timeFrameDoc.id}, periods=$periods")
                } else {
                    val newTimeFrame = TimeFrame(
                        timeFrameID = UUID.randomUUID().toString(),
                        courtID = courtID,
                        coSoID = coSoID,
                        date = selectedDate!!,
                        period = slots.map { it.period }
                    )
                    batch.set(db.collection("time_frames").document(newTimeFrame.timeFrameID), newTimeFrame)
                    Log.d("ItemDetailOwnerActivity", "Creating new time frame: timeFrameID=${newTimeFrame.timeFrameID}, periods=${newTimeFrame.period}")
                }

                withTimeoutOrNull(10000) {
                    batch.commit().await()
                } ?: throw Exception("Timeout when updating time frame")

                withContext(Dispatchers.Main) {
                    loadTimeFrames()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error updating time frame in Firestore: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi lưu khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                hideLoading()
            }
        }
    }

    private fun setupListeners() {
        binding.btnBackDetailOwner.setOnClickListener { finish() }

        binding.btnXacnhanDetailOwner.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        binding.btnUpdateSan.setOnClickListener {
            isEditing = true
            isUpdateSanPressed = true
            binding.inputLayoutTitle.visibility = View.VISIBLE
            binding.inputLayoutDiaChi.visibility = View.VISIBLE
            binding.inputLayoutSdt.visibility = View.VISIBLE
            binding.inputLayoutEmail.visibility = View.VISIBLE
            binding.inputLayoutGio.visibility = View.VISIBLE
            binding.btnSaveSan.visibility = View.VISIBLE
            binding.btnUpdateSan.visibility = View.GONE
            binding.btnDeleteSan.visibility = View.GONE
            binding.btnXacnhanDetailOwner.visibility = View.GONE

            binding.edtTitleDetailOwner.setText(binding.txtTitleDetailOwner.text)
            binding.edtDiaChiDetailOwner.setText(binding.txtDiaChiDetailOwner.text)
            binding.edtSdtDetailOwner.setText(binding.txtSdtDetailOwner.text)
            binding.edtEmailDetailOwner.setText(binding.txtEmailDetailOwner.text)
            binding.edtGioDetailOwner.setText(binding.txtGioItemDetail.text)
        }

        binding.btnSaveSan.setOnClickListener {
            saveUpdatedFacility()
            isUpdateSanPressed = false
        }

        binding.btnDeleteSan.setOnClickListener {
            confirmDeleteFacility()
        }

        binding.btnCamera.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun migrateFirestoreFields() {
        lifecycleScope.launch {
            try {
                showLoading()
                val snapshot = db.collection("sport_facilities").get().await()
                val batch = db.batch()

                snapshot.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val updatedData = data.toMutableMap()

                    if (data.containsKey("buoi")) {
                        updatedData["Buoi"] = data["buoi"]
                        updatedData.remove("buoi")
                    }
                    if (data.containsKey("hour")) {
                        updatedData["Hour"] = data["hour"]
                        updatedData.remove("hour")
                    }

                    if (updatedData != data) {
                        batch.set(doc.reference, updatedData)
                    }
                }

                batch.commit().await()
                Log.d("ItemDetailOwnerActivity", "Successfully updated Firestore documents")
                Toast.makeText(this@ItemDetailOwnerActivity, "Đã cập nhật dữ liệu Firestore", Toast.LENGTH_SHORT).show()

                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("firestore_migration_completed", true).apply()
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error updating Firestore documents: ${e.message}", e)
                Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi cập nhật dữ liệu Firestore", Toast.LENGTH_LONG).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun handleImageUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                showLoading()
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                handleImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error processing image from URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                hideLoading()
            }
        }
    }

    private fun handleImageBitmap(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                showLoading()
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                saveImageToFirestore(base64Image)

                withContext(Dispatchers.Main) {
                    binding.imgDetailOwner.setImageBitmap(bitmap)
                    Toast.makeText(this@ItemDetailOwnerActivity, "Cập nhật ảnh thành công", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error processing image bitmap: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                hideLoading()
            }
        }
    }

    private fun saveImageToFirestore(base64Image: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                showLoading()
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("ItemDetailOwnerActivity", "No authenticated user")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val currentUid = currentUser.uid

                val facilityDoc = db.collection("sport_facilities").document(coSoID).get().await()
                if (!facilityDoc.exists()) {
                    Log.w("ItemDetailOwnerActivity", "Sport facility does not exist: coSoID=$coSoID")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại trên Firestore", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val facilityOwnerID = facilityDoc.getString("ownerID")
                if (facilityOwnerID != currentUid) {
                    Log.e("ItemDetailOwnerActivity", "Owner mismatch: facilityOwnerID=$facilityOwnerID, currentUid=$currentUid")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền cập nhật ảnh", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                db.collection("sport_facilities").document(coSoID)
                    .update("images", listOf(base64Image))
                    .await()

                Log.d("ItemDetailOwnerActivity", "Updated image for coSoID: $coSoID")
                withContext(Dispatchers.Main) {
                    priceBoardAdapter.notifyDataSetChanged()
                    updateRecyclerViewVisibility()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error saving image to Firestore: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi lưu ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                hideLoading()
            }
        }
    }

    private fun listenToFirestoreChanges() {
        lifecycleScope.launch {
            var retryCount = 0
            val maxRetries = 3
            while (retryCount < maxRetries) {
                try {
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        Log.e("ItemDetailOwnerActivity", "No authenticated user for Firestore listener")
                        runOnUiThread {
                            Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại để tải khung giờ", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    if (coSoID.isEmpty()) {
                        Log.e("ItemDetailOwnerActivity", "Invalid coSoID: $coSoID")
                        runOnUiThread {
                            Toast.makeText(this@ItemDetailOwnerActivity, "Dữ liệu sân không hợp lệ", Toast.LENGTH_LONG).show()
                        }
                        finish()
                        return@launch
                    }

                    db.collection("timeSlots")
                        .whereEqualTo("ownerID", ownerID)
                        .addSnapshotListener { snapshot, error ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastSnapshotUpdateTime < 100) {
                                Log.d("ItemDetailOwnerActivity", "Skipping rapid snapshot update")
                                return@addSnapshotListener
                            }
                            lastSnapshotUpdateTime = currentTime

                            if (error != null) {
                                Log.e("ItemDetailOwnerActivity", "Error listening to Firestore: ${error.message}", error)
                                runOnUiThread {
                                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải dữ liệu khung giờ: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                                lifecycleScope.launch {
                                    delay(2000)
                                    reloadTimeSlots()
                                }
                                return@addSnapshotListener
                            }

                            if (snapshot == null) {
                                Log.w("ItemDetailOwnerActivity", "Snapshot is null for ownerID: $ownerID")
                                runOnUiThread {
                                    Toast.makeText(this@ItemDetailOwnerActivity, "Không thể tải dữ liệu khung giờ", Toast.LENGTH_SHORT).show()
                                }
                                lifecycleScope.launch {
                                    delay(2000)
                                    reloadTimeSlots()
                                }
                                return@addSnapshotListener
                            }

                            timeSlots.clear()
                            val seenPricingIds = mutableSetOf<String>()
                            if (snapshot.isEmpty) {
                                Log.d("ItemDetailOwnerActivity", "No time slots found for ownerID: $ownerID")
                                runOnUiThread {
                                    if (!hasShownEmptyToast) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Sân không có sẵn Bảng giá giờ", Toast.LENGTH_LONG).show()
                                        hasShownEmptyToast = true
                                    }
                                }
                            } else {
                                hasShownEmptyToast = false
                                for (doc in snapshot.documents) {
                                    val timeSlot = doc.toObject(TimeSlot::class.java)?.copy(pricingID = doc.id)
                                    if (timeSlot != null && isValidTimeSlot(timeSlot)) {
                                        if (timeSlot.pricingID !in seenPricingIds && (timeSlot.coSoID.isEmpty() || timeSlot.coSoID == coSoID)) {
                                            timeSlots.add(timeSlot)
                                            seenPricingIds.add(timeSlot.pricingID)
                                        } else {
                                            Log.w("ItemDetailOwnerActivity", "Skipped time slot: pricingID=${timeSlot.pricingID}, coSoID=${timeSlot.coSoID}, data=${doc.data}")
                                        }
                                    } else {
                                        Log.w("ItemDetailOwnerActivity", "Invalid time slot data: ${doc.id}, data=${doc.data}")
                                    }
                                }
                                timeSlots.sortBy { it.period }
                                Log.d("ItemDetailOwnerActivity", "Loaded ${timeSlots.size} unique time slots for ownerID: $ownerID, coSoID: $coSoID")
                            }

                            runOnUiThread {
                                priceBoardAdapter.notifyDataSetChanged()
                                updateRecyclerViewVisibility()
                            }
                        }
                    return@launch
                } catch (e: Exception) {
                    Log.e("ItemDetailOwnerActivity", "Error setting up snapshot listener: ${e.message}", e)
                    retryCount++
                    if (retryCount < maxRetries) {
                        delay(2000)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@ItemDetailOwnerActivity, "Không thể tải khung giờ sau $maxRetries lần thử", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun reloadTimeSlots() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                showLoading()
                val snapshot = db.collection("timeSlots")
                    .whereEqualTo("ownerID", ownerID)
                    .get()
                    .await()

                withContext(Dispatchers.Main) {
                    timeSlots.clear()
                    val seenPricingIds = mutableSetOf<String>()
                    if (snapshot.isEmpty) {
                        Log.d("ItemDetailOwnerActivity", "No time slots found for ownerID: $ownerID on reload")
                        if (!hasShownEmptyToast) {
                            Toast.makeText(this@ItemDetailOwnerActivity, "Sân không có sẵn Bảng giá giờ", Toast.LENGTH_LONG).show()
                            hasShownEmptyToast = true
                        }
                    } else {
                        hasShownEmptyToast = false
                        for (doc in snapshot.documents) {
                            val timeSlot = doc.toObject(TimeSlot::class.java)?.copy(pricingID = doc.id)
                            if (timeSlot != null && isValidTimeSlot(timeSlot)) {
                                if (timeSlot.pricingID !in seenPricingIds && (timeSlot.coSoID.isEmpty() || timeSlot.coSoID == coSoID)) {
                                    timeSlots.add(timeSlot)
                                    seenPricingIds.add(timeSlot.pricingID)
                                } else {
                                    Log.w("ItemDetailOwnerActivity", "Skipped time slot on reload: pricingID=${timeSlot.pricingID}, coSoID=${timeSlot.coSoID}, data=${doc.data}")
                                }
                            } else {
                                Log.w("ItemDetailOwnerActivity", "Invalid time slot data on reload: ${doc.id}, data=${doc.data}")
                            }
                        }
                        timeSlots.sortBy { it.period }
                        Log.d("ItemDetailOwnerActivity", "Reloaded ${timeSlots.size} unique time slots for ownerID: $ownerID, coSoID: $coSoID")
                    }
                    priceBoardAdapter.notifyDataSetChanged()
                    updateRecyclerViewVisibility()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error reloading time slots: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải lại khung giờ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                hideLoading()
            }
        }
    }

    private fun isValidTimeSlot(timeSlot: TimeSlot): Boolean {
        return timeSlot.pricingID.isNotEmpty() &&
                timeSlot.ownerID.isNotEmpty() &&
                timeSlot.session.isNotEmpty() &&
                timeSlot.courtSize.isNotEmpty() &&
                timeSlot.period.isNotEmpty() &&
                timeSlot.price > 0
    }

    private fun loadFacilityDetails() {
        lifecycleScope.launch {
            try {
                showLoading()
                val facilityDoc = db.collection("sport_facilities").document(coSoID).get().await()
                val facility = facilityDoc.toObject(SportFacility::class.java)
                facility?.let {
                    binding.txtTitleDetailOwner.text = it.name
                    binding.txtDiaChiDetailOwner.text = it.diaChi
                    binding.txtSdtDetailOwner.text = it.phoneContact
                    binding.txtEmailDetailOwner.text = it.email
                    binding.txtGioItemDetail.text = it.openingHours ?: "Chưa cài đặt giờ hoạt động"

                    // Load court size for txt_vs_detail
                    var courtSize: String? = null
                    try {
                        // Step 1: Get courtID from courts collection
                        var courtID: String? = null
                        repeat(2) { attempt ->
                            withTimeoutOrNull(10000) {
                                try {
                                    val courtSnapshot = db.collection("courts")
                                        .whereEqualTo("coSoID", coSoID)
                                        .limit(1)
                                        .get()
                                        .await()
                                    val court = courtSnapshot.documents.firstOrNull()?.toObject(Court::class.java)
                                    courtID = court?.courtID
                                    courtSize = court?.size?.takeIf { it.isNotEmpty() } // Store for fallback
                                    Log.d("ItemDetailOwnerActivity", "Attempt $attempt: CourtID: $courtID, Court size: ${court?.size} for coSoID: $coSoID")
                                    if (courtID != null) return@withTimeoutOrNull
                                } catch (e: Exception) {
                                    Log.w("ItemDetailOwnerActivity", "Attempt $attempt: courts query failed: ${e.message}")
                                }
                            }
                            if (courtID != null) return@repeat
                        }

                        // Step 2: Try time_slots collection with coSoID and courtID
                        if (courtID != null) {
                            repeat(2) { attempt ->
                                withTimeoutOrNull(10000) {
                                    try {
                                        val timeSlotSnapshot = db.collection("time_slots")
                                            .whereEqualTo("coSoID", coSoID)
                                            .whereEqualTo("courtID", courtID)
                                            .limit(1)
                                            .get()
                                            .await()
                                        val timeSlot = timeSlotSnapshot.documents.firstOrNull()?.toObject(TimeSlot::class.java)
                                        courtSize = timeSlot?.courtSize?.takeIf { it.isNotEmpty() }
                                        Log.d("ItemDetailOwnerActivity", "Attempt $attempt: TimeSlot courtSize: ${timeSlot?.courtSize} for coSoID: $coSoID, courtID: $courtID")
                                        if (courtSize != null) return@withTimeoutOrNull
                                    } catch (e: Exception) {
                                        Log.w("ItemDetailOwnerActivity", "Attempt $attempt: time_slots query failed: ${e.message}")
                                    }
                                }
                                if (courtSize != null) return@repeat
                            }
                        }

                        // Step 3: Try time_frames/{timeFrameID}/time_slots subcollection
                        if (courtSize == null && courtID != null) {
                            repeat(2) { attempt ->
                                withTimeoutOrNull(10000) {
                                    try {
                                        val timeFrameSnapshot = db.collection("time_frames")
                                            .whereEqualTo("coSoID", coSoID)
                                            .limit(1)
                                            .get()
                                            .await()
                                        val timeFrameID = timeFrameSnapshot.documents.firstOrNull()?.id
                                        Log.d("ItemDetailOwnerActivity", "Attempt $attempt: TimeFrame ID: $timeFrameID for coSoID: $coSoID")
                                        if (timeFrameID != null) {
                                            val subSnapshot = db.collection("time_frames")
                                                .document(timeFrameID)
                                                .collection("time_slots")
                                                .whereEqualTo("courtID", courtID)
                                                .limit(1)
                                                .get()
                                                .await()
                                            val timeSlot = subSnapshot.documents.firstOrNull()?.toObject(TimeSlot::class.java)
                                            courtSize = timeSlot?.courtSize?.takeIf { it.isNotEmpty() }
                                            Log.d("ItemDetailOwnerActivity", "Attempt $attempt: Subcollection TimeSlot courtSize: ${timeSlot?.courtSize} for courtID: $courtID")
                                            if (courtSize != null) return@withTimeoutOrNull
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ItemDetailOwnerActivity", "Attempt $attempt: time_frames/time_slots query failed: ${e.message}")
                                    }
                                }
                                if (courtSize != null) return@repeat
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ItemDetailOwnerActivity", "Error fetching court size for coSoID: $coSoID, ${e.message}", e)
                    }

                    binding.txtVsDetail.text = courtSize ?: "..vs.."

                    if (it.images.isNotEmpty() && binding.imgDetailOwner.drawable == null) {
                        if (it.images[0].startsWith("http")) {
                            Glide.with(binding.imgDetailOwner.context)
                                .load(it.images[0])
                                .placeholder(R.drawable.sanbong)
                                .into(binding.imgDetailOwner)
                        } else {
                            val decodedBytes = Base64.decode(it.images[0], Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            binding.imgDetailOwner.setImageBitmap(bitmap)
                        }
                    }
                } ?: run {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Không tìm thấy dữ liệu sân", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error loading facility details for coSoID: $coSoID, ${e.message}", e)
                Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải dữ liệu sân: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                hideLoading()
            }
        }
    }

    private fun updateRecyclerViewVisibility() {
        runOnUiThread {
            if (timeSlots.isEmpty()) {
                binding.priceDetailListOwner.visibility = View.GONE
                binding.emptyStateView.visibility = View.VISIBLE
            } else {
                binding.priceDetailListOwner.visibility = View.VISIBLE
                binding.emptyStateView.visibility = View.GONE
            }
        }
    }

    private fun saveUpdatedFacility() {
        val updatedName = binding.edtTitleDetailOwner.text.toString().trim()
        val updatedDiaChi = binding.edtDiaChiDetailOwner.text.toString().trim()
        val updatedSdt = binding.edtSdtDetailOwner.text.toString().trim()
        val updatedEmail = binding.edtEmailDetailOwner.text.toString().trim()
        val updatedGio = binding.edtGioDetailOwner.text.toString().trim()

        if (updatedName.isEmpty() || updatedDiaChi.isEmpty() || updatedSdt.isEmpty() || updatedEmail.isEmpty() || updatedGio.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        if (!updatedGio.matches(Regex("^\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}$"))) {
            Toast.makeText(this, "Giờ hoạt động phải có định dạng 'HH:mm - HH:mm'", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                showLoading()
                val facilitiesSnapshot = db.collection("sport_facilities")
                    .whereEqualTo("ownerID", ownerID)
                    .get()
                    .await()

                val batch: WriteBatch = db.batch()
                facilitiesSnapshot.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "openingHours" to updatedGio
                        )
                    )
                }

                batch.update(
                    db.collection("sport_facilities").document(coSoID),
                    mapOf(
                        "name" to updatedName,
                        "diaChi" to updatedDiaChi,
                        "phoneContact" to updatedSdt,
                        "email" to updatedEmail,
                        "openingHours" to updatedGio
                    )
                )

                batch.commit().await()

                binding.txtTitleDetailOwner.text = updatedName
                binding.txtDiaChiDetailOwner.text = updatedDiaChi
                binding.txtSdtDetailOwner.text = updatedSdt
                binding.txtEmailDetailOwner.text = updatedEmail
                binding.txtGioItemDetail.text = updatedGio

                isEditing = false
                binding.inputLayoutTitle.visibility = View.GONE
                binding.inputLayoutDiaChi.visibility = View.GONE
                binding.inputLayoutSdt.visibility = View.GONE
                binding.inputLayoutEmail.visibility = View.GONE
                binding.inputLayoutGio.visibility = View.GONE
                binding.btnSaveSan.visibility = View.GONE
                binding.btnUpdateSan.visibility = View.VISIBLE
                binding.btnDeleteSan.visibility = View.VISIBLE
                binding.btnXacnhanDetailOwner.visibility = View.VISIBLE

                Toast.makeText(this@ItemDetailOwnerActivity, "Cập nhật thành công", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error updating facility: ${e.message}", e)
                Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun confirmDeleteFacility() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn chắc chắn muốn xóa sân này? Hành động này không thể hoàn tác.")
            .setPositiveButton("Có") { _, _ ->
                deleteFacility()
            }
            .setNegativeButton("Không", null)
            .show()
    }

    private fun deleteFacility() {
        lifecycleScope.launch {
            try {
                showLoading()
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("ItemDetailOwnerActivity", "No authenticated user: coSoID=$coSoID")
                    Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val currentUid = currentUser.uid

                val facilityDoc = db.collection("sport_facilities").document(coSoID).get().await()
                if (!facilityDoc.exists()) {
                    Log.w("ItemDetailOwnerActivity", "Facility does not exist: coSoID=$coSoID")
                    Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val facilityOwnerID = facilityDoc.getString("ownerID")
                if (facilityOwnerID != currentUid) {
                    Log.e("ItemDetailOwnerActivity", "Owner mismatch: facilityOwnerID=$facilityOwnerID, currentUid=$currentUid")
                    Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền xóa", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val batch: WriteBatch = db.batch()

                val courtsSnapshot = db.collection("courts")
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()
                courtsSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                val timeSlotsSnapshot = db.collection("timeSlots")
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()
                timeSlotsSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                batch.delete(db.collection("sport_facilities").document(coSoID))

                batch.commit().await()

                Log.d("ItemDetailOwnerActivity", "Successfully deleted facility: coSoID=$coSoID")
                Toast.makeText(this@ItemDetailOwnerActivity, "Đã xóa sân", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error deleting facility: ${e.message}", e)
                Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi xóa sân: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            showImageSourceDialog()
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Yêu cầu quyền truy cập")
            .setMessage("Ứng dụng cần quyền truy cập camera và thư viện ảnh để chụp hoặc chọn ảnh cho sân. Vui lòng cấp quyền để tiếp tục.")
            .setPositiveButton("Thử lại") { _, _ ->
                checkAndRequestPermissions()
            }
            .setNegativeButton("Hủy") { _, _ ->
                Toast.makeText(this, "Không thể sử dụng camera hoặc thư viện ảnh do thiếu quyền.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun showImageSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Chọn nguồn ảnh")
            .setItems(arrayOf("Chụp ảnh", "Chọn từ thư viện")) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            takePictureLauncher.launch(null)
                        } else {
                            Toast.makeText(this, "Quyền camera bị từ chối.", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> {
                        val storageGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        }
                        if (storageGranted) {
                            pickImageLauncher.launch("image/*")
                        } else {
                            Toast.makeText(this, "Quyền truy cập thư viện ảnh bị từ chối.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}