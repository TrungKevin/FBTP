package com.trungkien.fbtp.owner.activity

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.trungkien.fbtp.AddPriceBoardAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.DetailItemOwnerBinding
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.InetAddress

class ItemDetailOwnerActivity : AppCompatActivity() {

    private lateinit var binding: DetailItemOwnerBinding
    private lateinit var priceBoardAdapter: AddPriceBoardAdapter
    private val timeSlots = mutableListOf<TimeSlot>()
    private var coSoID: String = ""
    private var isEditing = false
    private var isUpdateSanPressed = false // Biến theo dõi trạng thái btn_update_san
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val ownerID: String by lazy {
        auth.currentUser?.uid ?: ""
    }

    // ActivityResultLauncher để chọn ảnh từ thư viện
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageUri(it) }
    }

    // ActivityResultLauncher để chụp ảnh từ camera
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { handleImageBitmap(it) }
    }

    // ActivityResultLauncher để yêu cầu quyền
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true

        if (cameraGranted && storageGranted) {
            showImageSourceDialog()
        } else {
            // Kiểm tra xem có nên hiển thị giải thích
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

        // Check if Firestore migration is needed
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val migrationCompleted = prefs.getBoolean("firestore_migration_completed", false)
        if (!migrationCompleted) {
            migrateFirestoreFields()
        }

        // Log current UID for debugging
        Log.d("ItemDetailOwnerActivity", "Current UID: ${auth.currentUser?.uid}")

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
        loadFacilityDetails()
        listenToFirestoreChanges()
    }

    private fun migrateFirestoreFields() {
        lifecycleScope.launch {
            try {
                // Get all documents in the sport_facilities collection
                val snapshot = db.collection("sport_facilities").get().await()
                val batch = db.batch()

                snapshot.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val updatedData = data.toMutableMap()

                    // Check for lowercase buoi and hour fields
                    if (data.containsKey("buoi")) {
                        updatedData["Buoi"] = data["buoi"]
                        updatedData.remove("buoi")
                    }
                    if (data.containsKey("hour")) {
                        updatedData["Hour"] = data["hour"]
                        updatedData.remove("hour")
                    }

                    // Only update if changes were made
                    if (updatedData != data) {
                        batch.set(doc.reference, updatedData)
                    }
                }

                // Commit the batch
                batch.commit().await()
                Log.d("ItemDetailOwnerActivity", "Successfully updated Firestore documents to rename buoi to Buoi and hour to Hour")
                Toast.makeText(this@ItemDetailOwnerActivity, "Đã cập nhật dữ liệu Firestore", Toast.LENGTH_SHORT).show()

                // Save flag to indicate migration is complete
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("firestore_migration_completed", true).apply()
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error updating Firestore documents: ${e.message}", e)
                Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi cập nhật dữ liệu Firestore: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView() {
        priceBoardAdapter = AddPriceBoardAdapter(
            this,
            timeSlots,
            onUpdateClick = { position, updatedTimeSlot ->
                lifecycleScope.launch {
                    try {
                        // Kiểm tra tính hợp lệ của timeSlot
                        if (updatedTimeSlot.pricingID.isNullOrEmpty() ||
                            updatedTimeSlot.coSoID.isNullOrEmpty() ||
                            updatedTimeSlot.ownerID.isNullOrEmpty()) {
                            Log.e("ItemDetailOwnerActivity", "Invalid time slot data for update: pricingID=${updatedTimeSlot.pricingID}, coSoID=${updatedTimeSlot.coSoID}, ownerID=${updatedTimeSlot.ownerID}")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Dữ liệu khung giờ không hợp lệ: Thiếu pricingID, coSoID hoặc ownerID", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (updatedTimeSlot.session.isEmpty() ||
                            updatedTimeSlot.courtSize.isEmpty() ||
                            updatedTimeSlot.period.isEmpty() ||
                            updatedTimeSlot.price <= 0) {
                            Log.e("ItemDetailOwnerActivity", "Invalid time slot fields: session=${updatedTimeSlot.session}, courtSize=${updatedTimeSlot.courtSize}, period=${updatedTimeSlot.period}, price=${updatedTimeSlot.price}")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng điền đầy đủ thông tin khung giờ", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // Kiểm tra trạng thái xác thực
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            Log.e("ItemDetailOwnerActivity", "No authenticated user")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val currentUid = currentUser.uid
                        Log.d("ItemDetailOwnerActivity", "Current user UID for update: $currentUid")

                        // Kiểm tra quyền sở hữu thông qua sport_facilities
                        val facilityDoc = db.collection("sport_facilities").document(updatedTimeSlot.coSoID).get().await()
                        if (!facilityDoc.exists()) {
                            Log.w("ItemDetailOwnerActivity", "Sport facility does not exist: coSoID=${updatedTimeSlot.coSoID}")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại trên Firestore", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val facilityOwnerID = facilityDoc.getString("ownerID")
                        if (facilityOwnerID != currentUid) {
                            Log.e("ItemDetailOwnerActivity", "Owner mismatch for facility: facility ownerID=$facilityOwnerID, user UID=$currentUid, documentData=${facilityDoc.data}")
                            Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền cập nhật: Bạn không sở hữu sân này", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // Tạo batch để cập nhật timeSlot hiện tại và các timeSlot tương ứng trong các sân khác
                        val batch = db.batch()

                        // Cập nhật timeSlot hiện tại
                        batch.set(
                            db.collection("timeSlots").document(updatedTimeSlot.pricingID),
                            updatedTimeSlot
                        )

                        // Tìm tất cả sân của ownerID
                        val facilitiesSnapshot = db.collection("sport_facilities")
                            .whereEqualTo("ownerID", currentUid)
                            .get()
                            .await()

                        // Tìm và cập nhật timeSlot tương ứng trong các sân khác
                        for (facility in facilitiesSnapshot.documents) {
                            val otherCoSoID = facility.id
                            if (otherCoSoID != updatedTimeSlot.coSoID) { // Bỏ qua sân hiện tại
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
                                        Log.d("ItemDetailOwnerActivity", "Updating matching time slot in coSoID: $otherCoSoID, pricingID: ${timeSlotDoc.id}")
                                    }
                                }
                            }
                        }

                        // Commit batch với thời gian chờ 10 giây
                        withTimeoutOrNull(10000) {
                            batch.commit().await()
                        } ?: throw Exception("Timeout when updating time slots")

                        // Cập nhật danh sách cục bộ
                        timeSlots[position] = updatedTimeSlot
                        runOnUiThread {
                            priceBoardAdapter.notifyItemChanged(position)
                            updateRecyclerViewVisibility()
                            Toast.makeText(this@ItemDetailOwnerActivity, "Cập nhật khung giờ thành công trên tất cả sân", Toast.LENGTH_SHORT).show()
                        }
                        Log.d("ItemDetailOwnerActivity", "Updated time slot: ${updatedTimeSlot.pricingID} for coSoID: ${updatedTimeSlot.coSoID} and other facilities")
                    } catch (e: Exception) {
                        Log.e("ItemDetailOwnerActivity", "Error updating time slot: ${e.message}, timeSlot=$updatedTimeSlot", e)
                        val errorMessage = when {
                            e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền cập nhật khung giờ: Kiểm tra quyền sở hữu."
                            e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng. Vui lòng kiểm tra kết nối mạng."
                            e.message?.contains("NOT_FOUND") == true -> "Khung giờ hoặc sân không tồn tại trên Firestore."
                            e.message?.contains("Timeout") == true -> "Lỗi timeout khi cập nhật khung giờ. Vui lòng kiểm tra kết nối mạng và thử lại."
                            else -> "Lỗi khi cập nhật khung giờ: ${e.message}"
                        }
                        runOnUiThread {
                            Toast.makeText(this@ItemDetailOwnerActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }
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
                Log.d("ItemDetailOwnerActivity", "Attempting to delete time slot: pricingID=${timeSlot.pricingID}, coSoID=${timeSlot.coSoID}, ownerID=${timeSlot.ownerID}")

                AlertDialog.Builder(this)
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn chắc chắn muốn xóa khung giờ này? Khung giờ tương ứng trên các sân khác cũng sẽ bị xóa.")
                    .setPositiveButton("Có") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                // Kiểm tra trạng thái xác thực
                                val currentUser = auth.currentUser
                                if (currentUser == null) {
                                    Log.e("ItemDetailOwnerActivity", "No authenticated user")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                                val currentUid = currentUser.uid
                                Log.d("ItemDetailOwnerActivity", "Current user UID: $currentUid")

                                // Kiểm tra kết nối mạng
                                val isNetworkAvailable = isNetworkAvailable()
                                if (!isNetworkAvailable) {
                                    Log.e("ItemDetailOwnerActivity", "No network connection")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Không có kết nối mạng. Vui lòng kiểm tra kết nối và thử lại.", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }

                                // Kiểm tra tính hợp lệ của timeSlot
                                if (timeSlot.pricingID.isNullOrEmpty() ||
                                    timeSlot.coSoID.isNullOrEmpty() ||
                                    timeSlot.ownerID.isNullOrEmpty()) {
                                    Log.e("ItemDetailOwnerActivity", "Invalid time slot data: pricingID=${timeSlot.pricingID}, coSoID=${timeSlot.coSoID}, ownerID=${timeSlot.ownerID}")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Dữ liệu khung giờ không hợp lệ: Thiếu pricingID, coSoID hoặc ownerID", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }

                                // Kiểm tra quyền sở hữu thông qua sport_facilities
                                val facilityDoc = withTimeoutOrNull(10000) {
                                    db.collection("sport_facilities").document(timeSlot.coSoID).get().await()
                                }
                                if (facilityDoc == null) {
                                    Log.w("ItemDetailOwnerActivity", "Failed to fetch facility due to timeout: coSoID=${timeSlot.coSoID}")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Không thể tải dữ liệu sân do timeout. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
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
                                    Log.e("ItemDetailOwnerActivity", "Owner mismatch for facility: facility ownerID=$facilityOwnerID, user UID=$currentUid, documentData=${facilityDoc.data}")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền xóa: Bạn không sở hữu sân này", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }

                                // Thử xóa timeSlot với cơ chế retry
                                var lastError: Exception? = null
                                repeat(3) { attempt ->
                                    try {
                                        // Tạo batch để xóa timeSlot hiện tại và các timeSlot tương ứng
                                        val batch = db.batch()

                                        // Xóa timeSlot hiện tại
                                        batch.delete(db.collection("timeSlots").document(timeSlot.pricingID))

                                        // Tìm tất cả sân của ownerID
                                        val facilitiesSnapshot = db.collection("sport_facilities")
                                            .whereEqualTo("ownerID", currentUid)
                                            .get()
                                            .await()

                                        // Tìm và xóa timeSlot tương ứng trong các sân khác
                                        for (facility in facilitiesSnapshot.documents) {
                                            val otherCoSoID = facility.id
                                            if (otherCoSoID != timeSlot.coSoID) { // Bỏ qua sân hiện tại
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
                                                    Log.d("ItemDetailOwnerActivity", "Deleting matching time slot in coSoID: $otherCoSoID, pricingID: ${timeSlotDoc.id}")
                                                }
                                            }
                                        }

                                        // Commit batch với thời gian chờ 10 giây
                                        withTimeoutOrNull(10000) {
                                            batch.commit().await()
                                        } ?: throw Exception("Timeout when deleting time slot on attempt ${attempt + 1}")

                                        // Cập nhật danh sách cục bộ
                                        withContext(Dispatchers.Main) {
                                            timeSlots.removeAt(position)
                                            priceBoardAdapter.notifyItemRemoved(position)
                                            updateRecyclerViewVisibility()
                                            Toast.makeText(this@ItemDetailOwnerActivity, "Xóa khung giờ thành công trên tất cả sân", Toast.LENGTH_SHORT).show()
                                        }
                                        Log.d("ItemDetailOwnerActivity", "Deleted time slot: ${timeSlot.pricingID} for coSoID: ${timeSlot.coSoID} and other facilities")
                                        return@launch // Thành công, thoát
                                    } catch (e: Exception) {
                                        Log.w("ItemDetailOwnerActivity", "Delete attempt ${attempt + 1} failed: ${e.message}")
                                        lastError = e
                                        if (attempt < 2) delay(2000) // Chờ 2 giây trước khi thử lại
                                    }
                                }
                                // Nếu tất cả các lần thử thất bại
                                throw lastError ?: Exception("Unknown error after retries")
                            } catch (e: Exception) {
                                Log.e("ItemDetailOwnerActivity", "Error deleting time slot: ${e.message}, timeSlot=$timeSlot", e)
                                val errorMessage = when {
                                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền xóa khung giờ: Kiểm tra quyền sở hữu."
                                    e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng. Vui lòng kiểm tra kết nối mạng và thử lại."
                                    e.message?.contains("NOT_FOUND") == true -> "Khung giờ hoặc sân không tồn tại trên Firestore."
                                    e.message?.contains("Timeout") == true -> "Lỗi timeout khi xóa khung giờ. Vui lòng kiểm tra kết nối mạng và thử lại."
                                    else -> "Lỗi khi xóa khung giờ: ${e.message}"
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@ItemDetailOwnerActivity, errorMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Không", null)
                    .show()
            }
        )
        binding.priceDetailListOwner.apply {
            adapter = priceBoardAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ItemDetailOwnerActivity)
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
            isUpdateSanPressed = true // Cho phép cập nhật/xóa timeSlot sau khi nhấn btn_update_san
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
            isUpdateSanPressed = false // Đặt lại trạng thái sau khi lưu
        }

        binding.btnDeleteSan.setOnClickListener {
            confirmDeleteFacility()
        }

        // Xử lý nhấn btn_camera
        binding.btnCamera.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    // Kiểm tra và yêu cầu quyền camera/thư viện
    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Kiểm tra quyền CAMERA
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        // Kiểm tra quyền truy cập ảnh/thư viện
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: READ_MEDIA_IMAGES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 trở xuống: READ_EXTERNAL_STORAGE
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

    // Hiển thị dialog giải thích lý do cần quyền
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

    // Hiển thị dialog chọn nguồn ảnh (thư viện hoặc camera)
    private fun showImageSourceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Chọn nguồn ảnh")
            .setItems(arrayOf("Chụp ảnh", "Chọn từ thư viện")) { _, which ->
                when (which) {
                    0 -> {
                        // Kiểm tra quyền CAMERA trước khi mở
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            takePictureLauncher.launch(null)
                        } else {
                            Toast.makeText(this, "Quyền camera bị từ chối. Vui lòng cấp quyền trong cài đặt.", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> {
                        // Kiểm tra quyền truy cập ảnh trước khi mở thư viện
                        val storageGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        }
                        if (storageGranted) {
                            pickImageLauncher.launch("image/*")
                        } else {
                            Toast.makeText(this, "Quyền truy cập thư viện ảnh bị từ chối. Vui lòng cấp quyền trong cài đặt.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // Xử lý ảnh từ thư viện
    private fun handleImageUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                handleImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error processing image from URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Xử lý ảnh (Bitmap) từ camera hoặc thư viện
    private fun handleImageBitmap(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Nén và chuyển ảnh thành Base64
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                // Lưu ảnh vào Firestore
                saveImageToFirestore(base64Image)

                // Cập nhật giao diện
                withContext(Dispatchers.Main) {
                    binding.imgDetailOwner.setImageBitmap(bitmap)
                    Toast.makeText(this@ItemDetailOwnerActivity, "Cập nhật ảnh thành công", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error processing image bitmap: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Lưu ảnh Base64 vào Firestore
    private fun saveImageToFirestore(base64Image: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Kiểm tra xác thực
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("ItemDetailOwnerActivity", "No authenticated user")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val currentUid = currentUser.uid

                // Kiểm tra quyền sở hữu
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
                    Log.e("ItemDetailOwnerActivity", "Owner mismatch: facility ownerID=$facilityOwnerID, user UID=$currentUid")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền cập nhật ảnh: Bạn không sở hữu sân này", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // Cập nhật images[0] trong Firestore
                db.collection("sport_facilities").document(coSoID)
                    .update("images", listOf(base64Image))
                    .await()

                Log.d("ItemDetailOwnerActivity", "Updated image for coSoID: $coSoID")
                withContext(Dispatchers.Main) {
                    // Làm mới RecyclerView để đảm bảo hiển thị item_price_board
                    priceBoardAdapter.notifyDataSetChanged()
                    updateRecyclerViewVisibility()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error saving image to Firestore: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = when {
                        e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền cập nhật ảnh"
                        e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                        else -> "Lỗi khi lưu ảnh: ${e.message}"
                    }
                    Toast.makeText(this@ItemDetailOwnerActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun listenToFirestoreChanges() {
        lifecycleScope.launch {
            var retryCount = 0
            val maxRetries = 3
            while (retryCount < maxRetries) {
                try {
                    // Kiểm tra xác thực trước khi truy vấn
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        Log.e("ItemDetailOwnerActivity", "No authenticated user for Firestore listener")
                        runOnUiThread {
                            Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại để tải khung giờ", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    // Kiểm tra coSoID hợp lệ
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
                        .whereEqualTo("coSoID", coSoID)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e("ItemDetailOwnerActivity", "Error listening to Firestore: ${error.message}", error)
                                val errorMessage = when {
                                    error.message?.contains("PERMISSION_DENIED") == true -> {
                                        "Không có quyền tải khung giờ. Kiểm tra quyền sở hữu sân."
                                    }
                                    error.message?.contains("UNAVAILABLE") == true -> {
                                        "Không thể kết nối với Firestore. Vui lòng kiểm tra mạng."
                                    }
                                    else -> "Lỗi khi tải dữ liệu khung giờ: ${error.message}"
                                }
                                runOnUiThread {
                                    Toast.makeText(this@ItemDetailOwnerActivity, errorMessage, Toast.LENGTH_LONG).show()
                                }
                                // Retry sau 2 giây
                                lifecycleScope.launch {
                                    delay(2000)
                                    reloadTimeSlots()
                                }
                                return@addSnapshotListener
                            }

                            if (snapshot == null) {
                                Log.w("ItemDetailOwnerActivity", "Snapshot is null for coSoID: $coSoID")
                                runOnUiThread {
                                    Toast.makeText(this@ItemDetailOwnerActivity, "Không thể tải dữ liệu khung giờ", Toast.LENGTH_SHORT).show()
                                }
                                // Retry sau 2 giây
                                lifecycleScope.launch {
                                    delay(2000)
                                    reloadTimeSlots()
                                }
                                return@addSnapshotListener
                            }

                            timeSlots.clear()
                            if (snapshot.isEmpty) {
                                Log.d("ItemDetailOwnerActivity", "No time slots found for coSoID: $coSoID")
                            } else {
                                for (doc in snapshot.documents) {
                                    val timeSlot = doc.toObject(TimeSlot::class.java)?.copy(pricingID = doc.id)
                                    if (timeSlot != null && isValidTimeSlot(timeSlot)) {
                                        timeSlots.add(timeSlot)
                                    } else {
                                        Log.w("ItemDetailOwnerActivity", "Invalid time slot data: ${doc.id}, data: ${doc.data}")
                                    }
                                }
                                Log.d("ItemDetailOwnerActivity", "Loaded ${timeSlots.size} time slots for coSoID: $coSoID")
                            }

                            runOnUiThread {
                                priceBoardAdapter.notifyDataSetChanged()
                                updateRecyclerViewVisibility()
                            }
                        }
                    return@launch // Thoát vòng lặp nếu snapshot listener được thiết lập thành công
                } catch (e: Exception) {
                    Log.e("ItemDetailOwnerActivity", "Error setting up snapshot listener: ${e.message}", e)
                    retryCount++
                    if (retryCount < maxRetries) {
                        delay(2000) // Chờ 2 giây trước khi thử lại
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
                val snapshot = db.collection("timeSlots")
                    .whereEqualTo("ownerID", ownerID)
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()

                withContext(Dispatchers.Main) {
                    timeSlots.clear()
                    if (snapshot.isEmpty) {
                        Log.d("ItemDetailOwnerActivity", "No time slots found for coSoID: $coSoID on reload")
                    } else {
                        for (doc in snapshot.documents) {
                            val timeSlot = doc.toObject(TimeSlot::class.java)?.copy(pricingID = doc.id)
                            if (timeSlot != null && isValidTimeSlot(timeSlot)) {
                                timeSlots.add(timeSlot)
                            } else {
                                Log.w("ItemDetailOwnerActivity", "Invalid time slot data on reload: ${doc.id}, data: ${doc.data}")
                            }
                        }
                        Log.d("ItemDetailOwnerActivity", "Reloaded ${timeSlots.size} time slots for coSoID: $coSoID")
                    }
                    priceBoardAdapter.notifyDataSetChanged()
                    updateRecyclerViewVisibility()
                }
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error reloading time slots: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải lại khung giờ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isValidTimeSlot(timeSlot: TimeSlot): Boolean {
        return timeSlot.session.isNotEmpty() &&
                timeSlot.courtSize.isNotEmpty() &&
                timeSlot.period.isNotEmpty() &&
                timeSlot.price > 0
    }

    private fun loadFacilityDetails() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val facilityDoc = withContext(Dispatchers.IO) {
                    db.collection("sport_facilities").document(coSoID).get().await()
                }
                val facility = facilityDoc.toObject(SportFacility::class.java)
                facility?.let {
                    binding.txtTitleDetailOwner.text = it.name
                    binding.txtDiaChiDetailOwner.text = it.diaChi
                    binding.txtSdtDetailOwner.text = it.phoneContact
                    binding.txtEmailDetailOwner.text = it.email
                    binding.txtGioItemDetail.text = it.openingHours ?: "Chưa cài đặt giờ hoạt động"

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
                Log.e("ItemDetailOwnerActivity", "Error loading facility details: ${e.message}", e)
                Toast.makeText(this@ItemDetailOwnerActivity, "Lỗi khi tải dữ liệu sân: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                binding.progressBar.visibility = View.GONE
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
                binding.progressBar.visibility = View.VISIBLE

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
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền cập nhật sân"
                    e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                    else -> "Lỗi khi cập nhật: ${e.message}"
                }
                Toast.makeText(this@ItemDetailOwnerActivity, errorMessage, Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
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
                binding.progressBar.visibility = View.VISIBLE

                // Refresh authentication state
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("ItemDetailOwnerActivity", "No authenticated user: coSoID=$coSoID")
                    Toast.makeText(this@ItemDetailOwnerActivity, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val currentUid = currentUser.uid
                Log.d("ItemDetailOwnerActivity", "Attempting deletion with UID: $currentUid, coSoID: $coSoID")

                // Kiểm tra quyền sở hữu thông qua sport_facilities
                val facilityDoc = db.collection("sport_facilities").document(coSoID).get().await()
                if (!facilityDoc.exists()) {
                    Log.w("ItemDetailOwnerActivity", "Facility does not exist: coSoID=$coSoID")
                    Toast.makeText(this@ItemDetailOwnerActivity, "Sân không tồn tại", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val facilityOwnerID = facilityDoc.getString("ownerID")
                if (facilityOwnerID != currentUid) {
                    Log.e("ItemDetailOwnerActivity", "Owner mismatch: facility ownerID=$facilityOwnerID, user UID=$currentUid, documentData=${facilityDoc.data}")
                    Toast.makeText(this@ItemDetailOwnerActivity, "Không có quyền xóa: Bạn không sở hữu sân này", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Tạo batch để xóa nguyên tử
                val batch: WriteBatch = db.batch()

                // Xóa courts
                val courtsSnapshot = db.collection("courts")
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()
                courtsSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                // Xóa time slots
                val timeSlotsSnapshot = db.collection("timeSlots")
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()
                timeSlotsSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                // Xóa facility
                batch.delete(db.collection("sport_facilities").document(coSoID))

                // Commit batch
                batch.commit().await()

                Log.d("ItemDetailOwnerActivity", "Successfully deleted facility: coSoID=$coSoID")
                Toast.makeText(this@ItemDetailOwnerActivity, "Đã xóa sân", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Log.e("ItemDetailOwnerActivity", "Error deleting facility: coSoID=$coSoID", e)
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền xóa sân (lỗi xác thực)"
                    e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                    else -> "Lỗi khi xóa sân: ${e.message}"
                }
                Toast.makeText(this@ItemDetailOwnerActivity, errorMessage, Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showDebugDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // Hàm kiểm tra kết nối mạng mới
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}