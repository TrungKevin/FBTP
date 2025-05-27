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
import com.google.firebase.firestore.ListenerRegistration
import com.trungkien.fbtp.Adapter.UploadAdapter
import com.trungkien.fbtp.databinding.FindBadmintonBinding
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FindBadmintonActivity : AppCompatActivity() {
    private lateinit var binding: FindBadmintonBinding
    private lateinit var adapter: UploadAdapter
    private val db = FirebaseFirestore.getInstance()
    private val facilityList = mutableListOf<SportFacility>()
    private val filteredFacilityList = mutableListOf<SportFacility>()
    private var courtListener: ListenerRegistration? = null
    private var facilityListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "FindBadmintonActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FindBadmintonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo RecyclerView và adapter
        binding.listInforBmt.layoutManager = LinearLayoutManager(this)
        adapter = UploadAdapter(
            filteredFacilityList,
            userRole = "renter",
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
        binding.listInforBmt.adapter = adapter

        // Xử lý nút back
        binding.btnBackBmt.setOnClickListener {
            finish()
        }

        // Xử lý tìm kiếm
        binding.edtTimKiemBmt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFacilities(s.toString())
            }
        })

        binding.edtTimKiemBmt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterFacilities(binding.edtTimKiemBmt.text.toString())
                true
            } else {
                false
            }
        }

        // Thiết lập listener cho dữ liệu
        if (isNetworkAvailable()) {
            setupSnapshotListener()
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
                    Toast.makeText(this@FindBadmintonActivity, "Không có sân nào khả dụng", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (courts.size > 1) {
                    val courtTypes = courts.map { it.size }.distinct().toTypedArray()
                    AlertDialog.Builder(this@FindBadmintonActivity)
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
                Toast.makeText(this@FindBadmintonActivity, "Lỗi khi tải danh sách sân: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun setupSnapshotListener() {
        binding.progressBar.visibility = View.VISIBLE
        facilityList.clear()
        filteredFacilityList.clear()
        adapter.notifyDataSetChanged()

        // Lắng nghe collection courts
        courtListener = db.collection("courts")
            .whereEqualTo("sportType", "Badminton")
            .addSnapshotListener { courtSnapshot, courtError ->
                if (courtError != null) {
                    Log.e(TAG, "Error listening to courts: ${courtError.message}", courtError)
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Lỗi khi tải dữ liệu sân: ${courtError.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (courtSnapshot == null || courtSnapshot.isEmpty) {
                    Log.d(TAG, "No courts found for sportType=Badminton")
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Không tìm thấy sân cầu lông nào", Toast.LENGTH_SHORT).show()
                        updateUI(emptyList())
                    }
                    return@addSnapshotListener
                }

                lifecycleScope.launch {
                    try {
                        val facilitiesMap = mutableMapOf<String, SportFacility>()
                        for (courtDoc in courtSnapshot.documents) {
                            val court = courtDoc.toObject(Court::class.java) ?: continue
                            Log.d(TAG, "Processing court: coSoID=${court.coSoID}, sportType=${court.sportType}")

                            try {
                                val facilityDoc = withContext(Dispatchers.IO) {
                                    db.collection("sport_facilities")
                                        .document(court.coSoID)
                                        .get()
                                        .await()
                                }

                                if (facilityDoc.exists()) {
                                    val facility = facilityDoc.toObject(SportFacility::class.java)?.copy(coSoID = facilityDoc.id)
                                    facility?.let {
                                        facilitiesMap[it.coSoID] = it
                                        Log.d(TAG, "Added facility: name=${it.name}, coSoID=${it.coSoID}")
                                    } ?: Log.w(TAG, "Failed to map SportFacility for coSoID: ${court.coSoID}")
                                } else {
                                    Log.w(TAG, "No facility found for coSoID: ${court.coSoID}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching facility ${court.coSoID}: ${e.message}", e)
                            }
                        }

                        // Lắng nghe collection sport_facilities để cập nhật thay đổi
                        facilityListener?.remove()
                        facilityListener = db.collection("sport_facilities")
                            .whereIn("coSoID", facilitiesMap.keys.toList())
                            .addSnapshotListener { facilitySnapshot, facilityError ->
                                if (facilityError != null) {
                                    Log.e(TAG, "Error listening to facilities: ${facilityError.message}", facilityError)
                                    runOnUiThread {
                                        Toast.makeText(this@FindBadmintonActivity, "Lỗi khi tải dữ liệu cơ sở: ${facilityError.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    return@addSnapshotListener
                                }

                                if (facilitySnapshot != null) {
                                    for (doc in facilitySnapshot.documents) {
                                        val facility = doc.toObject(SportFacility::class.java)?.copy(coSoID = doc.id)
                                        facility?.let {
                                            facilitiesMap[it.coSoID] = it
                                            Log.d(TAG, "Updated facility: name=${it.name}, coSoID=${it.coSoID}")
                                        }
                                    }
                                }

                                runOnUiThread {
                                    binding.progressBar.visibility = View.GONE
                                    updateUI(facilitiesMap.values.toList())
                                }
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing courts: ${e.message}", e)
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@FindBadmintonActivity, "Lỗi khi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
                            updateUI(emptyList())
                        }
                    }
                }
            }
    }

    private fun updateUI(facilities: List<SportFacility>) {
        facilityList.clear()
        facilityList.addAll(facilities)
        filterFacilities(binding.edtTimKiemBmt.text.toString())
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

    override fun onDestroy() {
        super.onDestroy()
        courtListener?.remove()
        facilityListener?.remove()
    }
}