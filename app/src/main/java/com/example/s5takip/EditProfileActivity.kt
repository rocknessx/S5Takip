package com.fabrika.s5takip

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fabrika.s5takip.databinding.ActivityEditProfileBinding
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Profil düzenleme ekranı
 * Kullanıcı adı, soyadı ve profil fotoğrafını düzenleme
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private var selectedImageUri: Uri? = null
    private val currentUser = FirebaseAuth.getInstance().currentUser

    // Fotoğraf seçme için launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            if (selectedImageUri != null) {
                // Fotoğrafı göster
                binding.ivProfilePhoto.setImageURI(selectedImageUri)
                binding.tvPhotoInstruction.text = "✓ Yeni fotoğraf seçildi"
            }
        } else if (result.resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, "Fotoğraf seçilirken hata: ${ImagePicker.getError(result.data)}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Profil Düzenle"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Mevcut kullanıcı bilgilerini yükle
        loadCurrentUserInfo()

        // Click listener'ları ayarla
        setupClickListeners()
    }

    /**
     * Mevcut kullanıcı bilgilerini yükle
     */
    private fun loadCurrentUserInfo() {
        if (currentUser != null) {
            // Kullanıcı adını böl (ad soyad)
            val displayName = currentUser.displayName ?: ""
            val nameParts = displayName.split(" ")

            binding.etFirstName.setText(nameParts.getOrNull(0) ?: "")
            binding.etLastName.setText(
                if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""
            )

            // Email (değiştirilemez)
            binding.etEmail.setText(currentUser.email ?: "")
            binding.etEmail.isEnabled = false

            // Profil fotoğrafı
            val photoUrl = currentUser.photoUrl
            if (photoUrl != null) {
                // Glide ile yüklenebilir (gerçek uygulamada)
                // Glide.with(this).load(photoUrl).into(binding.ivProfilePhoto)
                binding.tvPhotoInstruction.text = "Mevcut profil fotoğrafınız"
            }
        } else {
            Toast.makeText(this, "Kullanıcı bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Profil fotoğrafı değiştir
        binding.llPhotoContainer.setOnClickListener {
            openImagePicker()
        }

        // İptal butonu
        binding.btnCancel.setOnClickListener {
            finish()
        }

        // Kaydet butonu
        binding.btnSave.setOnClickListener {
            saveProfile()
        }
    }

    /**
     * Fotoğraf seçici aç
     */
    private fun openImagePicker() {
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(400, 400) // Profil fotoğrafı için küçük boyut
            .createIntent { intent ->
                imagePickerLauncher.launch(intent)
            }
    }

    /**
     * Profil bilgilerini kaydet
     */
    private fun saveProfile() {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()

        if (firstName.isEmpty()) {
            binding.etFirstName.error = "Ad gerekli"
            return
        }

        if (currentUser == null) {
            Toast.makeText(this, "Kullanıcı bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        // Loading göster
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Kaydediliyor..."

        lifecycleScope.launch {
            try {
                val fullName = if (lastName.isNotEmpty()) "$firstName $lastName" else firstName

                // Profil güncelleme objesini oluştur
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)

                // Eğer yeni fotoğraf seçildiyse ekle
                if (selectedImageUri != null) {
                    // Gerçek uygulamada fotoğrafı Firebase Storage'a yükleyip URL alınır
                    // Şimdilik local URI'yi kullanıyoruz
                    profileUpdates.setPhotoUri(selectedImageUri)
                }

                // Firebase'e güncelleme gönder
                currentUser.updateProfile(profileUpdates.build()).await()

                runOnUiThread {
                    Toast.makeText(this@EditProfileActivity,
                        "Profil başarıyla güncellendi! ✓", Toast.LENGTH_LONG).show()

                    // Sonuç olarak başarı döndür
                    setResult(Activity.RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@EditProfileActivity,
                        "Profil güncellenirken hata: ${e.message}", Toast.LENGTH_LONG).show()

                    // Buton durumunu eski haline getir
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Kaydet"
                }
            }
        }
    }

    /**
     * Geri buton basıldığında
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}