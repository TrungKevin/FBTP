package com.trungkien.fbtp.owner.fragment

import android.Manifest
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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.MainActivity
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.FiveFragmentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class FiveFragment : Fragment() {

    private var _binding: FiveFragmentBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var originalPhone: String? = null // Lưu số điện thoại gốc
    private var isPhoneVisible: Boolean = false // Trạng thái ban đầu: ẩn số điện thoại

    // Activity result launcher cho chọn ảnh
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageUri(uri)
            } ?: Toast.makeText(requireContext(), "Không chọn được ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launcher cho quyền
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
        _binding = FiveFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load user information from Firestore
        loadUserInformation()

        // Handle phone visibility toggle
        binding.tilSdtFrag5.setEndIconOnClickListener {
            togglePhoneVisibility()
        }

        // Handle change password button
        binding.btnChangePassword.setOnClickListener {
            // Toggle visibility of change password form
            if (binding.layoutChangePassword.visibility == View.VISIBLE) {
                binding.layoutChangePassword.visibility = View.GONE
            } else {
                binding.layoutChangePassword.visibility = View.VISIBLE
                // Clear input fields
                binding.txtOldPassword.text?.clear()
                binding.txtNewPassword.text?.clear()
                binding.txtConfirmPassword.text?.clear()
            }
        }

        // Handle save password button
        binding.btnSavePassword.setOnClickListener {
            changePassword()
        }

        // Handle camera button to pick image
        binding.imgCamera.setOnClickListener {
            requestImagePermission()
        }

        // Handle logout button
        binding.btnLogoutFrag5.setOnClickListener {
            // Sign out from Firebase
            auth.signOut()

            // Hide bottom navigation
            (requireActivity() as? MainActivity)?.findViewById<View>(R.id.bottomNavigationOwner)?.visibility = View.GONE

            // Navigate to AccountActivity and clear back stack
            startActivity(Intent(requireContext(), AccountActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun loadUserInformation() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get current user
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                        // Navigate to AccountActivity
                        startActivity(Intent(requireContext(), AccountActivity::class.java))
                        requireActivity().finish()
                    }
                    return@launch
                }

                // Show loading state
                withContext(Dispatchers.Main) {
                    binding.txtUsername.isEnabled = false
                    binding.txtGmail.isEnabled = false
                    binding.txtPhone.isEnabled = false
                }

                // Fetch user data from Firestore
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                if (userDoc.exists()) {
                    val username = userDoc.getString("username") ?: "Không có tên"
                    val email = userDoc.getString("email") ?: currentUser.email ?: "Không có email"
                    val phone = userDoc.getString("phone") ?: "Không có số điện thoại"
                    val profileImageUrl = userDoc.getString("profileImageUrl") ?: ""

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        binding.txtUsername.setText(username)
                        binding.txtGmail.setText(email)
                        // Store original phone number
                        originalPhone = phone
                        // Show asterisks initially
                        val phoneLength = phone.length
                        binding.txtPhone.setText("*".repeat(phoneLength))
                        // Set initial endIcon to ic_eye_closed
                        binding.tilSdtFrag5.setEndIconDrawable(R.drawable.ic_eye_closed)
                        // Ensure fields remain non-editable
                        binding.txtUsername.isEnabled = false
                        binding.txtGmail.isEnabled = false
                        binding.txtPhone.isEnabled = false

                        // Load profile image from Base64
                        if (profileImageUrl.isNotEmpty()) {
                            try {
                                val decodedBytes = Base64.decode(profileImageUrl, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                binding.imgAvatar.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                // Fallback to default image if decoding fails
                                binding.imgAvatar.setImageResource(R.drawable.ic_profile)
                            }
                        } else {
                            binding.imgAvatar.setImageResource(R.drawable.ic_profile)
                        }
                    }
                } else {
                    // User document does not exist
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Không tìm thấy thông tin tài khoản", Toast.LENGTH_LONG).show()
                        binding.txtUsername.setText("Không có tên")
                        binding.txtGmail.setText(currentUser.email ?: "Không có email")
                        // Store default phone and show asterisks
                        originalPhone = "Không có số điện thoại"
                        val phoneLength = originalPhone?.length ?: 0
                        binding.txtPhone.setText("*".repeat(phoneLength))
                        binding.tilSdtFrag5.setEndIconDrawable(R.drawable.ic_eye_closed)
                        // Set default profile image
                        binding.imgAvatar.setImageResource(R.drawable.ic_profile)
                        // Ensure fields remain non-editable
                        binding.txtUsername.isEnabled = false
                        binding.txtGmail.isEnabled = false
                        binding.txtPhone.isEnabled = false
                    }
                }
            } catch (e: Exception) {
                // Handle errors (e.g., network issues, Firestore permissions)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi tải thông tin: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.txtUsername.setText("Lỗi tải dữ liệu")
                    binding.txtGmail.setText("Lỗi tải dữ liệu")
                    // Store default phone and show asterisks
                    originalPhone = "Lỗi tải dữ liệu"
                    val phoneLength = originalPhone?.length ?: 0
                    binding.txtPhone.setText("*".repeat(phoneLength))
                    binding.tilSdtFrag5.setEndIconDrawable(R.drawable.ic_eye_closed)
                    // Set default profile image
                    binding.imgAvatar.setImageResource(R.drawable.ic_profile)
                    // Ensure fields remain non-editable
                    binding.txtUsername.isEnabled = false
                    binding.txtGmail.isEnabled = false
                    binding.txtPhone.isEnabled = false
                }
            }
        }
    }

    private fun togglePhoneVisibility() {
        if (!isPhoneVisible) {
            // Show phone: Restore original
            binding.txtPhone.setText(originalPhone)
            binding.tilSdtFrag5.setEndIconDrawable(R.drawable.ic_eye_open)
            isPhoneVisible = true
        } else {
            // Hide phone: Show asterisks
            val phoneLength = originalPhone?.length ?: 0
            binding.txtPhone.setText("*".repeat(phoneLength))
            binding.tilSdtFrag5.setEndIconDrawable(R.drawable.ic_eye_closed)
            isPhoneVisible = false
        }
    }

    private fun changePassword() {
        val oldPassword = binding.txtOldPassword.text?.toString()?.trim()
        val newPassword = binding.txtNewPassword.text?.toString()?.trim()
        val confirmPassword = binding.txtConfirmPassword.text?.toString()?.trim()

        // Validate inputs
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

        // Show loading state
        binding.btnSavePassword.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Reauthenticate user
                val credential = EmailAuthProvider.getCredential(currentUser.email!!, oldPassword)
                currentUser.reauthenticate(credential).await()

                // Update password
                currentUser.updatePassword(newPassword).await()

                // Update UI on success
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Đổi mật khẩu thành công", Toast.LENGTH_LONG).show()
                    // Hide form and clear fields
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
                Log.e("FiveFragment", "Error processing image from URI: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi tải ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleImageBitmap(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Nén và chuyển ảnh thành Base64
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                // Lưu ảnh vào Firestore
                saveImageToFirestore(base64Image)

                // Cập nhật giao diện
                withContext(Dispatchers.Main) {
                    binding.imgAvatar.setImageBitmap(bitmap)
                    Toast.makeText(requireContext(), "Cập nhật ảnh thành công", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FiveFragment", "Error processing image bitmap: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveImageToFirestore(base64Image: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Kiểm tra xác thực
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e("FiveFragment", "No authenticated user")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
                        startActivity(Intent(requireContext(), AccountActivity::class.java))
                        requireActivity().finish()
                    }
                    return@launch
                }

                // Cập nhật profileImageUrl trong Firestore
                db.collection("users").document(currentUser.uid)
                    .update("profileImageUrl", base64Image)
                    .await()

                Log.d("FiveFragment", "Updated profile image for userID: ${currentUser.uid}")
            } catch (e: Exception) {
                Log.e("FiveFragment", "Error saving image to Firestore: ${e.message}", e)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}