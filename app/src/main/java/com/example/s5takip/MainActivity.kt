package com.fabrika.s5takip

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fabrika.s5takip.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*


/**
 * Ana sayfa - Basit başlangıç versiyonu
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Veritabanı ve SharedPreferences'ı başlat
        databaseHelper = DatabaseHelper(this)
        sharedPreferences = getSharedPreferences("s5_takip_prefs", MODE_PRIVATE)

        // Test kullanıcısı oluştur
        createTestUser()

        // Ekranı ayarla
        setupUI()
        setupClickListeners()
        updateStats()
    }

    private fun createTestUser() {
        // Mevcut kullanıcıyı kontrol et
        val userId = sharedPreferences.getString("current_user_id", null)
        if (userId != null) {
            currentUser = databaseHelper.getUserById(userId)
        }

        // Eğer kullanıcı yoksa oluştur
        if (currentUser == null) {
            val testUser = User(
                name = "Test Denetmen",
                email = "test@fabrika.com",
                department = "Kalıp Üretim", // Bu satır eklendi
                role = UserRole.AUDITOR
            )

            val success = databaseHelper.insertUser(testUser)
            if (success) {
                currentUser = testUser
                // Kullanıcıyı kaydet
                sharedPreferences.edit()
                    .putString("current_user_id", testUser.id)
                    .apply()
            }
        }
    }

    /**
     * Kullanıcı arayüzünü ayarla
     */
    private fun setupUI() {
        // Hoş geldin mesajı
        binding.tvWelcome.text = "Hoş Geldiniz, ${currentUser?.name ?: "Kullanıcı"}"

        // Bugünün tarihi
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr", "TR"))
        binding.tvDate.text = dateFormat.format(Date())

        // Kullanıcı bilgisi
        binding.tvUserInfo.text = "${currentUser?.name} - ${currentUser?.role?.name} - ${currentUser?.department}"

        // Butonları kullanıcı rolüne göre ayarla
        when (currentUser?.role) {
            UserRole.AUDITOR -> {
                // Denetmen - Tüm butonları göster
                binding.btnAddProblem.visibility = View.VISIBLE
                binding.btnGenerateReport.visibility = View.VISIBLE
            }
            UserRole.USER -> {
                // Kullanıcı - Sadece problemleri görüntüleme
                binding.btnAddProblem.visibility = View.GONE
                binding.btnGenerateReport.visibility = View.GONE
            }
            else -> {
                // Güvenlik için tüm butonları gizle
                binding.btnAddProblem.visibility = View.GONE
                binding.btnGenerateReport.visibility = View.GONE
            }
        }
    }

    /**
     * Buton tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Problem ekleme butonu - AddProblemActivity'yi aç
        binding.btnAddProblem.setOnClickListener {
            if (currentUser?.role == UserRole.AUDITOR) {
                val intent = Intent(this, AddProblemActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Bu işlem için denetmen yetkisi gerekli", Toast.LENGTH_SHORT).show()
            }
        }

        // Problemleri görüntüleme butonu - ProblemsListActivity'yi aç
        binding.btnViewProblems.setOnClickListener {
            val intent = Intent(this, ProblemsListActivity::class.java)
            startActivity(intent)
        }

        // Rapor oluşturma butonu - ReportActivity'yi aç
        binding.btnGenerateReport.setOnClickListener {
            if (currentUser?.role == UserRole.AUDITOR) {
                val intent = Intent(this, ReportActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Bu işlem için denetmen yetkisi gerekli", Toast.LENGTH_SHORT).show()
            }
        }

        // Kullanıcı değiştirme butonu - Test kullanıcısını yeniden oluştur
        binding.btnChangeUser.setOnClickListener {
            // Test için farklı bir kullanıcı oluştur
            val newTestUser = if (currentUser?.role == UserRole.AUDITOR) {
                User(
                    name = "Test Kullanıcı 2",
                    email = "test2@fabrika.com",
                    department = "Montaj",
                    role = UserRole.USER
                )
            } else {
                User(
                    name = "Test Denetmen",
                    email = "denetmen@fabrika.com",
                    department = "Kalıp Üretim",
                    role = UserRole.AUDITOR
                )
            }

            val success = databaseHelper.insertUser(newTestUser)
            if (success) {
                currentUser = newTestUser
                sharedPreferences.edit()
                    .putString("current_user_id", newTestUser.id)
                    .apply()

                // Ekranı yenile
                setupUI()
                updateStats()

                Toast.makeText(this, "Kullanıcı değiştirildi: ${newTestUser.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * İstatistikleri güncelle
     */
    private fun updateStats() {
        val today = getCurrentDate()
        val stats = databaseHelper.getStatsForDate(today)

        // İstatistikleri ekranda göster
        binding.tvTotalProblems.text = stats.totalProblems.toString()
        binding.tvResolvedProblems.text = (stats.resolvedProblems + stats.verifiedProblems).toString()
        binding.tvResolutionRate.text = "${stats.resolutionRate.toInt()}%"
    }

    /**
     * Sayfa yeniden görünür olduğunda istatistikleri güncelle
     */
    override fun onResume() {
        super.onResume()
        updateStats()
    }
}