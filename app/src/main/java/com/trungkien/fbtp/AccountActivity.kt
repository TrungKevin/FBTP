package com.trungkien.fbtp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.trungkien.fbtp.databinding.ActivityAccountBinding
import com.trungkien.fbtp.model.User
import com.trungkien.fbtp.model.UserRole

class AccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAccountBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        firestore = Firebase.firestore

        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.btnLogin.setOnClickListener {
            val email = binding.edtAccount.text.toString().trim()
            val password = binding.password.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Vui lòng nhập email và mật khẩu"
                return@setOnClickListener
            }

            if (!isNetworkAvailable()) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Không có kết nối mạng"
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            showLoading(true)


            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    Log.d("+++++++++++++++++", "onCreate: ")
                    val userId = authResult.user?.uid
                    if (userId != null) {
                        firestore.collection("users").document(userId).get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val user = document.toObject(User::class.java)
                                    user?.let {
                                        cacheUserData(it)
                                        val isOwner = it.roleID == UserRole.OWNER.name
                                        val intent = Intent(this, MainActivity::class.java).apply {
                                            putExtra("IS_OWNER", isOwner)
                                            putExtra("USERNAME", it.username)
                                        }
                                        startActivity(intent)
                                        finish()
                                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    binding.errorText.visibility = View.VISIBLE
                                    binding.errorText.text = "Không tìm thấy thông tin người dùng"
                                }
                                showLoading(false)
                                binding.btnLogin.isEnabled = true
                            }
                            .addOnFailureListener { e ->
                                Log.e("FirebaseError", "Lỗi đọc Firestore", e)
                                binding.errorText.visibility = View.VISIBLE
                                binding.errorText.text = "Lỗi khi truy vấn dữ liệu: ${e.message}"
                                showLoading(false)
                                binding.btnLogin.isEnabled = true
                            }
                    } else {
                        binding.errorText.visibility = View.VISIBLE
                        binding.errorText.text = "Lỗi không tìm thấy ID người dùng"
                        showLoading(false)
                        binding.btnLogin.isEnabled = true
                    }
                }
                .addOnFailureListener { exception ->
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = "Sai tài khoản hoặc mật khẩu: ${exception.message}"
                    showLoading(false)
                    binding.btnLogin.isEnabled = true
                }
        }

        binding.btnCRRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.forgotPassword.setOnClickListener {
            val email = binding.edtAccount.text.toString().trim()
            if (email.isEmpty()) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Vui lòng nhập email"
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Email đặt lại mật khẩu đã được gửi", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = "Lỗi: ${it.message}"
                }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.root.alpha = if (show) 0.5f else 1.0f
        if (!show) binding.errorText.visibility = View.GONE
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnected == true
    }

    private fun cacheUserData(user: User) {
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("IS_OWNER", user.roleID == UserRole.OWNER.name)
            putString("USERNAME", user.username)
            apply()
        }
    }
}