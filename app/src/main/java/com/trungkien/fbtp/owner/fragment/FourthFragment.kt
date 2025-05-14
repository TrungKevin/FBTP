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
    private var courtID: String = UUID.randomUUID().toString()
    private var courtType: String = "Football"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFourthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check authentication state
        if (auth.currentUser == null) {
            Toast.makeText(context, "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
            requireActivity().finish()
            return
        }

        coSoID = arguments?.getString("coSoID") ?: ""
        lifecycleScope.launch {
            try {
                if (coSoID.isEmpty()) {
                    // Fetch a coSoID if none provided
                    val snapshot = firestore.collection("sport_facilities")
                        .whereEqualTo("ownerID", auth.currentUser?.uid)
                        .limit(1)
                        .get()
                        .await()
                    coSoID = snapshot.documents.firstOrNull()?.id ?: ""
                    if (coSoID.isEmpty()) {
                        Toast.makeText(context, "Không tìm thấy sân nào. Vui lòng tạo sân.", Toast.LENGTH_LONG).show()
                        binding.root.isEnabled = false
                        return@launch
                    }
                }
                // Fetch courtID and courtType
                val courtSnapshot = firestore.collection("courts")
                    .whereEqualTo("coSoID", coSoID)
                    .limit(1)
                    .get()
                    .await()
                val courtDoc = courtSnapshot.documents.firstOrNull()
                if (courtDoc != null) {
                    courtID = courtDoc.id
                    courtType = courtDoc.getString("sportType") ?: "Football"
                } else {
                    // Fallback: Fetch any court owned by user
                    val fallbackSnapshot = firestore.collection("courts")
                        .whereEqualTo("ownerID", auth.currentUser?.uid)
                        .limit(1)
                        .get()
                        .await()
                    val fallbackCourt = fallbackSnapshot.documents.firstOrNull()
                    if (fallbackCourt != null) {
                        courtID = fallbackCourt.id
                        courtType = fallbackCourt.getString("sportType") ?: "Football"
                        coSoID = fallbackCourt.getString("coSoID") ?: coSoID
                    } else {
                        Toast.makeText(context, "Không tìm thấy sân nào. Vui lòng tạo sân.", Toast.LENGTH_LONG).show()
                        binding.root.isEnabled = false
                        return@launch
                    }
                }
                setupFragment()
            } catch (e: Exception) {
                Log.e("FourthFragment", "Error fetching court info", e)
                Toast.makeText(context, "Lỗi khi tải thông tin sân: ${e.message}", Toast.LENGTH_LONG).show()
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
                lifecycleScope.launch {
                    try {
                        // Kiểm tra dữ liệu hợp lệ
                        if (updatedTimeSlot.pricingID.isNullOrEmpty() || updatedTimeSlot.coSoID.isNullOrEmpty() || updatedTimeSlot.ownerID.isNullOrEmpty()) {
                            Log.e("FourthFragment", "Invalid time slot data: pricingID=${updatedTimeSlot.pricingID}, coSoID=${updatedTimeSlot.coSoID}, ownerID=${updatedTimeSlot.ownerID}")
                            Toast.makeText(context, "Dữ liệu khung giờ không hợp lệ", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (updatedTimeSlot.session.isEmpty() || updatedTimeSlot.courtSize.isEmpty() || updatedTimeSlot.period.isEmpty() || updatedTimeSlot.price <= 0) {
                            Log.e("FourthFragment", "Invalid time slot fields: session=${updatedTimeSlot.session}, courtSize=${updatedTimeSlot.courtSize}, period=${updatedTimeSlot.period}, price=${updatedTimeSlot.price}")
                            Toast.makeText(context, "Vui lòng điền đầy đủ thông tin khung giờ", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // Cập nhật Firestore và danh sách cục bộ
                        updateTimeSlotInFirestore(updatedTimeSlot, timeSlots[position])
                        // Danh sách timeSlots sẽ được cập nhật qua listenToFirestoreChanges
                        priceBoardAdapter.notifyItemChanged(position)
                        Toast.makeText(context, "Cập nhật khung giờ thành công trên tất cả sân", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("FourthFragment", "Error updating time slot", e)
                        Toast.makeText(context, "Lỗi khi cập nhật khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteClick = { position ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn chắc chắn muốn xóa khung giờ này?")
                    .setPositiveButton("Có") { _, _ ->
                        val timeSlot = timeSlots[position]
                        lifecycleScope.launch {
                            try {
                                deleteTimeSlotFromFirestore(timeSlot)
                                // Danh sách timeSlots sẽ được cập nhật qua listenToFirestoreChanges
                                priceBoardAdapter.notifyItemRemoved(position)
                                Toast.makeText(context, "Xóa khung giờ thành công", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("FourthFragment", "Error deleting time slot", e)
                                Toast.makeText(context, "Lỗi khi xóa khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
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
            val timeSlot = createTimeSlotFromInputs()
            if (timeSlot != null) {
                lifecycleScope.launch {
                    try {
                        timeSlots.add(timeSlot)
                        saveTimeSlotToFirestore(timeSlot)
                        priceBoardAdapter.notifyItemInserted(timeSlots.size - 1)
                        clearInputFields()
                        Toast.makeText(context, "Cập nhật bảng giá giờ thành công", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("FourthFragment", "Error saving time slot", e)
                        Toast.makeText(context, "Lỗi khi lưu khung giờ: ${e.message}", Toast.LENGTH_LONG).show()
                        timeSlots.remove(timeSlot) // Roll back UI change on failure
                        priceBoardAdapter.notifyItemRemoved(timeSlots.size)
                    }
                }
            }
        }
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

        return TimeSlot(
            scheduleID = null,
            price = price,
            courtSize = courtSize,
            period = period,
            session = session,
            isTimeRange = true,
            courtID = courtID,
            pricingID = UUID.randomUUID().toString(),
            ownerID = auth.currentUser?.uid ?: "",
            coSoID = coSoID
        )
    }

    private fun saveOpeningHoursToFacility(openingHours: String) {
        lifecycleScope.launch {
            try {
                // Get all facilities owned by the user
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", auth.currentUser?.uid)
                    .get()
                    .await()

                if (facilitiesSnapshot.isEmpty) {
                    Log.w("FourthFragment", "No facilities found for ownerID: ${auth.currentUser?.uid}")
                    return@launch
                }

                // Create a batch to update all facilities
                val batch: WriteBatch = firestore.batch()
                facilitiesSnapshot.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf("openingHours" to openingHours)
                    )
                }

                // Commit the batch
                batch.commit().await()
                Log.d("FourthFragment", "Updated opening hours: $openingHours for all facilities")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Error updating opening hours: ${e.message}", e)
                Toast.makeText(context, "Lỗi khi lưu giờ hoạt động: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTimeSlotToFirestore(timeSlot: TimeSlot) {
        lifecycleScope.launch {
            try {
                val facilitiesSnapshot = firestore.collection("sport_facilities")
                    .whereEqualTo("ownerID", auth.currentUser?.uid)
                    .get()
                    .await()
                val coSoIDs = facilitiesSnapshot.documents.map { it.id }

                coSoIDs.forEach { facilityID ->
                    val timeSlotForFacility = timeSlot.copy(
                        pricingID = UUID.randomUUID().toString(),
                        coSoID = facilityID
                    )
                    firestore.collection("timeSlots")
                        .document(timeSlotForFacility.pricingID)
                        .set(timeSlotForFacility)
                        .await()
                    Log.d("FourthFragment", "Saved time slot: ${timeSlotForFacility.pricingID} for coSoID: $facilityID")
                }
            } catch (e: Exception) {
                throw e // Let the caller handle the exception
            }
        }
    }

    private fun updateTimeSlotInFirestore(updatedTimeSlot: TimeSlot, originalTimeSlot: TimeSlot) {
        lifecycleScope.launch {
            try {
                // Kiểm tra quyền sở hữu
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("FourthFragment", "No authenticated user")
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
                for (facilityID in coSoIDs) {
                    val snapshot = firestore.collection("timeSlots")
                        .whereEqualTo("coSoID", facilityID)
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
                            coSoID = facilityID
                        )
                        batch.set(
                            firestore.collection("timeSlots").document(doc.id),
                            newTimeSlot
                        )
                        Log.d("FourthFragment", "Updating time slot: ${doc.id} for coSoID: $facilityID")
                    }
                }

                // Commit batch
                batch.commit().await()
                Log.d("FourthFragment", "Updated time slot across all facilities for ownerID: $currentUid")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Error updating time slot: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền cập nhật khung giờ"
                    e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                    else -> "Lỗi khi cập nhật khung giờ: ${e.message}"
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
                    Log.e("FourthFragment", "No authenticated user")
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
                for (facilityID in coSoIDs) {
                    val snapshot = firestore.collection("timeSlots")
                        .whereEqualTo("coSoID", facilityID)
                        .whereEqualTo("ownerID", currentUid)
                        .whereEqualTo("session", timeSlot.session)
                        .whereEqualTo("courtSize", timeSlot.courtSize)
                        .whereEqualTo("period", timeSlot.period)
                        .whereEqualTo("price", timeSlot.price)
                        .get()
                        .await()

                    for (doc in snapshot.documents) {
                        batch.delete(firestore.collection("timeSlots").document(doc.id))
                        Log.d("FourthFragment", "Deleting time slot: ${doc.id} for coSoID: $facilityID")
                    }
                }

                // Commit batch
                batch.commit().await()
                Log.d("FourthFragment", "Deleted time slot across all facilities for ownerID: $currentUid")
            } catch (e: Exception) {
                Log.e("FourthFragment", "Error deleting time slot: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền xóa khung giờ"
                    e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                    else -> "Lỗi khi xóa khung giờ: ${e.message}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun listenToFirestoreChanges() {
        if (auth.currentUser == null) {
            Log.w("FourthFragment", "No authenticated user, skipping Firestore listener")
            return
        }
        firestore.collection("timeSlots")
            .whereEqualTo("ownerID", auth.currentUser?.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FourthFragment", "Error listening to Firestore: ${error.message}")
                    Toast.makeText(context, "Lỗi khi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    timeSlots.clear()
                    val uniqueTimeSlots = mutableMapOf<String, TimeSlot>()
                    for (doc in snapshot.documents) {
                        val timeSlot = doc.toObject(TimeSlot::class.java) ?: continue
                        // Tạo key duy nhất dựa trên session, courtSize, period, price
                        val key = "${timeSlot.session}_${timeSlot.courtSize}_${timeSlot.period}_${timeSlot.price}"
                        if (!uniqueTimeSlots.containsKey(key)) {
                            uniqueTimeSlots[key] = timeSlot
                        }
                    }
                    timeSlots.addAll(uniqueTimeSlots.values)
                    priceBoardAdapter.notifyDataSetChanged()
                    Log.d("FourthFragment", "Loaded ${timeSlots.size} unique time slots for ownerID: ${auth.currentUser?.uid}")
                }
            }
    }

    private fun clearInputFields() {
        binding.edtSession.setText("")
        binding.edtCourtSize.setText("")
        binding.edtPeriod.setText("")
        binding.edtPrice.setText("")
        binding.edtGioHoatDong.setText("") // Clear opening hours input
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