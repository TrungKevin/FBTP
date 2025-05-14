package com.trungkien.fbtp.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.trungkien.fbtp.R
import java.text.SimpleDateFormat
import java.util.*

data class CalendarDay(
    val date: String, // Format: "dd/MM/yyyy"
    val dayOfWeek: String, // e.g., "T2", "T3"
    val day: String, // e.g., "15"
    val month: String, // e.g., "Tháng 4"
    val isEnabled: Boolean = true // Whether the date is selectable (e.g., not in the past)
)

class CalendarDayAdapter(
    private var calendarDays: List<CalendarDay>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<CalendarDayAdapter.ViewHolder>() {

    private var selectedPosition: Int = 0 // Default to first item

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tv_day)
        val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        val tvMonth: TextView = itemView.findViewById(R.id.tv_month)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val calendarDay = calendarDays[position]
        holder.tvDay.text = calendarDay.dayOfWeek
        holder.tvDate.text = calendarDay.day
        holder.tvMonth.text = calendarDay.month

        // Handle enabled/disabled state
        if (!calendarDay.isEnabled) {
            holder.itemView.isEnabled = false
            holder.itemView.alpha = 0.5f
            holder.tvDay.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
            holder.tvDate.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
            holder.tvMonth.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.transparent))
            holder.tvDate.setBackgroundResource(0)
        } else {
            holder.itemView.isEnabled = true
            holder.itemView.alpha = 1f
            // Highlight selected date
            if (position == selectedPosition) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.colorPrimaryLight))
                holder.tvDate.setBackgroundResource(R.drawable.circle_date_background)
                holder.tvDate.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
                holder.tvDay.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textPrimary))
                holder.tvMonth.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
            } else {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, android.R.color.transparent))
                holder.tvDate.setBackgroundResource(0)
                holder.tvDate.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textPrimary))
                holder.tvDay.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
                holder.tvMonth.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.textSecondary))
            }
        }

        // Use holder.getAdapterPosition() in click listener
        holder.itemView.setOnClickListener {
            val currentPosition = holder.getAdapterPosition()
            if (currentPosition != RecyclerView.NO_POSITION && calendarDay.isEnabled && currentPosition != selectedPosition) {
                val previousPosition = selectedPosition
                selectedPosition = currentPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(currentPosition)
                onItemClick(calendarDay.date)
            }
        }
    }

    override fun getItemCount(): Int = calendarDays.size

    fun updateData(newCalendarDays: List<CalendarDay>) {
        calendarDays = newCalendarDays
        selectedPosition = if (newCalendarDays.isNotEmpty()) {
            // Select the first enabled date
            newCalendarDays.indexOfFirst { it.isEnabled }.coerceAtLeast(0)
        } else {
            -1
        }
        notifyDataSetChanged()
        // Trigger onItemClick for the default selected date
        if (selectedPosition >= 0 && selectedPosition < calendarDays.size) {
            onItemClick(calendarDays[selectedPosition].date)
        }
    }

    // Utility function to validate and create CalendarDay objects
    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun createCalendarDay(dateStr: String, isFutureOnly: Boolean = true): CalendarDay? {
            return try {
                val date = dateFormat.parse(dateStr) ?: return null
                val calendar = Calendar.getInstance().apply { time = date }
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val isEnabled = !isFutureOnly || !calendar.before(today)

                CalendarDay(
                    date = dateStr,
                    dayOfWeek = getDayOfWeek(calendar),
                    day = calendar.get(Calendar.DAY_OF_MONTH).toString(),
                    month = "Tháng ${calendar.get(Calendar.MONTH) + 1}",
                    isEnabled = isEnabled
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun getDayOfWeek(calendar: Calendar): String {
            return when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "T2"
                Calendar.TUESDAY -> "T3"
                Calendar.WEDNESDAY -> "T4"
                Calendar.THURSDAY -> "T5"
                Calendar.FRIDAY -> "T6"
                Calendar.SATURDAY -> "T7"
                Calendar.SUNDAY -> "CN"
                else -> ""
            }
        }
    }
}