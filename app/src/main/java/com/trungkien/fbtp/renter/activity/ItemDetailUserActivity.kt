package com.trungkien.fbtp.renter.activity


import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.Adapter.PriceDetailRenterAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.DetailItemUserBinding
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

class ItemDetailUserActivity : AppCompatActivity() {
    private lateinit var binding: DetailItemUserBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var coSoID: String = ""
    private var courtID: String? = null // Store first courtID
    private var courtType: String? = null // Store first courtType (courtSize)
    private lateinit var priceAdapter: PriceDetailRenterAdapter // Store adapter as a field

    companion object {
        private const val TAG = "ItemDetailUserActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = DetailItemUserBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating layout: ${e.message}", e)
            Toast.makeText(this, "Lỗi giao diện, vui lòng thử lại", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        coSoID = intent.getStringExtra("coSoID") ?: ""
        if (coSoID.isEmpty()) {
            Log.e(TAG, "coSoID is empty")
            Toast.makeText(this, "ID cơ sở không hợp lệ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize RecyclerView with an empty adapter
        try {
            priceAdapter = PriceDetailRenterAdapter(this, emptyList()) { timeSlot ->
                Toast.makeText(this, "Khung giờ: ${timeSlot.period}", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Clicked TimeSlot, period: ${timeSlot.period}")
            }
            binding.priceDetailListRenter.layoutManager = LinearLayoutManager(this)
            binding.priceDetailListRenter.adapter = priceAdapter
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing RecyclerView: ${e.message}", e)
            Toast.makeText(this, "Lỗi khởi tạo danh sách giá", Toast.LENGTH_SHORT).show()
        }

        // Get imageBase64 from Intent
        val imageBase64 = intent.getStringExtra("imageBase64")

        binding.btnBackDetailRenter.setOnClickListener {
            finish()
        }

        binding.btnDatLichDetail.setOnClickListener {
            if (courtID == null || courtType == null) {
                Toast.makeText(this, "Không có sân hoặc loại sân khả dụng để đặt lịch", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, DatLichActivity::class.java).apply {
                putExtra("coSoID", coSoID)
                putExtra("courtID", courtID)
                putExtra("courtType", courtType)
            }
            startActivity(intent)
        }

        // Load image from Intent if available
        if (!imageBase64.isNullOrEmpty()) {
            try {
                val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                binding.imgDetailRenter.setImageBitmap(bitmap)
            } catch (e: Exception) {
                binding.imgDetailRenter.setImageResource(R.drawable.sanbong)
                Log.e(TAG, "Error decoding Base64 image from Intent: ${e.message}")
            }
            // Load other data from Firestore
            loadSportFacility(coSoID)
        } else {
            // Load all data including image from Firestore
            loadSportFacility(coSoID)
        }
    }

    private fun loadSportFacility(coSoID: String) {
        binding.progressBar.visibility = View.VISIBLE

        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Vui lòng đăng nhập để xem chi tiết cơ sở", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AccountActivity::class.java))
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val document = db.collection("sport_facilities")
                    .document(coSoID)
                    .get()
                    .await()

                if (document.exists()) {
                    val sportFacility = document.toObject(SportFacility::class.java)
                    if (sportFacility == null) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@ItemDetailUserActivity, "Dữ liệu không hợp lệ", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    binding.txtTitleDetailRenter.text = sportFacility.name
                    binding.txtDiaChiDetailRenter.text = sportFacility.diaChi
                    binding.txtSdtDetailRenter.text = sportFacility.phoneContact
                    binding.txtEmailDetailRenter.text = sportFacility.email.ifEmpty { "N/A" }
                    binding.txtGioItemDetailRenter.text = sportFacility.openingHours ?: "N/A"

                    // Only load image from Firestore if not provided by Intent
                    if (intent.getStringExtra("imageBase64").isNullOrEmpty()) {
                        if (sportFacility.images.isNotEmpty()) {
                            try {
                                val decodedBytes = Base64.decode(sportFacility.images[0], Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                binding.imgDetailRenter.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                binding.imgDetailRenter.setImageResource(R.drawable.sanbong)
                                Log.e(TAG, "Error decoding Base64 image from Firestore: ${e.message}")
                            }
                        } else {
                            binding.imgDetailRenter.setImageResource(R.drawable.sanbong)
                        }
                    }

                    loadTimeSlots(coSoID)
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ItemDetailUserActivity, "Không tìm thấy cơ sở thể thao", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ItemDetailUserActivity, "Lỗi khi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error loading sport facility: $coSoID", e)
            }
        }
    }

    private fun loadTimeSlots(coSoID: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Kiểm tra xác thực
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ItemDetailUserActivity, "Vui lòng đăng nhập để xem khung giờ", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@ItemDetailUserActivity, AccountActivity::class.java))
                    finish()
                    return@launch
                }

                // Kiểm tra coSoID hợp lệ
                if (coSoID.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyStateView.visibility = View.VISIBLE
                    binding.priceDetailListRenter.visibility = View.GONE
                    Toast.makeText(this@ItemDetailUserActivity, "ID cơ sở không hợp lệ", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Invalid coSoID: $coSoID")
                    return@launch
                }

                // Kiểm tra sport_facilities tồn tại
                val facilityDoc = db.collection("sport_facilities").document(coSoID).get().await()
                if (!facilityDoc.exists()) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyStateView.visibility = View.VISIBLE
                    binding.priceDetailListRenter.visibility = View.GONE
                    Toast.makeText(this@ItemDetailUserActivity, "Cơ sở thể thao không tồn tại", Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "Sport facility does not exist: coSoID=$coSoID")
                    return@launch
                }

                // Truy vấn timeSlots theo coSoID với debug
                Log.d(TAG, "Querying timeSlots for coSoID: $coSoID")
                val snapshot = db.collection("timeSlots")
                    .whereEqualTo("coSoID", coSoID)
                    .get()
                    .await()

                // Log số lượng tài liệu tìm thấy
                Log.d(TAG, "Found ${snapshot.documents.size} time slot documents for coSoID: $coSoID")
                if (!snapshot.isEmpty) {
                    // Log chi tiết từng tài liệu
                    snapshot.documents.forEach { doc ->
                        Log.d(TAG, "TimeSlot document ID: ${doc.id}, Data: ${doc.data}")
                    }
                }

                if (snapshot.isEmpty) {
                    binding.emptyStateView.visibility = View.VISIBLE
                    binding.priceDetailListRenter.visibility = View.GONE
                    Toast.makeText(
                        this@ItemDetailUserActivity,
                        "Không tìm thấy khung giờ cho cơ sở này. Vui lòng kiểm tra dữ liệu hoặc liên hệ quản trị viên.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.w(TAG, "No time slots found for coSoID: $coSoID. Check Firestore data or query.")
                } else {
                    binding.emptyStateView.visibility = View.GONE
                    binding.priceDetailListRenter.visibility = View.VISIBLE

                    // Chuyển đổi dữ liệu thành danh sách TimeSlot
                    val timeSlots = snapshot.documents.mapNotNull { doc ->
                        try {
                            val period = doc.getString("period") ?:
                            "${doc.getString("startTime") ?: "N/A"} - ${doc.getString("endTime") ?: "N/A"}"
                            TimeSlot(
                                scheduleID = doc.id,
                                price = doc.getDouble("price") ?: 0.0,
                                courtSize = doc.getString("courtSize") ?: "N/A",
                                period = period,
                                session = doc.getString("session") ?: "N/A",
                                coSoID = doc.getString("coSoID") ?: "",
                                courtID = doc.getString("courtID") ?: ""
                            ).also {
                                // Store first courtID and courtType
                                if (courtID == null && it.courtID.isNotEmpty()) {
                                    courtID = it.courtID
                                    courtType = it.courtSize
                                    Log.d(TAG, "Set courtID: $courtID, courtType: $courtType")
                                }
                                Log.d(TAG, "Parsed TimeSlot ${doc.id}, period: ${it.period}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing TimeSlot document ${doc.id}: ${e.message}, data: ${doc.data}")
                            null
                        }
                    }

                    if (timeSlots.isEmpty()) {
                        binding.emptyStateView.visibility = View.VISIBLE
                        binding.priceDetailListRenter.visibility = View.GONE
                        Toast.makeText(
                            this@ItemDetailUserActivity,
                            "Không có khung giờ hợp lệ để hiển thị. Vui lòng kiểm tra dữ liệu.",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.w(TAG, "No valid time slots after parsing for coSoID: $coSoID")
                    } else {
                        // Update adapter data instead of setting a new adapter
                        priceAdapter.updateData(timeSlots)
                        Log.d(TAG, "Loaded ${timeSlots.size} time slots for coSoID: $coSoID")
                    }
                }

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền truy cập khung giờ. Vui lòng kiểm tra quyền truy cập Firestore."
                    e.message?.contains("UNAVAILABLE") == true -> "Không thể kết nối với Firestore. Vui lòng kiểm tra mạng."
                    else -> "Lỗi khi tải khung giờ: ${e.message}"
                }
                Toast.makeText(this@ItemDetailUserActivity, errorMessage, Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error loading time slots for coSoID: $coSoID", e)
                binding.emptyStateView.visibility = View.VISIBLE
                binding.priceDetailListRenter.visibility = View.GONE
            }
        }
    }

    private fun formatPrice(price: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
        return formatter.format(price)
    }
}