package com.trungkien.fbtp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.trungkien.fbtp.databinding.ActivityMainBinding
import com.trungkien.fbtp.owner.fragment.ThirdFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import me.ibrahimsn.lib.SmoothBottomBar

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth
        Log.d("+++++++++++", " auth.currentUser : ${auth.currentUser}")
        if (auth.currentUser == null) {
            Log.d(TAG, "User not authenticated, redirecting to AccountActivity")
            startActivity(Intent(this, AccountActivity::class.java))
            finish()
            return
        }

        val isOwner = intent.getBooleanExtra("IS_OWNER", false)
        val username = intent.getStringExtra("USERNAME") ?: "Không có dữ liệu"
        Log.d(TAG, "IS_OWNER: $isOwner, USERNAME: $username")

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        navigateToStartDestination(isOwner, username)
        setupBottomBar(isOwner)
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                startActivity(Intent(this, AccountActivity::class.java))
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent?.let {
            val coSoID = it.getStringExtra("coSoID")
            val imageBase64 = it.getStringExtra("imageBase64")
            if (coSoID != null && imageBase64 != null) {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val fragment = navHostFragment.childFragmentManager.fragments.firstOrNull { it is ThirdFragment } as? ThirdFragment
                fragment?.updateFacilityImage(coSoID, imageBase64) ?: run {
                    Log.d(TAG, "ThirdFragment not found, data will be refreshed on resume")
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    fun onLoginSuccess(isOwner: Boolean, username: String) {
        Log.d(TAG, "Login success: IS_OWNER=$isOwner, USERNAME=$username")
        navigateToStartDestination(isOwner, username)
        setupBottomBar(isOwner)
    }

    private fun navigateToStartDestination(isOwner: Boolean, username: String) {
        val bundle = Bundle().apply { putString("username", username) }
        val navGraphId = if (isOwner) R.navigation.nav_owner else R.navigation.nav_renter
        navController.setGraph(navGraphId)
        val destination = if (isOwner) R.id.third_fragment else R.id.first_fragment
        navController.navigate(destination, bundle)
    }

    private fun setupBottomBar(isOwner: Boolean) {
        val ownerBar = findViewById<SmoothBottomBar>(R.id.bottomNavigationOwner)
        val renterBar = findViewById<SmoothBottomBar>(R.id.bottomNavigationRenter)

        if (isOwner) {
            ownerBar.visibility = View.VISIBLE
            renterBar.visibility = View.GONE

            ownerBar.setOnItemSelectedListener { index ->
                when (index) {
                    0 -> navController.navigate(R.id.third_fragment)
                    1 -> {
                        lifecycleScope.launch {
                            try {
                                val snapshot = firestore.collection("sport_facilities")
                                    .whereEqualTo("ownerID", auth.currentUser?.uid)
                                    .limit(1)
                                    .get()
                                    .await()
                                val coSoID = snapshot.documents.firstOrNull()?.id
                                if (coSoID != null) {
                                    val bundle = Bundle().apply { putString("coSoID", coSoID) }
                                    navController.navigate(R.id.fourth_fragment, bundle)
                                } else {
                                    Log.w(TAG, "No facilities found for owner")
                                    navController.navigate(R.id.third_fragment)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching coSoID", e)
                                navController.navigate(R.id.third_fragment)
                            }
                        }
                        true
                    }
                    2 -> navController.navigate(R.id.five_fragment)
                    else -> false
                }
            }
        } else {
            ownerBar.visibility = View.GONE
            renterBar.visibility = View.VISIBLE

            renterBar.setOnItemSelectedListener { index ->
                when (index) {
                    0 -> navController.navigate(R.id.first_fragment)
                    1 -> navController.navigate(R.id.second_fragment)
                    else -> false
                }
            }
        }
    }
}