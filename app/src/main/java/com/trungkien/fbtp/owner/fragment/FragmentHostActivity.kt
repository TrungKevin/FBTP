package com.trungkien.fbtp.owner.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.trungkien.fbtp.R
import com.trungkien.fbtp.owner.fragment.FourthFragment

class FragmentHostActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_host)

        val fragmentToShow = intent.getStringExtra("FRAGMENT_TO_SHOW")
        val coSoID = intent.getStringExtra("coSoID") ?: ""
        val courtType = intent.getStringExtra("courtType") ?: ""

        if (fragmentToShow == "FourthFragment") {
            val fragment = FourthFragment().apply {
                arguments = Bundle().apply {
                    putString("coSoID", coSoID)
                    putString("courtType", courtType)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
}