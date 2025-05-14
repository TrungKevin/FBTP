package com.trungkien.fbtp.owner.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.trungkien.fbtp.R
import com.trungkien.fbtp.owner.activity.ItemDetailOwnerActivity
import com.trungkien.fbtp.owner.activity.UploadInfoActivity
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.Adapter.UploadAdapter
import com.trungkien.fbtp.model.SportFacility
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ThirdFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var initialLayout: LinearLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var uploadAdapter: UploadAdapter
    private var lastClickTime = 0L // For click debouncing
    private val debounceDuration = 500L // 500ms debounce
    private var listenerJob: Job? = null // Track snapshot listener

    companion object {
        private const val TAG = "ThirdFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_third, container, false)

        fab = view.findViewById(R.id.bnt_Add)
        recyclerView = view.findViewById(R.id.rcv_upLoad_owner)
        initialLayout = view.findViewById(R.id.initialLayout)
        contentLayout = view.findViewById(R.id.contentLayout)
        progressBar = view.findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        if (!::uploadAdapter.isInitialized) {
            uploadAdapter = UploadAdapter(mutableListOf()) { sportFacility ->
                // Debounce item clicks
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime > debounceDuration) {
                    lastClickTime = currentTime
                    val intent = Intent(requireContext(), ItemDetailOwnerActivity::class.java).apply {
                        putExtra("coSoID", sportFacility.coSoID)
                        if (sportFacility.images.isNotEmpty()) {
                            putExtra("image", sportFacility.images[0])
                        }
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                }
            }
            recyclerView.adapter = uploadAdapter
        }

        fab.setOnClickListener {
            startActivity(Intent(requireContext(), UploadInfoActivity::class.java))
        }

        setupSnapshotListener()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Only set up listener if not active
        if (listenerJob == null || listenerJob?.isCancelled == true) {
            setupSnapshotListener()
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel listener to prevent memory leaks
        listenerJob?.cancel()
        listenerJob = null
    }

    private fun setupSnapshotListener() {
        val userId = auth.currentUser?.uid ?: run {
            Log.e(TAG, "User not authenticated")
            Toast.makeText(requireContext(), "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), AccountActivity::class.java))
            requireActivity().finish()
            return
        }

        listenerJob?.cancel() // Cancel any existing listener
        listenerJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                db.collection("sport_facilities")
                    .whereEqualTo("ownerID", userId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                            if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Toast.makeText(requireContext(), "Bạn không có quyền truy cập dữ liệu. Kiểm tra quy tắc bảo mật.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(requireContext(), "Lỗi khi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                            updateUI(emptyList())
                            return@addSnapshotListener
                        }

                        if (snapshot == null) {
                            Log.w(TAG, "Snapshot is null")
                            updateUI(emptyList())
                            return@addSnapshotListener
                        }

                        // Deduplicate facilities by coSoID
                        val facilitiesMap = mutableMapOf<String, SportFacility>()
                        for (doc in snapshot.documents) {
                            val facility = doc.toObject(SportFacility::class.java)?.copy(coSoID = doc.id)
                            if (facility != null && facility.coSoID.isNotEmpty()) {
                                facilitiesMap[facility.coSoID] = facility
                            }
                        }
                        val facilities = facilitiesMap.values.toList()

                        Log.d(TAG, "Loaded ${facilities.size} unique sport facilities for userId: $userId")
                        updateUI(facilities)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up snapshot listener: ${e.message}", e)
                Toast.makeText(requireContext(), "Lỗi khi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
                updateUI(emptyList())
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateUI(facilities: List<SportFacility>) {
        if (!isAdded || view == null) {
            Log.w(TAG, "Fragment not attached or view is null, skipping UI update")
            return
        }
        if (facilities.isEmpty()) {
            initialLayout.visibility = View.VISIBLE
            contentLayout.visibility = View.GONE
            updateFabPosition(isCentered = true)
        } else {
            initialLayout.visibility = View.GONE
            contentLayout.visibility = View.VISIBLE
            uploadAdapter.updateData(facilities)
            updateFabPosition(isCentered = false)
        }
    }

    private fun updateFabPosition(isCentered: Boolean) {
        val params = fab.layoutParams as CoordinatorLayout.LayoutParams

        if (isCentered) {
            params.anchorId = R.id.initialLayout
            params.anchorGravity = Gravity.CENTER
            params.gravity = Gravity.CENTER
            params.leftMargin = 0
            params.topMargin = 0
            params.rightMargin = 0
            params.bottomMargin = 0
            fab.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        } else {
            params.anchorId = View.NO_ID
            params.anchorGravity = Gravity.NO_GRAVITY
            params.gravity = Gravity.START or Gravity.TOP
            params.leftMargin = 16
            params.topMargin = 16
            params.rightMargin = 0
            params.bottomMargin = 0
            fab.animate().scaleX(0.8f).scaleY(0.8f).setDuration(300).start()
        }

        fab.layoutParams = params
    }

    fun updateFacilityImage(coSoID: String, imageBase64: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val facilityRef = db.collection("sport_facilities").document(coSoID)
                facilityRef.update("images", listOf(imageBase64)).await()
                // No need to reload manually; snapshot listener will handle updates
            } catch (e: Exception) {
                Log.e(TAG, "Error updating facility image: ${e.message}", e)
            }
        }
    }
}