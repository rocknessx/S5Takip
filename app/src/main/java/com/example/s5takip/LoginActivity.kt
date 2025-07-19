package com.fabrika.s5takip

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fabrika.s5takip.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Giriş ekranı - İki ayrı giriş butonu ile
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firebaseManager: FirebaseManager

    // Google Sign-In için launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)

            if (account != null) {
                signInWithGoogle(account)
            } else {
                hideLoading()
                Toast.makeText(this, "Google giriş iptal edildi", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            hideLoading()
            Toast.makeText(this, "Google giriş hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase Auth'u başlat
        auth = FirebaseAuth.getInstance()
        firebaseManager = FirebaseManager.getInstance()

        // Zaten giriş yapılmışsa direkt ana sayfaya git
        checkCurrentUser()

        // Click listener'ları ayarla
        setupClickListeners()
    }

    /**
     * Mevcut kullanıcı kontrolü
     */
    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Kullanıcı zaten giriş yapmış, grup seçimine git
            navigateToGroupSelection()
        }
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Gmail giriş butonu (Google Auth)
        binding.btnGoogleSignin.text = "🌐 Gmail ile Giriş Yap"
        binding.btnGoogleSignin.setOnClickListener {
            startGoogleSignIn()
        }

        // Email giriş butonu ekleyin (layout'ta olmalı)
        // binding.btnEmailSignin.text = "📧 Email ile Giriş Yap"
        // binding.btnEmailSignin.setOnClickListener {
        //     showEmailLoginDialog()
        // }

        // Geçici olarak bir buton daha ekleyelim
        binding.btnGoogleSignin.setOnClickListener {
            showLoginOptions()
        }
    }

    /**
     * Giriş seçenekleri dialog'u göster
     */
    private fun showLoginOptions() {
        val options = arrayOf("🌐 Gmail ile Giriş Yap", "📧 Email ile Giriş Yap", "🧪 Test Kullanıcısı")

        AlertDialog.Builder(this)
            .setTitle("Giriş Yöntemi Seçin")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startGoogleSignIn()
                    1 -> showEmailLoginDialog()
                    2 -> createTestUserAndLogin()
                }
            }
            .show()
    }

    /**
     * Google Sign-In başlat
     */
    private fun startGoogleSignIn() {
        showLoading("Gmail ile giriş yapılıyor...")

        try {
            val googleSignInClient = firebaseManager.getGoogleSignInClient(this)
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            hideLoading()
            Toast.makeText(this, "Google giriş başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Google hesabı ile Firebase'e giriş yap - Düzeltilmiş versiyon
     */
    private fun signInWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        showLoading("Gmail hesabı doğrulanıyor...")

        lifecycleScope.launch {
            try {
                println("DEBUG: Google sign-in başladı")

                val result = firebaseManager.signInWithGoogle(account)

                println("DEBUG: FirebaseManager sonucu: ${result.isSuccess}")

                if (result.isSuccess) {
                    val user = result.getOrNull()
                    println("DEBUG: AppUser oluşturuldu: ${user?.displayName}, ${user?.email}")

                    hideLoading()

                    Toast.makeText(
                        this@LoginActivity,
                        "Gmail ile giriş başarılı! Hoş geldiniz, ${user?.displayName ?: user?.email}!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Kısa bekleme sonrası navigation
                    binding.root.postDelayed({
                        navigateToGroupSelection()
                    }, 1000)

                } else {
                    hideLoading()
                    val error = result.exceptionOrNull()
                    println("DEBUG: Gmail giriş hatası: ${error?.message}")

                    Toast.makeText(
                        this@LoginActivity,
                        "Gmail giriş başarısız: ${error?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                hideLoading()
                println("DEBUG: Gmail giriş exception: ${e.message}")
                e.printStackTrace()

                Toast.makeText(
                    this@LoginActivity,
                    "Gmail giriş hatası: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Email giriş dialog'u göster
     */
    private fun showEmailLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_login, null)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_password)

        // Test değerleri önceden doldur
        etEmail.setText("test@s5takip.com")
        etPassword.setText("123456")

        AlertDialog.Builder(this)
            .setTitle("📧 Email ile Giriş Yap")
            .setView(dialogView)
            .setPositiveButton("Giriş") { _, _ ->
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    signInWithEmail(email, password)
                } else {
                    Toast.makeText(this, "Email ve şifre gerekli", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Kayıt Ol") { _, _ ->
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    registerWithEmail(email, password)
                } else {
                    Toast.makeText(this, "Email ve şifre gerekli", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("İptal", null)
            .show()
    }

    /**
     * Email ile giriş yap
     */
    private fun signInWithEmail(email: String, password: String) {
        showLoading("Email ile giriş yapılıyor...")

        lifecycleScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    hideLoading()
                    Toast.makeText(this@LoginActivity,
                        "Email giriş başarılı! Hoş geldiniz, ${user.email}!",
                        Toast.LENGTH_SHORT).show()
                    navigateToGroupSelection()
                } else {
                    hideLoading()
                    Toast.makeText(this@LoginActivity, "Email giriş başarısız", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(this@LoginActivity,
                    "Email giriş hatası: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Email ile kayıt ol
     */
    private fun registerWithEmail(email: String, password: String) {
        showLoading("Email hesabı oluşturuluyor...")

        lifecycleScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    hideLoading()
                    Toast.makeText(this@LoginActivity,
                        "Email hesabı oluşturuldu! Hoş geldiniz, ${user.email}!",
                        Toast.LENGTH_SHORT).show()
                    navigateToGroupSelection()
                } else {
                    hideLoading()
                    Toast.makeText(this@LoginActivity, "Email hesabı oluşturulamadı", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(this@LoginActivity,
                    "Email kayıt hatası: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Test kullanıcısı oluştur ve giriş yap
     */
    private fun createTestUserAndLogin() {
        showLoading("Test kullanıcısı oluşturuluyor...")

        lifecycleScope.launch {
            try {
                val email = "test@s5takip.com"
                val password = "123456"

                // Önce giriş yapmayı dene
                try {
                    auth.signInWithEmailAndPassword(email, password).await()
                    hideLoading()
                    Toast.makeText(this@LoginActivity, "Test kullanıcısı ile giriş yapıldı!", Toast.LENGTH_SHORT).show()
                    navigateToGroupSelection()
                } catch (e: Exception) {
                    // Kullanıcı yoksa oluştur
                    auth.createUserWithEmailAndPassword(email, password).await()
                    hideLoading()
                    Toast.makeText(this@LoginActivity, "Test kullanıcısı oluşturuldu ve giriş yapıldı!", Toast.LENGTH_SHORT).show()
                    navigateToGroupSelection()
                }

            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(this@LoginActivity, "Test kullanıcısı hatası: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Grup seçim ekranına git
     */
    private fun navigateToGroupSelection() {
        val intent = Intent(this, GroupSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Loading göstergisini göster
     */
    private fun showLoading(message: String) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvLoadingStatus.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = message
        binding.btnGoogleSignin.isEnabled = false
    }

    /**
     * Loading göstergisini gizle
     */
    private fun hideLoading() {
        binding.progressLoading.visibility = View.GONE
        binding.tvLoadingStatus.visibility = View.GONE
        binding.btnGoogleSignin.isEnabled = true
    }
}