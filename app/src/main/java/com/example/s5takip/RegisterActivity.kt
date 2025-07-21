package com.fabrika.s5takip

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fabrika.s5takip.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Kayıt olma ekranı
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase Auth'u başlat
        auth = FirebaseAuth.getInstance()

        // Click listener'ları ayarla
        setupClickListeners()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Yeni Hesap Oluştur"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Kayıt ol butonu
        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        // Giriş yap butonu (geri dön)
        binding.btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    /**
     * Kullanıcı kaydı yap
     */
    private fun registerUser() {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Form kontrolü
        if (firstName.isEmpty()) {
            binding.etFirstName.error = "Ad gerekli"
            return
        }

        if (lastName.isEmpty()) {
            binding.etLastName.error = "Soyad gerekli"
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Email gerekli"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Geçerli bir email adresi girin"
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Şifre gerekli"
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Şifre en az 6 karakter olmalı"
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Şifreler eşleşmiyor"
            return
        }

        // Loading göster
        showLoading("Hesap oluşturuluyor...")

        lifecycleScope.launch {
            try {
                // Firebase ile hesap oluştur
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    // Kullanıcı profil bilgilerini güncelle
                    val fullName = "$firstName $lastName"
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()

                    user.updateProfile(profileUpdates).await()

                    hideLoading()

                    Toast.makeText(this@RegisterActivity,
                        "Hesap başarıyla oluşturuldu! Hoş geldiniz, $fullName! 🎉",
                        Toast.LENGTH_LONG).show()

                    // Ana sayfaya yönlendir
                    navigateToGroupSelection()

                } else {
                    hideLoading()
                    Toast.makeText(this@RegisterActivity, "Hesap oluşturulamadı", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                hideLoading()

                val errorMessage = when {
                    e.message?.contains("email-already-in-use") == true ->
                        "Bu email adresi zaten kullanımda"
                    e.message?.contains("weak-password") == true ->
                        "Şifre çok zayıf, daha güçlü bir şifre seçin"
                    e.message?.contains("invalid-email") == true ->
                        "Geçersiz email adresi"
                    e.message?.contains("network") == true ->
                        "İnternet bağlantısı sorunu"
                    else -> "Kayıt hatası: ${e.message}"
                }

                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Grup seçim ekranına git - DÜZELTİLMİŞ VERSİYON
     */
    private fun navigateToGroupSelection() {
        try {
            println("DEBUG: 📱 RegisterActivity'den GroupSelectionActivity'ye yönlendiriliyor...")

            val intent = Intent(this, GroupSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            println("DEBUG: 🔄 Intent oluşturuldu, startActivity çağrılıyor...")
            startActivity(intent)

            println("DEBUG: ✨ Activity başlatıldı, finish() çağrılıyor...")
            finish()

            println("DEBUG: 🎯 RegisterActivity sonlandırıldı")

        } catch (e: Exception) {
            println("DEBUG: ❌ Navigation hatası: ${e.message}")
            e.printStackTrace()

            Toast.makeText(this,
                "Navigation hatası: ${e.message}",
                Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Loading göstergisini göster
     */
    private fun showLoading(message: String) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvLoadingStatus.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = message
        binding.btnRegister.isEnabled = false
        binding.btnBackToLogin.isEnabled = false
    }

    /**
     * Loading göstergisini gizle
     */
    private fun hideLoading() {
        binding.progressLoading.visibility = View.GONE
        binding.tvLoadingStatus.visibility = View.GONE
        binding.btnRegister.isEnabled = true
        binding.btnBackToLogin.isEnabled = true
    }

    /**
     * Geri buton basıldığında
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}