package com.trungkien.fbtp.owner.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.trungkien.fbtp.AddPriceBoardAdapter
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.FragmentFourthBinding
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class FourthFragment : Fragment() {

    private var _binding: FragmentFourthBinding? = null
    private val binding get() = _binding!!
    private lateinit var priceBoardAdapter: AddPriceBoardAdapter
    private val timeSlots = mutableListOf<TimeSlot>()
    private var coSoID: String = ""
    private var courtType: String = "Football"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFourthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Kiểm tra trạng thái xác thực
        if (auth.currentUser == null) {
            Toast.makeText(context, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
            requireActivity().finish()
            return
        }

        coSoID = arguments?.getString("coSoID") ?: ""
        lifecycleScope.launch {
            try {
                // Lấy tất cả cơ sở của người dùng
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", auth.currentUser?.uid)
                    .get()
                    .await()
                if (facilitiesSnapshot.isEmpty) {
                    Toast.makeText(context, "Không tìm thấy sân nào. Vui lòng tạo sân.", Toast.LENGTH_LONG).show()
                    binding.root.isEnabled = false
                    return@launch
                }

                // Sử dụng coSoID được cung cấp nếu hợp lệ, nếu không thì lấy cơ sở đầu tiên
                if (coSoID.isEmpty() || facilitiesSnapshot.documents.none { it.id == coSoID }) {
                    coSoID = facilitiesSnapshot.documents.first().id
                }

                // Lấy courtType từ sân đầu tiên của bất kỳ cơ sở nào
                val courtSnapshot = firestore.collection("courts")
                    .whereEqualTo("coSoID", coSoID)
                    .limit(1)
                    .get()
                    .await()
                courtType = courtSnapshot.documents.firstOrNull()?.getString("sportType") ?: "Football"

                setupFragment()
            } catch (e: Exception) {
                Log.e("FourthFragment", "Lỗi khi lấy thông tin cơ sở", e)
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Không có quyền truy cập thông tin sân. Vui lòng kiểm tra quyền."
                } else {
                    "Lỗi khi tải thông tin sân: ${e.message}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                binding.root.isEnabled = false
            }
        }
    }

    private fun setupFragment() {
        setupRecyclerView()
        setupInputFields()
        setupListeners()
        listenToFirestoreChanges()
    }

    private fun setupRecyclerView() {
        priceBoardAdapter = AddPriceBoardAdapter(
            requireContext(),
            timeSlots,
            onUpdateClick = { position, updatedTimeSlot ->
                if (isLoading) return@AddPriceBoardAdapter // Ngăn chặn click khi đang tải
                lifecycleScope.launch {
                    try {
                        // Kiểm tra dữ liệu hợp lệ
                        if (updatedTimeSlot.pricingID.isNullOrEmpty() || updatedTimeSlot.coSoID.isNullOrEmpty() || updatedTimeSlot.ownerID.isNullOrEmpty()) {
                            Log.e("FourthFragment", "Dữ liệu khung giờ không hợp lệ: pricingID=${updatedTimeSlot.pricingID}, coSoID=${updatedTimeSlot.coSoID}, ownerID=${updatedTimeSlot.ownerID}")
                            Toast.makeText(context, "Dữ liệu khung giờ không hợp lệ", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (updatedTimeSlot.session.isEmpty() || updatedTimeSlot.courtSize.isEmpty() || updatedTimeSlot.period.isEmpty() || updatedTimeSlot.price <= 0) {
                            Log.e("FourthFragment", "Trường khung giờ không hợp lệ: session=${updatedTimeSlot.session}, courtSize=${updatedTimeSlot.courtSize}, period=${updatedTimeSlot.period}, price=${updatedTimeSlot.price}")
                            Toast.makeText(context, "Vui lòng điền đầy đủ thông tin khung giờ", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        showLoading(true)
                        updateTimeSlotInFirestore(updatedTimeSlot, timeSlots[position])
                        // Danh sách timeSlots sẽ được cập nhật qua listenToFirestoreChanges
                        priceBoardAdapter.notifyItemChanged(position)
                        Toast.makeText(context, "Cập nhật khung giờ thành công trên tất cả sân", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("FourthFragment", "Lỗi khi cập nhật khung giờ", e)
                        val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                            "Không có quyền cập nhật khung giờ. Vui lòng kiểm tra quyền."
                        } else {
                            "Lỗi khi cập nhật khung giờ: ${e.message}"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    } finally {
                        showLoading(false)
                    }
                }
            },
            onDeleteClick = { position ->
                if (isLoading) return@AddPriceBoardAdapter // Ngăn chặn click khi đang tải
                AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn chắc chắn muốn xóa khung giờ này?")
                    .setPositiveButton("Có") { _, _ ->
                        val timeSlot = timeSlots[position]
                        lifecycleScope.launch {
                            try {
                                showLoading(true)
                                deleteTimeSlotFromFirestore(timeSlot)
                                // Danh sách timeSlots sẽ được cập nhật qua listenToFirestoreChanges
                                priceBoardAdapter.notifyItemRemoved(position)
                                Toast.makeText(context, "Xóa khung giờ thành công", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("FourthFragment", "Lỗi khi xóa khung giờ", e)
                                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                                    "Không có quyền xóa khung giờ. Vui lòng kiểm tra quyền."
                                } else {
                                    "Lỗi khi xóa khung giờ: ${e.message}"
                                }
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            } finally {
                                showLoading(false)
                            }
                        }
                    }
                    .setNegativeButton("Không", null)
                    .show()
            }
        )
        binding.rcvAddKhungGio.apply {
            adapter = priceBoardAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
    }

    private fun setupInputFields() {
        val sessions = listOf("Sáng", "Chiều", "Tối")
        val sessionAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_menu_item, sessions)
        binding.edtSession.setAdapter(sessionAdapter)
        binding.edtSession.setOnClickListener { binding.edtSession.showDropDown() }
        binding.edtSession.setOnTouchListener { _, _ -> binding.edtSession.showDropDown(); false }

        val courtSizes = when (courtType) {
            "Football" -> listOf("5 vs 5", "7 vs 7", "11 vs 11")
            "Badminton" -> listOf("Sân Đơn", "Sân Đôi")
            "Tennis" -> listOf("Sân Đất Nện", "Sân Cỏ", "Sân Thảm")
            "Pickleball" -> listOf("Sân Ngoài Trời", "Sân Trong Nhà")
            else -> listOf(courtType)
        }
        val courtSizeAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_menu_item, courtSizes)
        binding.edtCourtSize.setAdapter(courtSizeAdapter)
        binding.edtCourtSize.setOnClickListener { binding.edtCourtSize.showDropDown() }
        binding.edtCourtSize.setOnTouchListener { _, _ -> binding.edtCourtSize.showDropDown(); false }
    }

    private fun setupListeners() {
        binding.btnXacNhan.setOnClickListener {
            if (isLoading) return@setOnClickListener // Ngăn chặn click khi đang tải
            val timeSlot = createTimeSlotFromInputs()
            if (timeSlot != null) {
                lifecycleScope.launch {
                    try {
                        showLoading(true)
                        timeSlots.add(timeSlot)
                        saveTimeSlotToFirestore(timeSlot)
                        priceBoardAdapter.notifyItemInserted(timeSlots.size - 1)
                        clearInputFields()
                        Toast.makeText(context, "Cập nhật bảng giá giờ thành công", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("FourthFragment", "Lỗi khi lưu khung giờ", e)
                        val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                            "Không có quyền lưu khung giờ. Vui lòng kiểm tra quyền."
                        } else {
                            "Lỗi khi lưu khung giờ: ${e.message}"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        timeSlots.remove(timeSlot) // Hoàn tác thay đổi UI nếu thất bại
                        priceBoardAdapter.notifyItemRemoved(timeSlots.size)
                    } finally {
                        showLoading(false)
                    }
                }
            }
        }

        binding.btnAddNewBoard.setOnClickListener {
            if (isLoading) return@setOnClickListener // Ngăn chặn click khi đang tải
            lifecycleScope.launch {
                try {
                    showLoading(true)

                    // Kiểm tra người dùng xác thực
                    val currentUser = auth.currentUser
                    if (currentUser == null) {
                        Log.e("FourthFragment", "Không có người dùng được xác thực")
                        Toast.makeText(context, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val currentUid = currentUser.uid

                    // Lấy tất cả timeSlots của owner và deduplicate
                    val allTimeSlotsSnapshot = firestore.collection("timeSlots")
                        .whereEqualTo("ownerID", currentUid)
                        .get()
                        .await()
                    if (allTimeSlotsSnapshot.isEmpty) {
                        Log.d("FourthFragment", "Không có khung giờ nào để thêm")
                        Toast.makeText(context, "Không có khung giờ nào để thêm", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Deduplicate timeSlots dựa trên session, courtSize, period, price
                    val uniqueTimeSlots = mutableMapOf<String, TimeSlot>()
                    for (doc in allTimeSlotsSnapshot.documents) {
                        val timeSlot = doc.toObject(TimeSlot::class.java) ?: continue
                        val key = "${timeSlot.session}_${timeSlot.courtSize}_${timeSlot.period}_${timeSlot.price}"
                        if (!uniqueTimeSlots.containsKey(key)) {
                            uniqueTimeSlots[key] = timeSlot
                        }
                    }
                    if (uniqueTimeSlots.isEmpty()) {
                        Log.d("FourthFragment", "Không có khung giờ duy nhất để thêm")
                        Toast.makeText(context, "Không có khung giờ nào để thêm", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Lấy tất cả cơ sở của owner
                    val facilitiesSnapshot = firestore.collection("sport_facilities")
                        .whereEqualTo("ownerID", currentUid)
                        .get()
                        .await()
                    if (facilitiesSnapshot.isEmpty) {
                        Log.w("FourthFragment", "Không tìm thấy cơ sở nào cho ownerID: $currentUid")
                        Toast.makeText(context, "Không tìm thấy sân nào để thêm khung giờ", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Tìm các cơ sở có price_detail_list_owner trống
                    val emptyFacilities = mutableListOf<String>()
                    for (facilityDoc in facilitiesSnapshot.documents) {
                        val facilityCoSoID = facilityDoc.id
                        val timeSlotsSnapshot = firestore.collection("timeSlots")
                            .whereEqualTo("coSoID", facilityCoSoID)
                            .get()
                            .await()
                        if (timeSlotsSnapshot.isEmpty) {
                            emptyFacilities.add(facilityCoSoID)
                        }
                    }

                    if (emptyFacilities.isEmpty()) {
                        Log.d("FourthFragment", "Không có cơ sở nào có price_detail_list_owner trống")
                        Toast.makeText(context, "Không có sân nào cần thêm khung giờ", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    // Tạo batch để thêm timeSlots
                    val batch = firestore.batch()
                    var addedCount = 0

                    for (coSoID in emptyFacilities) {
                        // Lấy tất cả sân cho cơ sở này
                        val courtsSnapshot = firestore.collection("courts")
                            .whereEqualTo("coSoID", coSoID)
                            .get()
                            .await()
                        if (courtsSnapshot.isEmpty) {
                            Log.w("FourthFragment", "Không tìm thấy sân nào cho coSoID: $coSoID")
                            continue
                        }

                        // Thêm các timeSlots duy nhất vào cơ sở này
                        for ((key, timeSlot) in uniqueTimeSlots) {
                            for (courtDoc in courtsSnapshot.documents) {
                                val courtID = courtDoc.id
                                val newTimeSlot = timeSlot.copy(
                                    pricingID = UUID.randomUUID().toString(),
                                    coSoID = coSoID,
                                    courtID = courtID
                                )
                                batch.set(
                                    firestore.collection("timeSlots").document(newTimeSlot.pricingID),
                                    newTimeSlot
                                )
                                addedCount++
                                Log.d("FourthFragment", "Thêm khung giờ: ${newTimeSlot.pricingID} cho coSoID: $coSoID, courtID: $courtID, key: $key")
                            }
                        }
                    }

                    // Thực hiện batch
                    if (addedCount > 0) {
                        batch.commit().await()
                        Log.d("FourthFragment", "Đã thêm $addedCount khung giờ cho ${emptyFacilities.size} cơ sở")
                        Toast.makeText(context, "Thêm khung giờ cho sân thành công", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("FourthFragment", "Không có khung giờ nào được thêm")
                        Toast.makeText(context, "Không có khung giờ nào được thêm", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("FourthFragment", "Lỗi khi thêm khung giờ", e)
                    val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                        "Không có quyền thêm khung giờ. Vui lòng kiểm tra quyền."
                    } else {
                        "Lỗi khi thêm khung giờ: ${e.message}"
                    }
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                } finally {
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        isLoading = show
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnXacNhan.isEnabled = !show
        binding.btnAddNewBoard.isEnabled = !show // Vô hiệu hóa nút trong khi tải
        binding.root.isEnabled = !show // Vô hiệu hóa root view để ngăn tương tác
        // Thông báo adapter để vô hiệu hóa các nút
        priceBoardAdapter.setLoading(show)
    }

    private fun createTimeSlotFromInputs(): TimeSlot? {
        val session = binding.edtSession.text.toString()
        val courtSize = binding.edtCourtSize.text.toString()
        val period = binding.edtPeriod.text.toString()
        val priceStr = binding.edtPrice.text.toString()
        val openingHours = binding.edtGioHoatDong.text.toString()

        if (session.isEmpty()) {
            Toast.makeText(context, "Vui lòng chọn buổi", Toast.LENGTH_SHORT).show()
            return null
        }
        if (courtSize.isEmpty()) {
            Toast.makeText(context, "Vui lòng chọn cỡ sân", Toast.LENGTH_SHORT).show()
            return null
        }
        if (period.isEmpty()) {
            Toast.makeText(context, "Vui lòng nhập thời gian", Toast.LENGTH_SHORT).show()
            return null
        }
        val price = priceStr.toDoubleOrNull()
        if (price == null || price <= 0) {
            Toast.makeText(context, "Vui lòng nhập giá hợp lệ", Toast.LENGTH_SHORT).show()
            return null
        }

        if (openingHours.isNotEmpty()) {
            if (!openingHours.matches(Regex("^\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}$"))) {
                Toast.makeText(context, "Giờ hoạt động phải có định dạng 'HH:mm - HH:mm'", Toast.LENGTH_SHORT).show()
                return null
            }
            saveOpeningHoursToFacility(openingHours)
        }

        // Tạo TimeSlot cơ bản không có courtID (sẽ được gán động trong saveTimeSlotToFirestore)
        return TimeSlot(
            scheduleID = null,
            price = price,
            courtSize = courtSize,
            period = period,
            session = session,
            isTimeRange = true,
            courtID = "", // Sẽ được gán động
            pricingID = UUID.randomUUID().toString(),
            ownerID = auth.currentUser?.uid ?: "",
            coSoID = "" // Sẽ được gán động
        )
    }

    private fun saveOpeningHoursToFacility(openingHours: String) {
        lifecycleScope.launch {
            try {
                // Lấy tất cả cơ sở của người dùng
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", auth.currentUser?.uid)
                    .get()
                    .await()

                if (facilitiesSnapshot.isEmpty) {
                    Log.w("FourthFragment", "Không tìm thấy cơ sở nào cho ownerID: ${auth.currentUser?.uid}")
                    return@launch
                }

                // Tạo batch để cập nhật tất cả cơ sở
                val batch: WriteBatch = firestore.batch()
                facilitiesSnapshot.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf("openingHours" to openingHours)
                    )
                }

                // Thực hiện batch
                batch.commit().await()
                Log.d("FourthFragment", "Cập nhật giờ hoạt động: $openingHours cho tất cả cơ sở")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Lỗi khi cập nhật giờ hoạt động", e)
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Không có quyền cập nhật giờ hoạt động. Vui lòng kiểm tra quyền."
                } else {
                    "Lỗi khi lưu giờ hoạt động: ${e.message}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTimeSlotToFirestore(timeSlot: TimeSlot) {
        lifecycleScope.launch {
            try {
                // Lấy tất cả cơ sở của người dùng
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", auth.currentUser?.uid)
                    .get()
                    .await()
                val coSoIDs = facilitiesSnapshot.documents.map { it.id }

                if (coSoIDs.isEmpty()) {
                    throw Exception("Không tìm thấy sân nào của người dùng")
                }

                // Tạo batch để lưu khung giờ cho tất cả sân trong tất cả cơ sở
                val batch = firestore.batch()
                for (coSoID in coSoIDs) {
                    // Lấy tất cả sân cho cơ sở này
                    val courtsSnapshot = firestore.collection("courts")
                        .whereEqualTo("coSoID", coSoID)
                        .get()
                        .await()

                    if (courtsSnapshot.isEmpty) {
                        Log.w("FourthFragment", "Không tìm thấy sân nào cho coSoID: $coSoID")
                        continue
                    }

                    // Lưu khung giờ cho mỗi sân
                    courtsSnapshot.documents.forEach { courtDoc ->
                        val courtID = courtDoc.id
                        val timeSlotForCourt = timeSlot.copy(
                            pricingID = UUID.randomUUID().toString(),
                            coSoID = coSoID,
                            courtID = courtID
                        )
                        batch.set(
                            firestore.collection("timeSlots").document(timeSlotForCourt.pricingID),
                            timeSlotForCourt
                        )
                        Log.d("FourthFragment", "Lưu khung giờ: ${timeSlotForCourt.pricingID} cho coSoID: $coSoID, courtID: $courtID")
                    }
                }

                // Thực hiện batch
                batch.commit().await()
                Log.d("FourthFragment", "Đã lưu khung giờ cho tất cả sân trong ${coSoIDs.size} cơ sở")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Lỗi khi lưu khung giờ: ${e.message}", e)
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Không có quyền lưu khung giờ. Vui lòng kiểm tra quyền."
                } else {
                    "Lỗi khi lưu khung giờ: ${e.message}"
                }
                throw Exception(errorMessage) // Để caller xử lý ngoại lệ
            }
        }
    }

    private fun updateTimeSlotInFirestore(updatedTimeSlot: TimeSlot, originalTimeSlot: TimeSlot) {
        lifecycleScope.launch {
            try {
                // Kiểm tra quyền sở hữu
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("FourthFragment", "Không có người dùng được xác thực")
                    Toast.makeText(context, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val currentUid = currentUser.uid

                // Tạo batch để cập nhật nguyên tử
                val batch = firestore.batch()

                // Tìm tất cả sân của ownerID
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", currentUid)
                    .get()
                    .await()
                val coSoIDs = facilitiesSnapshot.documents.map { it.id }

                // Tìm và cập nhật các timeSlot tương ứng trong tất cả sân
                for (coSoID in coSoIDs) {
                    // Lấy tất cả sân cho cơ sở này
                    val courtsSnapshot = firestore.collection("courts")
                        .whereEqualTo("coSoID", coSoID)
                        .get()
                        .await()

                    for (courtDoc in courtsSnapshot.documents) {
                        val courtID = courtDoc.id
                        // Tìm các khung giờ phù hợp cho sân này
                        val snapshot = firestore.collection("timeSlots")
                            .whereEqualTo("coSoID", coSoID)
                            .whereEqualTo("courtID", courtID)
                            .whereEqualTo("ownerID", currentUid)
                            .whereEqualTo("session", originalTimeSlot.session)
                            .whereEqualTo("courtSize", originalTimeSlot.courtSize)
                            .whereEqualTo("period", originalTimeSlot.period)
                            .whereEqualTo("price", originalTimeSlot.price)
                            .get()
                            .await()

                        for (doc in snapshot.documents) {
                            val newTimeSlot = updatedTimeSlot.copy(
                                pricingID = doc.id,
                                coSoID = coSoID,
                                courtID = courtID
                            )
                            batch.set(
                                firestore.collection("timeSlots").document(doc.id),
                                newTimeSlot
                            )
                            Log.d("FourthFragment", "Cập nhật khung giờ: ${doc.id} cho coSoID: $coSoID, courtID: $courtID")
                        }
                    }
                }

                // Thực hiện batch
                batch.commit().await()
                Log.d("FourthFragment", "Đã cập nhật khung giờ trên tất cả cơ sở cho ownerID: $currentUid")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Lỗi khi cập nhật khung giờ: ${e.message}", e)
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Không có quyền cập nhật khung giờ. Vui lòng kiểm tra quyền."
                } else {
                    "Lỗi khi cập nhật khung giờ: ${e.message}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteTimeSlotFromFirestore(timeSlot: TimeSlot) {
        lifecycleScope.launch {
            try {
                // Kiểm tra quyền sở hữu
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("FourthFragment", "Không có người dùng được xác thực")
                    Toast.makeText(context, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val currentUid = currentUser.uid

                // Tạo batch để xóa nguyên tử
                val batch = firestore.batch()

                // Tìm tất cả sân của ownerID
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", currentUid)
                    .get()
                    .await()
                val coSoIDs = facilitiesSnapshot.documents.map { it.id }

                // Tìm và xóa các timeSlot tương ứng trong tất cả sân
                for (coSoID in coSoIDs) {
                    // Lấy tất cả sân cho cơ sở này
                    val courtsSnapshot = firestore.collection("courts")
                        .whereEqualTo("coSoID", coSoID)
                        .get()
                        .await()

                    for (courtDoc in courtsSnapshot.documents) {
                        val courtID = courtDoc.id
                        // Tìm các khung giờ phù hợp cho sân này
                        val snapshot = firestore.collection("timeSlots")
                            .whereEqualTo("coSoID", coSoID)
                            .whereEqualTo("courtID", courtID)
                            .whereEqualTo("ownerID", currentUid)
                            .whereEqualTo("session", timeSlot.session)
                            .whereEqualTo("courtSize", timeSlot.courtSize)
                            .whereEqualTo("period", timeSlot.period)
                            .whereEqualTo("price", timeSlot.price)
                            .get()
                            .await()

                        for (doc in snapshot.documents) {
                            batch.delete(firestore.collection("timeSlots").document(doc.id))
                            Log.d("FourthFragment", "Xóa khung giờ: ${doc.id} cho coSoID: $coSoID, courtID: $courtID")
                        }
                    }
                }

                // Thực hiện batch
                batch.commit().await()
                Log.d("FourthFragment", "Đã xóa khung giờ trên tất cả cơ sở cho ownerID: $currentUid")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Lỗi khi xóa khung giờ: ${e.message}", e)
                val errorMessage = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    "Không có quyền xóa khung giờ. Vui lòng kiểm tra quyền."
                } else {
                    "Lỗi khi xóa khung giờ: ${e.message}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
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

    private fun listenToFirestoreChanges() {
        if (auth.currentUser == null) {
            Log.w("FourthFragment", "Không có người dùng được xác thực, bỏ qua listener Firestore")
            Toast.makeText(context, "Vui lòng đăng nhập lại để tải dữ liệu", Toast.LENGTH_LONG).show()
            return
        }
        if (coSoID.isEmpty()) {
            Log.e("FourthFragment", "Invalid coSoID: $coSoID")
            Toast.makeText(context, "Dữ liệu sân không hợp lệ", Toast.LENGTH_LONG).show()
            return
        }
        firestore.collection("timeSlots")
            .whereEqualTo("ownerID", auth.currentUser?.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FourthFragment", "Lỗi khi nghe Firestore: ${error.message}")
                    val errorMessage = if (error.message?.contains("PERMISSION_DENIED") == true) {
                        "Không có quyền truy cập dữ liệu khung giờ. Vui lòng kiểm tra quyền."
                    } else {
                        "Lỗi khi tải dữ liệu: ${error.message}"
                    }
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    timeSlots.clear()
                    val uniqueTimeSlots = mutableMapOf<String, TimeSlot>()
                    for (doc in snapshot.documents) {
                        val timeSlot = doc.toObject(TimeSlot::class.java)?.copy(pricingID = doc.id) ?: continue
                        if (isValidTimeSlot(timeSlot) && timeSlot.coSoID == coSoID) {
                            val key = "${timeSlot.session}_${timeSlot.courtSize}_${timeSlot.period}_${timeSlot.price}_${timeSlot.coSoID}"
                            if (!uniqueTimeSlots.containsKey(key)) {
                                uniqueTimeSlots[key] = timeSlot
                            }
                        } else {
                            Log.w("FourthFragment", "Skipped invalid or mismatched time slot: pricingID=${timeSlot.pricingID}, coSoID=${timeSlot.coSoID}, data=${doc.data}")
                        }
                    }

                    // Sắp xếp timeSlots theo yêu cầu
                    val sessionOrder = mapOf("Sáng" to 1, "Chiều" to 2, "Tối" to 3)
                    val sortedTimeSlots = if (courtType == "Football") {
                        // Sắp xếp cho bóng đá: 5 vs 5 (Sáng, Chiều, Tối), 7 vs 7 (Sáng, Chiều, Tối), 11 vs 11 (Sáng, Chiều, Tối)
                        val courtSizeOrder = mapOf("5 vs 5" to 1, "7 vs 7" to 2, "11 vs 11" to 3)
                        uniqueTimeSlots.values.sortedWith(compareBy(
                            { courtSizeOrder[it.courtSize] ?: 4 }, // Sắp xếp theo courtSize
                            { sessionOrder[it.session] ?: 4 } // Sắp xếp theo session
                        ))
                    } else {
                        // Sắp xếp cho các môn khác: mỗi courtSize hoàn thành Sáng, Chiều, Tối trước khi chuyển sang courtSize khác
                        uniqueTimeSlots.values
                            .groupBy { it.courtSize } // Nhóm theo courtSize
                            .values
                            .flatMap { courtGroup ->
                                courtGroup.sortedWith(compareBy { sessionOrder[it.session] ?: 4 }) // Sắp xếp session trong mỗi nhóm
                            }
                    }

                    timeSlots.addAll(sortedTimeSlots)
                    priceBoardAdapter.notifyDataSetChanged()
                    Log.d("FourthFragment", "Đã tải ${timeSlots.size} khung giờ duy nhất cho ownerID: ${auth.currentUser?.uid}, coSoID: $coSoID")
                }
            }
    }

    private fun clearInputFields() {
        binding.edtSession.setText("")
        binding.edtCourtSize.setText("")
        binding.edtPeriod.setText("")
        binding.edtPrice.setText("")
        binding.edtGioHoatDong.setText("") // Xóa trường nhập giờ hoạt động
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(coSoID: String) = FourthFragment().apply {
            arguments = Bundle().apply {
                putString("coSoID", coSoID)
            }
        }
    }
}