package com.trungkien.fbtp.renter.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.R
import com.trungkien.fbtp.Adapter.UploadAdapter
import com.trungkien.fbtp.databinding.FindFootballBinding
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FindFootballActivity : AppCompatActivity() {
    private lateinit var binding: FindFootballBinding
    private lateinit var adapter: UploadAdapter
    private val db = FirebaseFirestore.getInstance()
    private val facilityList = mutableListOf<SportFacility>()
    private val filteredFacilityList = mutableListOf<SportFacility>()

    companion object {
        private const val TAG = "FindFootballActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FindFootballBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo RecyclerView và adapter
        binding.listInforFB.layoutManager = LinearLayoutManager(this)
        adapter = UploadAdapter(
            filteredFacilityList,
            userRole = "renter", // Thêm userRole
            onItemClick = { facility ->
                val intent = Intent(this, ItemDetailUserActivity::class.java).apply {
                    putExtra("coSoID", facility.coSoID)
                }
                startActivity(intent)
            },
            onBookClick = { facility ->
                handleBookClick(facility)
            },
            onNotificationClick = {}
        )
        binding.listInforFB.adapter = adapter

        // Xử lý nút back
        binding.btnBackFb.setOnClickListener {
            finish()
        }

        // Xử lý tìm kiếm
        binding.edtTimKiemFB.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFacilities(s.toString())
            }
        })

        binding.edtTimKiemFB.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterFacilities(binding.edtTimKiemFB.text.toString())
                true
            } else {
                false
            }
        }

        // Tải dữ liệu
        if (isNetworkAvailable()) {
            loadFacilities()
        } else {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(
                this,
                "Không có kết nối mạng. Vui lòng kiểm tra Wi-Fi hoặc dữ liệu di động.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnected == true
    }

    private fun handleBookClick(sportFacility: SportFacility) {
        lifecycleScope.launch {
            try {
                val snapshot = db.collection("courts")
                    .whereEqualTo("coSoID", sportFacility.coSoID)
                    .get()
                    .await()
                val courts = snapshot.toObjects(Court::class.java)

                if (courts.isEmpty()) {
                    Toast.makeText(this@FindFootballActivity, "Không có sân nào khả dụng", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (courts.size > 1) {
                    val courtTypes = courts.map { it.size }.distinct().toTypedArray()
                    AlertDialog.Builder(this@FindFootballActivity)
                        .setTitle("Chọn loại sân")
                        .setItems(courtTypes) { _, which ->
                            val selectedCourt = courts.find { it.size == courtTypes[which] }
                            selectedCourt?.let {
                                startBookingActivity(sportFacility.coSoID, it.courtID, it.size)
                            }
                        }
                        .setNegativeButton("Hủy", null)
                        .show()
                } else {
                    val court = courts.first()
                    startBookingActivity(sportFacility.coSoID, court.courtID, court.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching courts: ${e.message}", e)
                Toast.makeText(this@FindFootballActivity, "Lỗi khi tải danh sách sân: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBookingActivity(coSoID: String, courtID: String, courtType: String) {
        val intent = Intent(this, DatLichActivity::class.java).apply {
            putExtra("coSoID", coSoID)
            putExtra("courtID", courtID)
            putExtra("courtType", courtType)
        }
        startActivity(intent)
    }

    private fun loadFacilities() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                facilityList.clear()
                filteredFacilityList.clear()

                // Truy vấn các sân thuộc loại "Football"
                val courtSnapshot = withContext(Dispatchers.IO) {
                    db.collection("courts")
                        .whereEqualTo("sportType", "Football")
                        .get()
                        .await()
                }

                Log.d(TAG, "Số lượng tài liệu courts với sportType=Football: ${courtSnapshot.size()}")

                if (courtSnapshot.isEmpty) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@FindFootballActivity, "Không tìm thấy sân bóng đá nào trong Firestore", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                    return@launch
                }

                // Tải thông tin cơ sở
                for (courtDoc in courtSnapshot) {
                    val court = courtDoc.toObject(com.trungkien.fbtp.model.Court::class.java)
                    Log.d(TAG, "Xử lý court: coSoID=${court.coSoID}, sportType=${court.sportType}")
                    try {
                        val facilityDoc = withContext(Dispatchers.IO) {
                            db.collection("sport_facilities")
                                .document(court.coSoID)
                                .get()
                                .await()
                        }

                        if (facilityDoc.exists()) {
                            Log.d(TAG, "Dữ liệu sport_facilities ${court.coSoID}: ${facilityDoc.data}")
                            val facility = facilityDoc.toObject(SportFacility::class.java)
                            facility?.let {
                                if (!facilityList.any { existing -> existing.coSoID == it.coSoID }) {
                                    facilityList.add(it)
                                    Log.d(TAG, "Thêm sân: name=${it.name}, coSoID=${it.coSoID}")
                                } else {
                                    Log.d(TAG, "Bỏ qua sân trùng lặp: name=${it.name}, coSoID=${it.coSoID}")
                                }
                            } ?: Log.w(TAG, "Không ánh xạ được SportFacility cho coSoID: ${court.coSoID}")
                        } else {
                            Log.w(TAG, "Không tìm thấy cơ sở với coSoID: ${court.coSoID}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi tải cơ sở ${court.coSoID}: ${e.message}", e)
                    }
                }

                // Cập nhật danh sách hiển thị
                filteredFacilityList.addAll(facilityList)
                binding.progressBar.visibility = View.GONE
                if (filteredFacilityList.isEmpty()) {
                    Toast.makeText(this@FindFootballActivity, "Không tìm thấy sân bóng đá nào hợp lệ", Toast.LENGTH_SHORT).show()
                } else {
                    adapter.notifyDataSetChanged()
                    Log.d(TAG, "Số lượng sân hiển thị: ${filteredFacilityList.size}")
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@FindFootballActivity, "Lỗi khi tải danh sách sân: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Lỗi khi tải danh sách: ${e.message}", e)
            }
        }
    }

    private fun filterFacilities(query: String) {
        filteredFacilityList.clear()
        if (query.isEmpty()) {
            filteredFacilityList.addAll(facilityList)
        } else {
            val lowerCaseQuery = query.lowercase()
            filteredFacilityList.addAll(facilityList.filter {
                it.name.lowercase().contains(lowerCaseQuery) ||
                        it.diaChi.lowercase().contains(lowerCaseQuery)
            })
        }
        adapter.notifyDataSetChanged()
        if (filteredFacilityList.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy sân phù hợp", Toast.LENGTH_SHORT).show()
        }
    }
}