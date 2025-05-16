package com.trungkien.fbtp

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.databinding.ItemPriceBoardBinding
import com.trungkien.fbtp.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class AddPriceBoardAdapter(
    private val context: Context,
    private val timeSlots: MutableList<TimeSlot>,
    private val onUpdateClick: (Int, TimeSlot) -> Unit,
    private val onDeleteClick: (Int) -> Unit,
    private val hideButtons: Boolean = false // Tham số để kiểm soát hiển thị nút và tiêu đề
) : RecyclerView.Adapter<AddPriceBoardAdapter.ViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private var isLoading = false

    inner class ViewHolder(private val binding: ItemPriceBoardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeSlot: TimeSlot, position: Int) {
            // Kiểm tra dữ liệu hợp lệ trước khi hiển thị
            if (!isValidTimeSlot(timeSlot)) {
                binding.txtSession.text = "N/A"
                binding.txtCourtSize.text = "N/A"
                binding.txtPeriod.text = "N/A"
                binding.txtPrice.text = "N/A"
                return
            }

            binding.txtSession.text = timeSlot.session
            binding.txtCourtSize.text = timeSlot.courtSize
            binding.txtPeriod.text = timeSlot.period
            // Format price with Vietnamese locale
            val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
            binding.txtPrice.text = "${formatter.format(timeSlot.price.toInt())} VNĐ"

            // Ẩn hoặc hiển thị nút và tiêu đề dựa trên hideButtons
            binding.btnUpDate.visibility = if (hideButtons) View.GONE else View.VISIBLE
            binding.btnDelete.visibility = if (hideButtons) View.GONE else View.VISIBLE
            binding.txtHeaderUpdate.visibility = if (hideButtons) View.GONE else View.VISIBLE
            binding.txtHeaderDelete.visibility = if (hideButtons) View.GONE else View.VISIBLE

            // Disable buttons during loading
            binding.btnUpDate.isEnabled = !isLoading
            binding.btnDelete.isEnabled = !isLoading

            binding.btnUpDate.setOnClickListener {
                if (isLoading) return@setOnClickListener // Prevent clicks during loading
                showUpdateDialog(position, timeSlot)
            }

            binding.btnDelete.setOnClickListener {
                if (isLoading) return@setOnClickListener // Prevent clicks during loading
                onDeleteClick(position)
            }
        }

        private fun isValidTimeSlot(timeSlot: TimeSlot): Boolean {
            return timeSlot.session.isNotEmpty() &&
                    timeSlot.courtSize.isNotEmpty() &&
                    timeSlot.period.isNotEmpty() &&
                    timeSlot.price > 0
        }

        private fun showUpdateDialog(position: Int, timeSlot: TimeSlot) {
            val dialog = Dialog(context)
            dialog.setContentView(R.layout.dialog_update_time_slot)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            val edtSession = dialog.findViewById<AutoCompleteTextView>(R.id.edt_session)
            val edtCourtSize = dialog.findViewById<AutoCompleteTextView>(R.id.edt_court_size)
            val edtPeriod = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edt_period)
            val edtPrice = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edt_price)
            val btnConfirm = dialog.findViewById<Button>(R.id.btn_confirm)
            val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

            // Kiểm tra dữ liệu timeSlot trước khi hiển thị dialog
            if (timeSlot.pricingID.isNullOrEmpty() || timeSlot.coSoID.isNullOrEmpty() || timeSlot.ownerID.isNullOrEmpty()) {
                Toast.makeText(context, "Dữ liệu khung giờ không hợp lệ, không thể cập nhật", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                return
            }

            // Setup session dropdown
            val sessions = arrayOf("Sáng", "Chiều", "Tối")
            val sessionAdapter = ArrayAdapter(context, R.layout.dropdown_menu_item, sessions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            edtSession.setAdapter(sessionAdapter)
            edtSession.setText(timeSlot.session, false)
            edtSession.setOnItemClickListener { _, _, pos, _ ->
                edtSession.setText(sessions[pos], false)
            }
            edtSession.setOnClickListener { edtSession.showDropDown() }
            edtSession.setOnTouchListener { _, _ -> edtSession.showDropDown(); false }

            // Setup court size dropdown
            CoroutineScope(Dispatchers.Main).launch {
                var courtType = "Football" // Giá trị mặc định
                try {
                    val courtSnapshot = withContext(Dispatchers.IO) {
                        firestore.collection("courts")
                            .whereEqualTo("coSoID", timeSlot.coSoID)
                            .limit(1)
                            .get()
                            .await()
                    }
                    val courtDoc = courtSnapshot.documents.firstOrNull()
                    courtType = courtDoc?.getString("sportType") ?: "Football"
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Lỗi khi tải thông tin sân: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                val courtSizes = when (courtType) {
                    "Football" -> listOf("5 vs 5", "7 vs 7", "11 vs 11")
                    "Badminton" -> listOf("Sân Đơn", "Sân Đôi")
                    "Tennis" -> listOf("Sân Đất Nện", "Sân Cỏ", "Sân Thảm")
                    "Pickleball" -> listOf("Sân Ngoài Trời", "Sân Trong Nhà")
                    else -> listOf(courtType)
                }
                val courtSizeAdapter = ArrayAdapter(context, R.layout.dropdown_menu_item, courtSizes).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                edtCourtSize.setAdapter(courtSizeAdapter)
                edtCourtSize.setText(timeSlot.courtSize, false)
                edtCourtSize.setOnItemClickListener { _, _, pos, _ ->
                    edtCourtSize.setText(courtSizes[pos], false)
                }
                edtCourtSize.setOnClickListener { edtCourtSize.showDropDown() }
                edtCourtSize.setOnTouchListener { _, _ -> edtCourtSize.showDropDown(); false }
            }

            // Setup period
            edtPeriod.setText(timeSlot.period)

            // Setup price
            edtPrice.setText(timeSlot.price.toInt().toString())

            btnConfirm.setOnClickListener {
                val session = edtSession.text.toString()
                val courtSize = edtCourtSize.text.toString()
                val period = edtPeriod.text.toString()
                val priceText = edtPrice.text.toString()
                val price = priceText.toDoubleOrNull() ?: 0.0

                // Validate inputs
                if (session.isEmpty()) {
                    Toast.makeText(context, "Vui lòng chọn buổi", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (courtSize.isEmpty()) {
                    Toast.makeText(context, "Vui lòng chọn cỡ sân", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (period.isEmpty()) {
                    Toast.makeText(context, "Vui lòng nhập thời gian", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (priceText.isEmpty() || price <= 0) {
                    Toast.makeText(context, "Vui lòng nhập giá hợp lệ", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updatedTimeSlot = timeSlot.copy(
                    session = session,
                    courtSize = courtSize,
                    period = period,
                    price = price
                )
                onUpdateClick(position, updatedTimeSlot)
                dialog.dismiss()
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPriceBoardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(timeSlots[position], position)
    }

    override fun getItemCount(): Int = timeSlots.size

    // New method to toggle loading state
    fun setLoading(loading: Boolean) {
        isLoading = loading
        notifyDataSetChanged() // Refresh to update button states
    }
}