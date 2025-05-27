package com.trungkien.fbtp.renter.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.MainActivity
import com.trungkien.fbtp.R
import com.trungkien.fbtp.Adapter.BookingAdapter
import com.trungkien.fbtp.databinding.FragmentSecondBinding
import com.trungkien.fbtp.model.Booking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookingAdapter: BookingAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var bookingListener: ListenerRegistration? = null
    private var isLoading = false
    private var originalPhone: String? = null
    private var isPhoneVisible: Boolean = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageUri(uri)
            } ?: Toast.makeText(requireContext(), "Không chọn được ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            Toast.makeText(requireContext(), "Quyền truy cập thư viện bị từ chối", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize RecyclerView and Adapter
        bookingAdapter = BookingAdapter(emptyList(), coroutineScope)
        binding.rvBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookingAdapter
        }

        // Disable interactions during loading
        setInteractionEnabled(false)

        // Load user information
        loadUserInformation()

        // Handle phone visibility toggle
        binding.tilSdtFrag2.setEndIconOnClickListener {
            togglePhoneVisibility()
        }

        // Handle change password button
        binding.btnChangePasswordRenter.setOnClickListener {
            if (!isLoading) {
                if (binding.layoutChangePassword.visibility == View.VISIBLE) {
                    binding.layoutChangePassword.visibility = View.GONE
                } else {
                    binding.layoutChangePassword.visibility = View.VISIBLE
                    binding.txtOldPassword.text?.clear()
                    binding.txtNewPassword.text?.clear()
                    binding.txtConfirmPassword.text?.clear()
                }
            }
        }

        // Handle save password button
        binding.btnSavePassword.setOnClickListener {
            if (!isLoading) {
                changePassword()
            }
        }

        // Handle avatar camera button
        binding.imgCameraRenter.setOnClickListener {
            if (!isLoading) {
                requestImagePermission()
            }
        }

        // Handle logout button
        binding.btnLogoutRenter.setOnClickListener {
            if (!isLoading) {
                auth.signOut()
                val sharedPref = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                sharedPref.edit().clear().apply()
                val activity = requireActivity() as? MainActivity
                activity?.findViewById<View>(R.id.bottomNavigationRenter)?.visibility = View.GONE
                val intent = Intent(requireContext(), AccountActivity::class.java)
                startActivity(intent)
                requireActivity().finish()
            }
        }

        // Load user-specific bookings with real-time listener
        setupBookingListener()
    }

    private fun loadUserInformation() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                        startActivity(Intent(requireContext(), AccountActivity::class.java))
                        requireActivity().finish()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.edtUsernameRenter.isEnabled = false
                    binding.edtGmailRenter.isEnabled = false
                    binding.edtSdtRenter.isEnabled = false
                }

                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                if (userDoc.exists()) {
                    val username = userDoc.getString("username") ?: "Không có tên"
                    val email = userDoc.getString("email") ?: currentUser.email ?: "Không có email"
                    val phone = userDoc.getString("phone") ?: "Không có số điện thoại"
                    val profileImageUrl = userDoc.getString("profileImageUrl") ?: ""

                    withContext(Dispatchers.Main) {
                        binding.edtUsernameRenter.setText(username)
                        binding.edtGmailRenter.setText(email)
                        originalPhone = phone
                        val phoneLength = phone.length
                        binding.edtSdtRenter.setText("*".repeat(phoneLength))
                        binding.tilSdtFrag2.setEndIconDrawable(R.drawable.ic_eye_closed)
                        binding.edtUsernameRenter.isEnabled = false
                        binding.edtGmailRenter.isEnabled = false
                        binding.edtSdtRenter.isEnabled = false

                        if (profileImageUrl.isNotEmpty()) {
                            try {
                                val decodedBytes = Base64.decode(profileImageUrl, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                binding.imgAvatarRenter.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                binding.imgAvatarRenter.setImageResource(R.drawable.ic_profile)
                            }
                        } else {
                            binding.imgAvatarRenter.setImageResource(R.drawable.ic_profile)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Không tìm thấy thông tin tài khoản", Toast.LENGTH_LONG).show()
                        binding.edtUsernameRenter.setText("Không có tên")
                        binding.edtGmailRenter.setText(currentUser.email ?: "Không có email")
                        originalPhone = "Không có số điện thoại"
                        val phoneLength = originalPhone?.length ?: 0
                        binding.edtSdtRenter.setText("*".repeat(phoneLength))
                        binding.tilSdtFrag2.setEndIconDrawable(R.drawable.ic_eye_closed)
                        binding.imgAvatarRenter.setImageResource(R.drawable.ic_profile)
                        binding.edtUsernameRenter.isEnabled = false
                        binding.edtGmailRenter.isEnabled = false
                        binding.edtSdtRenter.isEnabled = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi tải thông tin: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.edtUsernameRenter.setText("Lỗi tải dữ liệu")
                    binding.edtGmailRenter.setText("Lỗi tải dữ liệu")
                    originalPhone = "Lỗi tải dữ liệu"
                    val phoneLength = originalPhone?.length ?: 0
                    binding.edtSdtRenter.setText("*".repeat(phoneLength))
                    binding.tilSdtFrag2.setEndIconDrawable(R.drawable.ic_eye_closed)
                    binding.imgAvatarRenter.setImageResource(R.drawable.ic_profile)
                    binding.edtUsernameRenter.isEnabled = false
                    binding.edtGmailRenter.isEnabled = false
                    binding.edtSdtRenter.isEnabled = false
                }
            }
        }
    }

    private fun togglePhoneVisibility() {
        if (!isPhoneVisible) {
            binding.edtSdtRenter.setText(originalPhone)
            binding.tilSdtFrag2.setEndIconDrawable(R.drawable.ic_eye_open)
            isPhoneVisible = true
        } else {
            val phoneLength = originalPhone?.length ?: 0
            binding.edtSdtRenter.setText("*".repeat(phoneLength))
            binding.tilSdtFrag2.setEndIconDrawable(R.drawable.ic_eye_closed)
            isPhoneVisible = false
        }
    }

    private fun changePassword() {
        val oldPassword = binding.txtOldPassword.text?.toString()?.trim()
        val newPassword = binding.txtNewPassword.text?.toString()?.trim()
        val confirmPassword = binding.txtConfirmPassword.text?.toString()?.trim()

        if (oldPassword.isNullOrEmpty() || newPassword.isNullOrEmpty() || confirmPassword.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword.length < 8) {
            Toast.makeText(requireContext(), "Mật khẩu mới phải có ít nhất 8 ký tự", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(requireContext(), "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.email == null) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
            return
        }

        binding.btnSavePassword.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val credential = EmailAuthProvider.getCredential(currentUser.email!!, oldPassword)
                currentUser.reauthenticate(credential).await()
                currentUser.updatePassword(newPassword).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Đổi mật khẩu thành công", Toast.LENGTH_LONG).show()
                    binding.layoutChangePassword.visibility = View.GONE
                    binding.txtOldPassword.text?.clear()
                    binding.txtNewPassword.text?.clear()
                    binding.txtConfirmPassword.text?.clear()
                    binding.btnSavePassword.isEnabled = true
                }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Mật khẩu cũ không đúng", Toast.LENGTH_LONG).show()
                    binding.btnSavePassword.isEnabled = true
                }
            } catch (e: FirebaseAuthWeakPasswordException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Mật khẩu mới quá yếu", Toast.LENGTH_LONG).show()
                    binding.btnSavePassword.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi đổi mật khẩu: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnSavePassword.isEnabled = true
                }
            }
        }
    }

    private fun requestImagePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun handleImageUri(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                handleImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("SecondFragment", "Error processing image from URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi tải ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleImageBitmap(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                saveImageToFirestore(base64Image)
                withContext(Dispatchers.Main) {
                    binding.imgAvatarRenter.setImageBitmap(bitmap)
                    Toast.makeText(requireContext(), "Cập nhật ảnh thành công", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SecondFragment", "Error processing image bitmap: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveImageToFirestore(base64Image: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("SecondFragment", "No authenticated user")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                        startActivity(Intent(requireContext(), AccountActivity::class.java))
                        requireActivity().finish()
                    }
                    return@launch
                }

                db.collection("users").document(currentUser.uid)
                    .update("profileImageUrl", base64Image)
                    .await()

                Log.d("SecondFragment", "Updated profile image for userID: ${currentUser.uid}")
            } catch (e: Exception) {
                Log.e("SecondFragment", "Error saving image to Firestore: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = when {
                        e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền cập nhật ảnh"
                        e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                        else -> "Lỗi khi lưu ảnh: ${e.message}"
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupBookingListener() {
        val user = auth.currentUser
        if (user == null) {
            setLoadingState(false)
            binding.rvBookings.visibility = View.GONE
            binding.txtEmptyBookings.visibility = View.VISIBLE
            setInteractionEnabled(true)
            return
        }

        setLoadingState(true)

        bookingListener = db.collection("bookings")
            .whereEqualTo("userID", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    coroutineScope.launch(Dispatchers.Main) {
                        setLoadingState(false)
                        binding.rvBookings.visibility = View.GONE
                        binding.txtEmptyBookings.visibility = View.VISIBLE
                        setInteractionEnabled(true)
                    }
                    return@addSnapshotListener
                }

                val bookings = snapshot?.documents?.mapNotNull { it.toObject(Booking::class.java) }
                    ?.sortedByDescending { it.createdAt } ?: emptyList()
                coroutineScope.launch(Dispatchers.Main) {
                    bookingAdapter.updateData(bookings)
                    setLoadingState(false)
                    if (bookings.isEmpty()) {
                        binding.rvBookings.visibility = View.GONE
                        binding.txtEmptyBookings.visibility = View.VISIBLE
                    } else {
                        binding.rvBookings.visibility = View.VISIBLE
                        binding.txtEmptyBookings.visibility = View.GONE
                    }
                    setInteractionEnabled(true)
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        this.isLoading = isLoading
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.mainContent.alpha = if (isLoading) 0.5f else 1.0f
    }

    private fun setInteractionEnabled(enabled: Boolean) {
        binding.rvBookings.isEnabled = enabled
        binding.btnLogoutRenter.isEnabled = enabled
        binding.btnChangePasswordRenter.isEnabled = enabled
        binding.imgCameraRenter.isEnabled = enabled
        binding.mainContent.isEnabled = enabled
    }

    override fun onDestroyView() {
        bookingListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}