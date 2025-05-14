package com.trungkien.fbtp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.trungkien.fbtp.databinding.ActivityRegisterBinding
import com.trungkien.fbtp.model.User
import com.trungkien.fbtp.model.UserRole

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "RegisterActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        firestore = Firebase.firestore

        setupRoleSpinner()

        if (isNetworkAvailable()) {
            testFirestoreConnection()
        } else {
            showNetworkError()
        }

        binding.btnRegister.setOnClickListener {
            Log.d(TAG, "Register button clicked, Network available: ${isNetworkAvailable()}")
            if (isNetworkAvailable()) {
                registerUser()
            } else {
                showNetworkError()
            }
        }

        binding.btnBackRegister.setOnClickListener {
            finish()
        }

        binding.txtLogin.setOnClickListener {
            finish()
        }
    }

    private fun testFirestoreConnection() {
        if (auth.currentUser == null) {
            Log.d(TAG, "No authenticated user for Firestore test")
            Toast.makeText(this, "Vui lòng đăng nhập để kiểm tra kết nối Firestore", Toast.LENGTH_SHORT).show()
            return
        }

        val testData = hashMapOf(
            "test" to "connection",
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection("connection_tests").document("test_${System.currentTimeMillis()}")
            .set(testData)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore connection successful")
                Toast.makeText(this, "Kết nối Firestore OK", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore connection failed: ${e.message}", e)
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED") == true -> "Không có quyền truy cập Firestore"
                    e.message?.contains("UNAVAILABLE") == true -> "Firestore không khả dụng, kiểm tra kết nối mạng"
                    else -> "Lỗi kết nối Firestore: ${e.message}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    private fun setupRoleSpinner() {
        val roles = UserRole.values()
            .filter { it != UserRole.ADMIN }
            .map { it.roleName }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roles
        )
        binding.spinnerDangKy.setAdapter(adapter)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "^[A-Za-z0-9+_.-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$"
        return email.matches(emailPattern.toRegex())
    }

    private fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^0\\d{9}$"))
    }

    private fun registerUser() {
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val username = binding.edtUsername.text.toString().trim()
        val phone = binding.edtSdt.text.toString().trim()
        val selectedRoleName = binding.spinnerDangKy.text.toString()

        if (!validateInputs(email, password, confirmPassword, username, phone, selectedRoleName)) {
            return
        }

        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    handleRegistrationSuccess(email, username, phone, selectedRoleName)
                } else {
                    handleRegistrationFailure(task.exception)
                }
            }
    }

    private fun validateInputs(
        email: String,
        password: String,
        confirmPassword: String,
        username: String,
        phone: String,
        selectedRoleName: String
    ): Boolean {
        binding.edtEmail.error = null
        binding.etPassword.error = null
        binding.etConfirmPassword.error = null
        binding.edtUsername.error = null
        binding.edtSdt.error = null

        var isValid = true

        when {
            email.isEmpty() -> {
                binding.edtEmail.error = "Vui lòng nhập email"
                isValid = false
            }
            !isValidEmail(email) -> {
                binding.edtEmail.error = "Email không hợp lệ"
                isValid = false
            }
            password.isEmpty() -> {
                binding.etPassword.error = "Vui lòng nhập mật khẩu"
                isValid = false
            }
            password.length < 6 -> {
                binding.etPassword.error = "Mật khẩu phải từ 6 ký tự trở lên"
                isValid = false
            }
            confirmPassword != password -> {
                binding.etConfirmPassword.error = "Mật khẩu xác nhận không khớp"
                isValid = false
            }
            username.isEmpty() -> {
                binding.edtUsername.error = "Vui lòng nhập tên người dùng"
                isValid = false
            }
            phone.isEmpty() -> {
                binding.edtSdt.error = "Vui lòng nhập số điện thoại"
                isValid = false
            }
            !isValidPhone(phone) -> {
                binding.edtSdt.error = "Số điện thoại phải bắt đầu bằng 0 và có 10 số"
                isValid = false
            }
            selectedRoleName.isEmpty() -> {
                Toast.makeText(this, "Vui lòng chọn vai trò", Toast.LENGTH_SHORT).show()
                isValid = false
            }
        }
        return isValid
    }

    private fun handleRegistrationSuccess(
        email: String,
        username: String,
        phone: String,
        selectedRoleName: String
    ) {
        val user = auth.currentUser
        if (user == null) {
            showLoading(false)
            showError("Lỗi: Không tạo được tài khoản")
            return
        }

        val roleID = UserRole.values().firstOrNull { it.roleName == selectedRoleName }?.name ?: "RENTER"
        val newUser = User(
            userID = user.uid,
            username = username,
            email = email,
            phone = phone,
            roleID = roleID
        )

        Log.d(TAG, "Saving user: $newUser")

        firestore.collection("users").document(user.uid)
            .set(newUser)
            .addOnSuccessListener {
                Log.d(TAG, "User saved successfully")
                showLoading(false)
                Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show()
                navigateToProperScreen(roleID, username)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save user: ${e.message}", e)
                showLoading(false)
                showError("Lỗi khi lưu thông tin: ${e.message}")
                user.delete().addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        Log.d(TAG, "Deleted incomplete user: ${user.uid}")
                    } else {
                        Log.e(TAG, "Failed to delete incomplete user: ${deleteTask.exception?.message}")
                    }
                }
            }
    }

    private fun navigateToProperScreen(roleID: String, username: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("IS_OWNER", roleID == UserRole.OWNER.name)
            putExtra("USERNAME", username)
        }
        startActivity(intent)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            Log.d(TAG, "Network: $network, Capabilities: $capabilities")
            capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            val activeNetwork = connectivityManager.activeNetworkInfo
            Log.d(TAG, "ActiveNetwork: $activeNetwork")
            activeNetwork?.isConnected == true
        }
    }

    private fun handleRegistrationFailure(exception: Exception?) {
        showLoading(false)
        Log.e(TAG, "Registration failed: ${exception?.message}", exception)
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không hợp lệ"
            is FirebaseAuthUserCollisionException -> "Email đã được sử dụng. Vui lòng chọn email khác"
            is java.net.UnknownHostException -> "Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng"
            is java.net.SocketTimeoutException -> "Hết thời gian kết nối. Vui lòng thử lại"
            else -> "Đăng ký thất bại: ${exception?.message ?: "Lỗi không xác định"}"
        }
        showError(errorMessage)
    }

    private fun showNetworkError() {
        Toast.makeText(
            this,
            "Không có kết nối mạng. Vui lòng kiểm tra Wi-Fi hoặc dữ liệu di động.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
    }
}