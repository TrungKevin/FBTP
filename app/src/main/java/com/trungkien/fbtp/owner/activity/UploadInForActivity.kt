package com.trungkien.fbtp.owner.activity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.Adapter.KhungGioAdapter
import com.trungkien.fbtp.MainActivity
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.UploadInforBinding
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.model.TimeFrame
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class UploadInfoActivity : AppCompatActivity() {

    private lateinit var binding: UploadInforBinding
    private var selectedImageUri: Uri? = null
    private val sportTypes = listOf("Football", "Badminton", "Pickleball", "Tennis")
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var khungGioAdapter: KhungGioAdapter
    private var coSoID: String = ""
    private val timeFramesByDate = mutableMapOf<String, TimeFrame>()
    private var courtID: String = ""
    private var selectedDate: String = ""

    companion object {
        private const val TAG = "UploadInfoActivity"
        private const val UPLOAD_LIMIT = 10
    }

    private val thietLapNgayGioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val timeFrames = data?.getParcelableArrayListExtra<TimeFrame>(ThietLapNgayGio.RESULT_TIME_FRAME)
            if (!timeFrames.isNullOrEmpty()) {
                Log.d(TAG, "Received TimeFrames: $timeFrames")
                timeFramesByDate.clear()
                timeFrames.forEach { timeFrame ->
                    timeFramesByDate[timeFrame.date] = timeFrame
                }
                showToast("Đã thiết lập khung giờ cho ${timeFrames.size} ngày")
                updateTimeSlotsForSelectedDate()
            } else {
                showToast("Không nhận được khung giờ")
            }
        } else {
            showToast("Hủy thiết lập khung giờ")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UploadInforBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = auth.currentUser?.uid ?: run {
            showToast("Vui lòng đăng nhập")
            startActivity(Intent(this, AccountActivity::class.java))
            finish()
            return
        }

        // Set up ArrayAdapter for spinner
        val spinnerAdapter = ArrayAdapter(this, R.layout.dropdown_menu_item, sportTypes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinner.setAdapter(spinnerAdapter)
        binding.spinner.setText("", false)

        binding.spinner.setOnItemClickListener { _, _, position, _ ->
            val selectedSport = sportTypes[position]
            Log.d(TAG, "Selected sport: $selectedSport")
            showToast("Bạn đã chọn sân $selectedSport")
            binding.spinner.setText(selectedSport, false)
            binding.rcvSizeSan.visibility = View.VISIBLE
            when (selectedSport) {
                "Football" -> {
                    binding.rdSan5.text = "5 vs 5"
                    binding.rdSan5.visibility = View.VISIBLE
                    binding.rdSan7.text = "7 vs 7"
                    binding.rdSan7.visibility = View.VISIBLE
                    binding.rdSan11.text = "11 vs 11"
                    binding.rdSan11.visibility = View.VISIBLE
                }
                "Badminton" -> {
                    binding.rdSan5.text = "Sân Đơn"
                    binding.rdSan5.visibility = View.VISIBLE
                    binding.rdSan7.text = "Sân Đôi"
                    binding.rdSan7.visibility = View.VISIBLE
                    binding.rdSan11.visibility = View.GONE
                }
                "Pickleball" -> {
                    binding.rdSan5.text = "Sân Trong Nhà"
                    binding.rdSan5.visibility = View.VISIBLE
                    binding.rdSan7.text = "Sân Ngoài Trời"
                    binding.rdSan7.visibility = View.VISIBLE
                    binding.rdSan11.visibility = View.GONE
                }
                "Tennis" -> {
                    binding.rdSan5.text = "Sân Cỏ"
                    binding.rdSan5.visibility = View.VISIBLE
                    binding.rdSan7.text = "Sân Thảm"
                    binding.rdSan7.visibility = View.VISIBLE
                    binding.rdSan11.text = "Sân Đất Nện"
                    binding.rdSan11.visibility = View.VISIBLE
                }
            }
            binding.radioGroupSizeSan.clearCheck()
        }

        binding.spinner.setOnTouchListener { _, _ ->
            binding.spinner.showDropDown()
            false
        }

        val radioGroup = binding.radioGroupSizeSan
        var lastCheckedId: Int = View.NO_ID
        listOf(binding.rdSan5, binding.rdSan7, binding.rdSan11).forEach { radioButton ->
            radioButton.setOnClickListener {
                if (radioButton.id == lastCheckedId) {
                    radioGroup.clearCheck()
                    lastCheckedId = View.NO_ID
                } else {
                    lastCheckedId = radioButton.id
                }
            }
        }

        // Initialize KhungGioAdapter for rcv_khungGio_upLoad
        khungGioAdapter = KhungGioAdapter(emptyList()) {  position, _, _ ->}
        binding.rcvKhungGioUpLoad.apply {
            adapter = khungGioAdapter
            layoutManager = GridLayoutManager(this@UploadInfoActivity, 3)
        }

        setupCalendar()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnUpload.setOnClickListener { checkStoragePermissionAndOpenPicker() }
        binding.btnSubmit.setOnClickListener { validateAndUpload(userId) }
        binding.btnLapNgayGio.setOnClickListener {
            val loaiSan = binding.spinner.text.toString().trim()
            if (loaiSan.isEmpty()) {
                showToast("Vui lòng chọn loại sân trước khi thiết lập ngày giờ")
                return@setOnClickListener
            }

            val courtSize = when {
                binding.rdSan5.isChecked -> binding.rdSan5.text.toString()
                binding.rdSan7.isChecked -> binding.rdSan7.text.toString()
                binding.rdSan11.isChecked -> binding.rdSan11.text.toString()
                else -> ""
            }

            if (courtSize.isEmpty()) {
                showToast("Vui lòng chọn kích thước sân trước khi thiết lập ngày giờ")
                return@setOnClickListener
            }

            coSoID = db.collection("sport_facilities").document().id
            courtID = db.collection("courts").document().id

            val intent = Intent(this@UploadInfoActivity, ThietLapNgayGio::class.java).apply {
                putExtra("coSoID", coSoID)
                putExtra("courtType", loaiSan)
                putExtra("courtSize", courtSize)
                putExtra("courtID", courtID)
            }
            thietLapNgayGioLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateTimeSlotsForSelectedDate()
    }

    private fun setupCalendar() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.rcvCalendaUpLoad.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val calendarItems = mutableListOf<CalendarItem>()
        for (i in 0 until 14) {
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val dayOfWeek = when (date.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "T2"
                Calendar.TUESDAY -> "T3"
                Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"
                Calendar.FRIDAY -> "T6"
                Calendar.SATURDAY -> "T7"
                Calendar.SUNDAY -> "CN"
                else -> ""
            }
            val day = date.get(Calendar.DAY_OF_MONTH).toString()
            val month = "Tháng ${date.get(Calendar.MONTH) + 1}"
            val dateStr = dateFormat.format(date.time)

            calendarItems.add(CalendarItem(dayOfWeek, day, month, dateStr))
        }

        selectedDate = calendarItems.firstOrNull()?.date ?: ""

        val calendarAdapter = CalendarAdapter(calendarItems) { date ->
            selectedDate = date
            updateTimeSlotsForSelectedDate()
        }
        binding.rcvCalendaUpLoad.adapter = calendarAdapter
        updateTimeSlotsForSelectedDate()
    }

    private fun updateTimeSlotsForSelectedDate() {
        val timeFrame = timeFramesByDate[selectedDate]
        val timeSlots = if (timeFrame != null) {
            timeFrame.period.map { period ->
                TimeSlot(
                    price = 0.0,
                    courtSize = binding.spinner.text.toString().trim(),
                    period = period,
                    session = when {
                        period.startsWith("08") || period.startsWith("09") || period.startsWith("10") || period.startsWith("11") -> "Sáng"
                        period.startsWith("12") || period.startsWith("13") || period.startsWith("14") || period.startsWith("15") || period.startsWith("16") -> "Chiều"
                        else -> "Tối"
                    },
                    isTimeRange = true,
                    courtID = timeFrame.courtID,
                    coSoID = timeFrame.coSoID
                )
            }.sortedBy { it.period }
        } else {
            emptyList()
        }

        khungGioAdapter.updateData(timeSlots)
        if (timeSlots.isEmpty()) {
            showToast("Chưa thiết lập khung giờ cho ngày $selectedDate")
        }
    }

    private fun checkStoragePermissionAndOpenPicker() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            showToast("Cần quyền truy cập ảnh để chọn hình")
        }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedImageUri = uri
                Glide.with(this)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(binding.imgUpload)
                binding.tvFileName.text = "Đã chọn: ${uri.lastPathSegment?.substringAfterLast('/') ?: "Không rõ"}"
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing image: ${e.message}", e)
                showToast("Không thể truy cập ảnh")
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        imagePickerLauncher.launch(intent)
    }

    private fun validateAndUpload(userId: String) {
        val tenSan = binding.edtTensanNhap.text.toString().trim()
        val diaChi = binding.edtDiaChiNhap.text.toString().trim()
        val sdt = binding.edtSdtNhap.text.toString().trim()
        val email = binding.edtEmailNhap.text.toString().trim()
        val loaiSan = binding.spinner.text.toString().trim()
        val courtSize = when {
            binding.rdSan5.isChecked -> binding.rdSan5.text.toString()
            binding.rdSan7.isChecked -> binding.rdSan7.text.toString()
            binding.rdSan11.isChecked -> binding.rdSan11.text.toString()
            else -> ""
        }

        when {
            tenSan.isEmpty() -> {
                binding.edtTensanNhap.error = "Vui lòng nhập tên sân"
                return
            }
            diaChi.isEmpty() -> {
                binding.edtDiaChiNhap.error = "Vui lòng nhập địa chỉ"
                return
            }
            sdt.isEmpty() || !sdt.matches(Regex("^[0-9]{10,11}$")) -> {
                binding.edtSdtNhap.error = "Số điện thoại không hợp lệ"
                return
            }
            email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.edtEmailNhap.error = "Email không hợp lệ"
                return
            }
            loaiSan.isEmpty() -> {
                showToast("Vui lòng chọn loại sân")
                return
            }
            courtSize.isEmpty() -> {
                showToast("Vui lòng chọn kích thước sân")
                return
            }
            selectedImageUri == null -> {
                showToast("Vui lòng chọn hình ảnh")
                return
            }
            timeFramesByDate.isEmpty() -> {
                showToast("Vui lòng thiết lập khung giờ trước khi tải lên")
                return
            }
        }

        lifecycleScope.launch {
            try {
                if (!checkUploadLimit(userId)) {
                    showToast("Bạn đã đạt giới hạn số lượng sân ($UPLOAD_LIMIT)!")
                    return@launch
                }
                uploadData(userId, tenSan, diaChi, sdt, email, loaiSan, courtSize, selectedImageUri!!, timeFramesByDate.values.toList())
            } catch (e: Exception) {
                Log.e(TAG, "Error checking upload limit: ${e.message}", e)
                showToast("Lỗi kiểm tra giới hạn: ${e.message}")
            }
        }
    }

    private suspend fun checkUploadLimit(userId: String): Boolean {
        val snapshot = withContext(Dispatchers.IO) {
            db.collection("sport_facilities")
                .whereEqualTo("ownerID", userId)
                .get()
                .await()
        }
        Log.d(TAG, "Current facility count for user $userId: ${snapshot.size()}")
        return snapshot.size() < UPLOAD_LIMIT
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnected == true
    }

    private suspend fun uploadData(
        userId: String,
        tenSan: String,
        diaChi: String,
        sdt: String,
        email: String,
        loaiSan: String,
        courtSize: String,
        imageUri: Uri,
        timeFrames: List<TimeFrame>
    ) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        try {
            if (!isNetworkAvailable()) throw Exception("Không có kết nối mạng")

            val mimeType = contentResolver.getType(imageUri)
            Log.d(TAG, "Image MIME type: $mimeType")
            if (mimeType !in listOf("image/jpeg", "image/png")) throw Exception("Ảnh không hợp lệ")

            val bitmap = withContext(Dispatchers.IO) {
                Glide.with(this@UploadInfoActivity)
                    .asBitmap()
                    .load(imageUri)
                    .override(400, 200)
                    .submit()
                    .get()
            }
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream)
            val imageBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            Log.d(TAG, "Base64 image size: ${imageBase64.length} characters")

            val allPeriods = timeFrames.flatMap { it.period }
            val openingHours = if (allPeriods.isNotEmpty()) {
                val firstPeriod = allPeriods.minByOrNull { it.split("-")[0] } ?: "05:00-24:00"
                val lastPeriod = allPeriods.maxByOrNull { it.split("-")[1] } ?: "05:00-24:00"
                "${firstPeriod.split("-")[0]}-${lastPeriod.split("-")[1]}"
            } else {
                "05:00-24:00"
            }

            val sportFacility = SportFacility(
                coSoID = coSoID,
                name = tenSan,
                diaChi = diaChi,
                phoneContact = sdt,
                email = email,
                ownerID = userId,
                images = listOf(imageBase64),
                description = "",
                pricePerHour = 0.0,
                Hour = "",
                Buoi = emptyList(),
                openingHours = openingHours
            )

            val court = Court(
                courtID = courtID,
                coSoID = coSoID,
                ownerID = userId,
                timeFrameID = timeFrames.firstOrNull()?.timeFrameID ?: "",
                courtName = tenSan,
                sportType = loaiSan,
                status = "available",
                size = courtSize, // Use selected courtSize from radioGroupSizeSan
                period = "",
                pricePerHour = 0.0,
                session = ""
            )

            withTimeout(15000) {
                withContext(Dispatchers.IO) {
                    db.collection("sport_facilities").document(coSoID).set(sportFacility).await()
                }
            }
            Log.d(TAG, "Successfully uploaded sport_facility")

            withTimeout(15000) {
                withContext(Dispatchers.IO) {
                    db.collection("courts").document(courtID).set(court).await()
                }
            }
            Log.d(TAG, "Successfully uploaded court")

            timeFrames.forEach { timeFrame ->
                withTimeout(15000) {
                    withContext(Dispatchers.IO) {
                        db.collection("time_frames").document(timeFrame.timeFrameID).set(timeFrame).await()
                    }
                }
                Log.d(TAG, "Successfully uploaded time_frame: ${timeFrame.timeFrameID}")
            }

            val resultIntent = Intent().apply {
                putExtra("coSoID", coSoID)
                putExtra("imageBase64", imageBase64)
                putExtra("refresh", true)
            }
            setResult(RESULT_OK, resultIntent)

            val intent = Intent(this@UploadInfoActivity, MainActivity::class.java).apply {
                putExtra("coSoID", coSoID)
                putExtra("imageBase64", imageBase64)
                putExtra("refresh", true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)

            showToast("Tải lên thành công!")
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            val errorMessage = when {
                e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền tải lên (kiểm tra ownerID: $userId)"
                e.message?.contains("NOT_FOUND") == true -> "Dữ liệu không tồn tại"
                e.message?.contains("TIMEOUT") == true -> "Hết thời gian tải lên, vui lòng thử lại"
                else -> "Lỗi upload: ${e.message}"
            }
            showToast(errorMessage)
        } finally {
            binding.progressBar.visibility = View.GONE
            binding.btnSubmit.isEnabled = true
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

data class CalendarItem(
    val dayOfWeek: String,
    val day: String,
    val month: String,
    val date: String
)

class CalendarAdapter(
    private val items: List<CalendarItem>,
    private val onDateClick: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tv_day)
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvMonth: TextView = itemView.findViewById(R.id.tv_month)

        fun bind(item: CalendarItem) {
            tvDay.text = item.dayOfWeek
            tvDate.text = item.day
            tvMonth.text = item.month
            itemView.setOnClickListener {
                onDateClick(item.date)
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.colorPrimaryLight))
                (0 until itemCount).filter { it != adapterPosition }.forEach { position ->
                    val holder = (itemView.parent as androidx.recyclerview.widget.RecyclerView)
                        .findViewHolderForAdapterPosition(position) as? ViewHolder
                    holder?.itemView?.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.transparent))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}