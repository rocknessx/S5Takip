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
 * Giriş ekranı - Tamamen Düzeltilmiş Versiyon
 * Gmail ile giriş ve email/şifre giriş seçenekleri
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
                println("DEBUG: Google hesabı seçildi: ${account.email}")
                signInWithGoogle(account)
            } else {
                hideLoading()
                Toast.makeText(this, "Google giriş iptal edildi", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            hideLoading()
            println("DEBUG: Google Sign-In API Exception: ${e.statusCode} - ${e.message}")

            val errorMessage = when (e.statusCode) {
                12501 -> "Giriş iptal edildi"
                12502 -> "Geçersiz hesap seçimi"
                12500 -> "Google Play Services güncelleştirmesi gerekli"
                else -> "Google giriş hatası (Kod: ${e.statusCode})"
            }

            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        println("DEBUG: LoginActivity onCreate başladı")

        // Firebase Auth'u başlat
        auth = FirebaseAuth.getInstance()
        firebaseManager = FirebaseManager.getInstance()

        // Zaten giriş yapılmışsa direkt ana sayfaya git
        checkCurrentUser()

        // Click listener'ları ayarla
        setupClickListeners()

        println("DEBUG: LoginActivity hazır")
    }

    /**
     * Mevcut kullanıcı kontrolü
     */
    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            println("DEBUG: Kullanıcı zaten giriş yapmış: ${currentUser.email}")
            // Firestore bağlantısını test etmeden direkt yönlendir
            navigateToGroupSelection()
        } else {
            println("DEBUG: Kullanıcı giriş yapmamış")
        }
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Gmail giriş butonu
        binding.btnGoogleSignin.setOnClickListener {
            println("DEBUG: Gmail giriş butonuna tıklandı")
            startGoogleSignIn()
        }

        // Email giriş butonu
        binding.btnEmailSignin.setOnClickListener {
            println("DEBUG: Email giriş butonuna tıklandı")
            showEmailLoginDialog()
        }

        // Kayıt ol butonu
        binding.btnRegister.setOnClickListener {
            println("DEBUG: Kayıt ol butonuna tıklandı")
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Google Sign-In başlat - Hesap seçimi ile
     */
    private fun startGoogleSignIn() {
        showLoading("Gmail hesaplarınız yükleniyor...")

        try {
            println("DEBUG: Google Sign-In Client oluşturuluyor...")
            val googleSignInClient = firebaseManager.getGoogleSignInClient(this)

            // Önceki oturumları temizle ki hesap seçimi çıksın
            googleSignInClient.signOut().addOnCompleteListener { signOutTask ->
                println("DEBUG: Önceki Google oturumları temizlendi: ${signOutTask.isSuccessful}")

                val signInIntent = googleSignInClient.signInIntent
                println("DEBUG: Google Sign-In intent başlatılıyor...")
                googleSignInLauncher.launch(signInIntent)
            }
        } catch (e: Exception) {
            hideLoading()
            println("DEBUG: Google Sign-In başlatma hatası: ${e.message}")
            Toast.makeText(this, "Google giriş başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Google hesabı ile Firebase'e giriş yap
     */
    private fun signInWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        showLoading("${account.email} ile giriş yapılıyor...")

        lifecycleScope.launch {
            try {
                println("DEBUG: Firebase ile Google giriş başlıyor - ${account.email}")

                val result = firebaseManager.signInWithGoogle(account)

                if (result.isSuccess) {
                    val user = result.getOrNull()
                    println("DEBUG: Google giriş başarılı: ${user?.displayName}, ${user?.email}")

                    hideLoading()

                    // Başarı mesajı göster
                    Toast.makeText(
                        this@LoginActivity,
                        "Hoş geldiniz, ${user?.displayName ?: user?.email}! 🎉",
                        Toast.LENGTH_LONG
                    ).show()

                    // Kısa bekleme sonrası navigation (UI responsive olsun)
                    binding.root.postDelayed({
                        println("DEBUG: GroupSelectionActivity'ye yönlendiriliyor...")
                        navigateToGroupSelection()
                    }, 1500) // 1.5 saniye bekle

                } else {
                    hideLoading()
                    val error = result.exceptionOrNull()
                    println("DEBUG: Google giriş başarısız: ${error?.message}")

                    // Firestore hatası olsa bile kullanıcı giriş yapmışsa devam et
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        println("DEBUG: Firestore hatası var ama kullanıcı giriş yapmış, devam ediliyor")

                        Toast.makeText(
                            this@LoginActivity,
                            "Giriş başarılı! (Bazı özellikler sınırlı olabilir)",
                            Toast.LENGTH_LONG
                        ).show()

                        binding.root.postDelayed({
                            navigateToGroupSelection()
                        }, 1000)
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Gmail giriş başarısız: ${error?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                hideLoading()
                println("DEBUG: Google giriş exception: ${e.message}")
                e.printStackTrace()

                // Exception olsa bile kullanıcı giriş yapmışsa devam et
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    println("DEBUG: Exception var ama kullanıcı giriş yapmış, devam ediliyor")

                    Toast.makeText(
                        this@LoginActivity,
                        "Giriş tamamlandı! (Bağlantı sorunları olabilir)",
                        Toast.LENGTH_LONG
                    ).show()

                    binding.root.postDelayed({
                        navigateToGroupSelection()
                    }, 1000)
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Gmail giriş hatası: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Email giriş dialog'u göster
     */
    private fun showEmailLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_login_only, null)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_password)

        AlertDialog.Builder(this)
            .setTitle("📧 Email ile Giriş Yap")
            .setView(dialogView)
            .setPositiveButton("Giriş Yap") { _, _ ->
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    signInWithEmail(email, password)
                } else {
                    Toast.makeText(this, "Email ve şifre gerekli", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * Email ile giriş yap
     */
    private fun signInWithEmail(email: String, password: String) {
        showLoading("$email ile giriş yapılıyor...")

        lifecycleScope.launch {
            try {
                println("DEBUG: Email ile giriş başlıyor: $email")

                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    println("DEBUG: Email giriş başarılı: ${user.email}")

                    hideLoading()
                    Toast.makeText(this@LoginActivity,
                        "Hoş geldiniz, ${user.email}! 🎉",
                        Toast.LENGTH_SHORT).show()

                    binding.root.postDelayed({
                        navigateToGroupSelection()
                    }, 1000)
                } else {
                    hideLoading()
                    Toast.makeText(this@LoginActivity, "Email giriş başarısız", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                hideLoading()
                println("DEBUG: Email giriş hatası: ${e.message}")

                val errorMessage = when {
                    e.message?.contains("invalid-email") == true -> "Geçersiz email adresi"
                    e.message?.contains("wrong-password") == true -> "Yanlış şifre"
                    e.message?.contains("user-not-found") == true -> "Bu email ile kayıtlı kullanıcı bulunamadı"
                    e.message?.contains("too-many-requests") == true -> "Çok fazla deneme yapıldı, lütfen bekleyin"
                    else -> "Giriş hatası: ${e.message}"
                }

                Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Grup seçim ekranına git - Güvenli navigation
     */
    private fun navigateToGroupSelection() {
        try {
            println("DEBUG: GroupSelectionActivity'ye yönlendirme başlıyor...")

            val intent = Intent(this, GroupSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            println("DEBUG: Intent oluşturuldu, startActivity çağrılıyor...")
            startActivity(intent)

            println("DEBUG: startActivity çağrıldı, finish() çağrılıyor...")
            finish()

            println("DEBUG: Navigation tamamlandı")
        } catch (e: Exception) {
            println("DEBUG: Navigation hatası: ${e.message}")
            e.printStackTrace()

            Toast.makeText(this, "Sayfa yönlendirme hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Loading göstergisini göster
     */
    private fun showLoading(message: String) {
        runOnUiThread {
            binding.progressLoading.visibility = View.VISIBLE
            binding.tvLoadingStatus.visibility = View.VISIBLE
            binding.tvLoadingStatus.text = message
            binding.btnGoogleSignin.isEnabled = false
            binding.btnEmailSignin.isEnabled = false
            binding.btnRegister.isEnabled = false

            println("DEBUG: Loading gösteriliyor: $message")
        }
    }

    /**
     * Loading göstergisini gizle
     */
    private fun hideLoading() {
        runOnUiThread {
            binding.progressLoading.visibility = View.GONE
            binding.tvLoadingStatus.visibility = View.GONE
            binding.btnGoogleSignin.isEnabled = true
            binding.btnEmailSignin.isEnabled = true
            binding.btnRegister.isEnabled = true

            println("DEBUG: Loading gizlendi")
        }
    }

    override fun onResume() {
        super.onResume()
        println("DEBUG: LoginActivity onResume")
    }

    override fun onPause() {
        super.onPause()
        println("DEBUG: LoginActivity onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        println("DEBUG: LoginActivity onDestroy")
    }
}