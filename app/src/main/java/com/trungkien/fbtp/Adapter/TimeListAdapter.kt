package com.trungkien.fbtp.adapter

import android.app.Dialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.ItemPriceBoardBinding
import com.trungkien.fbtp.model.TimeSlot
import java.util.regex.Pattern

class TimeListAdapter(
    private val timeSlots: MutableList<TimeSlot>,
    private val onUpdateClick: (Int, TimeSlot) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<TimeListAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemPriceBoardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeSlot: TimeSlot, position: Int) {
            binding.txtSession.text = timeSlot.session
            binding.txtCourtSize.text = timeSlot.courtSize
            binding.txtPeriod.text = timeSlot.period
            binding.txtPrice.text = "${timeSlot.price.toInt()} VNĐ"

            binding.btnUpDate.setOnClickListener {
                showUpdateDialog(position, timeSlot)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(position)
            }
        }

        private fun showUpdateDialog(position: Int, timeSlot: TimeSlot) {
            val dialog = Dialog(binding.root.context)
            dialog.setContentView(R.layout.dialog_update_time_slot)
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            val edtSession = dialog.findViewById<AutoCompleteTextView>(R.id.edt_session)
            val edtCourtSize = dialog.findViewById<AutoCompleteTextView>(R.id.edt_court_size)
            val edtPeriod = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edt_period)
            val edtPrice = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edt_price)
            val btnConfirm = dialog.findViewById<Button>(R.id.btn_confirm)
            val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)

            // Setup session dropdown
            val sessions = arrayOf("Sáng", "Chiều", "Tối")
            val sessionAdapter = ArrayAdapter(binding.root.context, R.layout.dropdown_menu_item, sessions).apply {
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
            val courtSizes = arrayOf("5vs5", "7vs7", "11vs11")
            val courtSizeAdapter = ArrayAdapter(binding.root.context, R.layout.dropdown_menu_item, courtSizes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            edtCourtSize.setAdapter(courtSizeAdapter)
            edtCourtSize.setText(timeSlot.courtSize, false)
            edtCourtSize.setOnItemClickListener { _, _, pos, _ ->
                edtCourtSize.setText(courtSizes[pos], false)
            }
            edtCourtSize.setOnClickListener { edtCourtSize.showDropDown() }
            edtCourtSize.setOnTouchListener { _, _ -> edtCourtSize.showDropDown(); false }

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
                    Toast.makeText(binding.root.context, "Vui lòng chọn buổi", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (courtSize.isEmpty()) {
                    Toast.makeText(binding.root.context, "Vui lòng chọn cỡ sân", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val periodPattern = Pattern.compile("^\\d+ giờ( \\d+ phút)?$")
                if (period.isEmpty() || !periodPattern.matcher(period).matches()) {
                    Toast.makeText(binding.root.context, "Vui lòng nhập thời gian hợp lệ (VD: 1 giờ, 1 giờ 30 phút)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (priceText.isEmpty() || price <= 0) {
                    Toast.makeText(binding.root.context, "Vui lòng nhập giá hợp lệ", Toast.LENGTH_SHORT).show()
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
}