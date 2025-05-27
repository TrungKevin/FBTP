package com.trungkien.fbtp.renter.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.FragmentFirstBinding
import com.trungkien.fbtp.renter.activity.FindFootballActivity
import com.trungkien.fbtp.renter.activity.FindBadmintonActivity
import com.trungkien.fbtp.renter.activity.FindTennisActivity
import com.trungkien.fbtp.renter.activity.FindPickleballActivity
import com.trungkien.fbtp.renter.activity.ItemDetailUserActivity
import com.trungkien.fbtp.Adapter.UploadAdapter
import com.trungkien.fbtp.model.Court
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.renter.activity.DatLichActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var uploadAdapter: UploadAdapter
    private var allFacilities: MutableList<SportFacility> = mutableListOf() // Updated to MutableList
    private var loadFacilitiesJob: Job? = null

    companion object {
        private const val TAG = "FirstFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register broadcast receiver for profile image updates
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.trungkien.fbtp.PROFILE_IMAGE_UPDATED") {
                    Log.d(TAG, "Received PROFILE_IMAGE_UPDATED broadcast")
                    notifyProfileImageUpdate()
                }
            }
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            receiver,
            IntentFilter("com.trungkien.fbtp.PROFILE_IMAGE_UPDATED")
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize RecyclerView and Adapter
        uploadAdapter = UploadAdapter(
            mutableListOf(),
            userRole = "renter",
            onItemClick = { sportFacility ->
                val intent = Intent(requireContext(), ItemDetailUserActivity::class.java).apply {
                    putExtra("coSoID", sportFacility.coSoID)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            },
            onBookClick = { sportFacility ->
                handleBookClick(sportFacility)
            },
            onNotificationClick = {}
        )
        binding.rcvTongHop.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = uploadAdapter
        }

        // Load all facilities
        loadAllFacilities()

        // Setup search functionality
        binding.edtTimKiem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                filterFacilities(query)
            }
        })

        // Handle clicks for sport-specific buttons
        binding.imgbtnFB.setOnClickListener {
            val intent = Intent(requireContext(), FindFootballActivity::class.java)
            startActivity(intent)
        }

        binding.imgBtnBMT.setOnClickListener {
            val intent = Intent(requireContext(), FindBadmintonActivity::class.java)
            startActivity(intent)
        }

        binding.imgBtnTN.setOnClickListener {
            val intent = Intent(requireContext(), FindTennisActivity::class.java)
            startActivity(intent)
        }

        binding.imgBtnPKB.setOnClickListener {
            val intent = Intent(requireContext(), FindPickleballActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleBookClick(sportFacility: SportFacility) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Fetch courts for the facility
                val snapshot = db.collection("courts")
                    .whereEqualTo("coSoID", sportFacility.coSoID)
                    .get()
                    .await()
                val courts = snapshot.toObjects(Court::class.java)

                if (courts.isEmpty()) {
                    Toast.makeText(requireContext(), "Không có sân nào khả dụng", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // If multiple courts, prompt user to select court type
                if (courts.size > 1) {
                    val courtTypes = courts.map { it.size }.distinct().toTypedArray()
                    AlertDialog.Builder(requireContext())
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
                    // Single court, proceed directly
                    val court = courts.first()
                    startBookingActivity(sportFacility.coSoID, court.courtID, court.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching courts: ${e.message}", e)
                Toast.makeText(requireContext(), "Lỗi khi tải danh sách sân: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startBookingActivity(coSoID: String, courtID: String, courtType: String) {
        val intent = Intent(requireContext(), DatLichActivity::class.java).apply {
            putExtra("coSoID", coSoID)
            putExtra("courtID", courtID)
            putExtra("courtType", courtType)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadAllFacilities()
    }

    private fun loadAllFacilities() {
        loadFacilitiesJob?.cancel()
        loadFacilitiesJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                _binding?.rcvTongHop?.visibility = View.GONE

                val snapshot = db.collection("sport_facilities")
                    .get()
                    .await()
                allFacilities.clear()
                allFacilities.addAll(snapshot.toObjects(SportFacility::class.java))

                _binding?.let { binding ->
                    if (allFacilities.isEmpty()) {
                        Toast.makeText(requireContext(), "Không có sân nào được tìm thấy", Toast.LENGTH_SHORT).show()
                        binding.rcvTongHop.visibility = View.GONE
                    } else {
                        uploadAdapter.updateData(allFacilities)
                        binding.rcvTongHop.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading facilities: ${e.message}", e)
                _binding?.let { binding ->
                    binding.rcvTongHop.visibility = View.GONE
                }
            }
        }
    }

    private fun filterFacilities(query: String) {
        if (query.isEmpty()) {
            _binding?.let { binding ->
                uploadAdapter.updateData(allFacilities)
                binding.rcvTongHop.visibility = if (allFacilities.isEmpty()) View.GONE else View.VISIBLE
            }
            return
        }

        val filteredFacilities = allFacilities.filter { facility ->
            facility.name.contains(query, ignoreCase = true)
        }.sortedBy { facility ->
            val name = facility.name.lowercase()
            val queryLower = query.lowercase()
            when {
                name.startsWith(queryLower) -> 0
                name.contains(queryLower) -> 1
                else -> 2
            }
        }

        _binding?.let { binding ->
            if (filteredFacilities.isEmpty()) {
                Toast.makeText(requireContext(), "Không tìm thấy sân phù hợp", Toast.LENGTH_SHORT).show()
                binding.rcvTongHop.visibility = View.GONE
            } else {
                uploadAdapter.updateData(filteredFacilities)
                binding.rcvTongHop.visibility = View.VISIBLE
            }
        }
    }

    private fun notifyProfileImageUpdate() {
        Log.d(TAG, "Notifying adapter of profile image update, facilities count: ${allFacilities.size}")
        uploadAdapter.updateData(allFacilities)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadFacilitiesJob?.cancel()
        _binding = null
    }
}