package com.trungkien.fbtp.owner.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.R
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.owner.activity.ItemDetailOwnerActivity
import com.trungkien.fbtp.owner.activity.UploadInfoActivity
import com.trungkien.fbtp.Adapter.UploadAdapter
import com.trungkien.fbtp.model.SportFacility
import com.trungkien.fbtp.owner.activity.NotificationActivity
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
    private var lastClickTime = 0L
    private val debounceDuration = 500L
    private var listenerJob: Job? = null
    private val facilities: MutableList<SportFacility> = mutableListOf() // Added class-level property//****

    companion object {
        private const val TAG = "ThirdFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {//****
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
    ): View? {
        val view = inflater.inflate(R.layout.fragment_third, container, false)

        fab = view.findViewById(R.id.bnt_Add)
        recyclerView = view.findViewById(R.id.rcv_upLoad_owner)
        initialLayout = view.findViewById(R.id.initialLayout)
        contentLayout = view.findViewById(R.id.contentLayout)
        progressBar = view.findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        if (!::uploadAdapter.isInitialized) {
            uploadAdapter = UploadAdapter(
                mutableListOf(),
                userRole = "owner",
                onItemClick = { sportFacility ->
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
                },
                onBookClick = { /* No-op: Owners don't book facilities */ },
                onNotificationClick = { sportFacility ->
                     val debounceDuration = 500L
                        val currentTime = System.currentTimeMillis()
                        if (currentTime  > debounceDuration) {
                            Log.d(TAG, "Notification button clicked for facility: ${sportFacility.coSoID}")
                            if (!isNetworkAvailable(requireContext())) {
                                Toast.makeText(context, "Không có kết nối mạng", Toast.LENGTH_SHORT).show()
                            }
                            try {
                                if (sportFacility.coSoID.isNotEmpty()) {
                                    val intent = Intent(activity, NotificationActivity::class.java).apply {
                                        putExtra("coSoID", sportFacility.coSoID)
                                    }
                                    requireContext().startActivity(intent)
                                } else {
                                    Log.e(TAG, "coSoID is empty for facility: ${sportFacility.name}")
                                    Toast.makeText(requireContext(), "Dữ liệu cơ sở không hợp lệ", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting NotificationActivity: ${e.message}", e)
                                Toast.makeText(requireContext(), "Lỗi hệ thống, vui lòng thử lại", Toast.LENGTH_SHORT).show()
                            }
                        }

                }

            )
            recyclerView.adapter = uploadAdapter
        }

        fab.setOnClickListener {
            startActivity(Intent(requireContext(), UploadInfoActivity::class.java))
        }

        setupSnapshotListener()

        return view
    }

    override fun onResume() {//****
        super.onResume()
        if (listenerJob == null || listenerJob?.isCancelled == true) {
            setupSnapshotListener()
        }
    }
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    override fun onPause() {
        super.onPause()
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

        listenerJob?.cancel()
        listenerJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                db.collection("sport_facilities")
                    .whereEqualTo("ownerID", userId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                            Toast.makeText(requireContext(), "Lỗi khi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                            updateUI(emptyList())
                            return@addSnapshotListener
                        }

                        if (snapshot == null) {
                            Log.w(TAG, "Snapshot is null")
                            updateUI(emptyList())
                            return@addSnapshotListener
                        }

                        val facilitiesMap = mutableMapOf<String, SportFacility>()
                        for (doc in snapshot.documents) {
                            val facility = doc.toObject(SportFacility::class.java)?.copy(coSoID = doc.id)
                            if (facility != null && facility.coSoID.isNotEmpty()) {
                                facilitiesMap[facility.coSoID] = facility
                            } else {
                                Log.w(TAG, "Invalid facility: coSoID is empty for document ${doc.id}")
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

    override fun onDestroy() {
        super.onDestroy()
        Glide.get(requireContext()).clearMemory()
    }

    private fun updateUI(facilities: List<SportFacility>) {
        if (!isAdded || view == null) {
            Log.w(TAG, "Fragment not attached or view is null, skipping UI update")
            return
        }
        this.facilities.clear() // Clear old data
        this.facilities.addAll(facilities) // Update class-level property
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
            } catch (e: Exception) {
                Log.e(TAG, "Error updating facility image: ${e.message}", e)
            }
        }
    }

    fun notifyProfileImageUpdate() {//****
        Log.d(TAG, "Notifying adapter of profile image update, facilities count: ${facilities.size}")
        uploadAdapter.updateData(facilities)
    }
}