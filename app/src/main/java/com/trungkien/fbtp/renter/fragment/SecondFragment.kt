package com.trungkien.fbtp.renter.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.trungkien.fbtp.AccountActivity
import com.trungkien.fbtp.MainActivity
import com.trungkien.fbtp.R
import com.trungkien.fbtp.databinding.FragmentSecondBinding

class SecondFragment : Fragment() {

    private lateinit var binding: FragmentSecondBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        // Xử lý nút đăng xuất
        binding.btnLogoutFrag2.setOnClickListener {
            // 1. Xóa thông tin đăng nhập trong SharedPreferences
            val sharedPref = requireActivity().getSharedPreferences("user_data", Context.MODE_PRIVATE)
            sharedPref.edit().clear().apply()

            // 2. Ẩn bottom navigation
            val activity = requireActivity() as? MainActivity
            activity?.findViewById<View>(R.id.bottomNavigationRenter)?.visibility = View.GONE

            // 3. Điều hướng về AccountActivity
            val intent = Intent(requireContext(), AccountActivity::class.java)
            startActivity(intent)
            requireActivity().finish() // Kết thúc MainActivity để ngăn quay lại
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}