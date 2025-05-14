package com.trungkien.fbtp.renter.fragment

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import com.trungkien.fbtp.model.SportFacility
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var uploadAdapter: UploadAdapter
    private var allFacilities: List<SportFacility> = emptyList()
    private var loadFacilitiesJob: Job? = null

    companion object {
        private const val TAG = "FirstFragment"
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
        uploadAdapter = UploadAdapter(mutableListOf()) { sportFacility ->
            val intent = Intent(requireContext(), ItemDetailUserActivity::class.java).apply {
                putExtra("coSoID", sportFacility.coSoID)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
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

        // Xử lý khi nhấp vào nút bóng đá
        binding.imgbtnFB.setOnClickListener {
            val intent = Intent(requireContext(), FindFootballActivity::class.java)
            startActivity(intent)
        }

        // Xử lý khi nhấp vào nút cầu lông
        binding.imgBtnBMT.setOnClickListener {
            val intent = Intent(requireContext(), FindBadmintonActivity::class.java)
            startActivity(intent)
        }

        // Xử lý khi nhấp vào nút quần vợt
        binding.imgBtnTN.setOnClickListener {
            val intent = Intent(requireContext(), FindTennisActivity::class.java)
            startActivity(intent)
        }

        // Xử lý khi nhấp vào nút pickleball
        binding.imgBtnPKB.setOnClickListener {
            val intent = Intent(requireContext(), FindPickleballActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh facilities list when fragment resumes
        loadAllFacilities()
    }

    private fun loadAllFacilities() {
        // Cancel previous job to prevent duplicate coroutines
        loadFacilitiesJob?.cancel()
        loadFacilitiesJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Only update UI if binding is available
                _binding?.rcvTongHop?.visibility = View.GONE // Safe access

                val snapshot = db.collection("sport_facilities")
                    .get()
                    .await()
                allFacilities = snapshot.toObjects(SportFacility::class.java)

                // Update UI only if binding is available
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
                // Show error only if binding is available
                _binding?.let { binding ->
//                    Toast.makeText(requireContext(), "Lỗi khi tải danh sách sân: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.rcvTongHop.visibility = View.GONE
                }
            }
        }
    }

    private fun filterFacilities(query: String) {
        if (query.isEmpty()) {
            // Restore full list when query is empty
            _binding?.let { binding ->
                uploadAdapter.updateData(allFacilities)
                binding.rcvTongHop.visibility = if (allFacilities.isEmpty()) View.GONE else View.VISIBLE
            }
            return
        }

        // Filter and sort facilities
        val filteredFacilities = allFacilities.filter { facility ->
            facility.name.contains(query, ignoreCase = true)
        }.sortedBy { facility ->
            // Sort by similarity: prioritize prefix matches, then partial matches
            val name = facility.name.lowercase()
            val queryLower = query.lowercase()
            when {
                name.startsWith(queryLower) -> 0 // Highest priority for prefix matches
                name.contains(queryLower) -> 1 // Lower priority for partial matches
                else -> 2 // Shouldn't happen due to filter
            }
        }

        // Update RecyclerView
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

    override fun onDestroyView() {
        super.onDestroyView()
        loadFacilitiesJob?.cancel() // Cancel coroutine to prevent leaks
        _binding = null
    }
}