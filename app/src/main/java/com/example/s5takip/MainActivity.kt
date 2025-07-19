package com.fabrika.s5takip

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fabrika.s5takip.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Ana sayfa - Grup bilgileri ve haftalık denetmen yönetimi ile
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var firebaseManager: FirebaseManager

    private var currentUser: User? = null
    private var selectedGroup: Group? = null
    private var currentGroupId: String = ""
    private var todaysAuditor: GroupMember? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Veritabanı ve SharedPreferences'ı başlat
        databaseHelper = DatabaseHelper(this)
        sharedPreferences = getSharedPreferences("s5_takip_prefs", MODE_PRIVATE)
        firebaseManager = FirebaseManager.getInstance()

        // Intent'ten grup bilgilerini al
        loadGroupInfo()

        // Test kullanıcısı oluştur
        createTestUser()

        // Bugünün denetmenini yükle
        loadTodaysAuditor()

        // Ekranı ayarla
        setupUI()
        setupClickListeners()
        updateStats()
    }

    /**
     * Grup bilgilerini intent'ten yükle
     */
    private fun loadGroupInfo() {
        currentGroupId = intent.getStringExtra("selected_group_id") ?: ""
        val groupName = intent.getStringExtra("selected_group_name") ?: "Grup"

        if (currentGroupId.isNotEmpty()) {
            selectedGroup = Group(
                id = currentGroupId,
                name = groupName
            )
            println("DEBUG: Grup bilgileri yüklendi - ID: $currentGroupId, Ad: $groupName")
        } else {
            println("DEBUG: Grup bilgisi bulunamadı!")
        }
    }

    /**
     * Bugünün denetmenini yükle
     */
    private fun loadTodaysAuditor() {
        if (currentGroupId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val result = firebaseManager.getWeeklyAuditors(currentGroupId)
                if (result.isSuccess) {
                    val weeklyAuditors = result.getOrNull() ?: emptyList()

                    // Bugünün günü (1=Pazartesi, 7=Pazar)
                    val calendar = Calendar.getInstance()
                    val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        Calendar.SUNDAY -> 7
                        else -> 1
                    }

                    // Bugünün denetmenini bul
                    val todaysAuditorAssignment = weeklyAuditors.find { it.weekDay == dayOfWeek }

                    if (todaysAuditorAssignment != null) {
                        // Grup üyelerini getir ve bugünün denetmenini bul
                        val membersResult = firebaseManager.getGroupMembers(currentGroupId)
                        if (membersResult.isSuccess) {
                            val members = membersResult.getOrNull() ?: emptyList()
                            todaysAuditor = members.find { it.userId == todaysAuditorAssignment.auditorId }

                            runOnUiThread {
                                updateTodaysAuditorDisplay()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Bugünün denetmeni yüklenirken hata: ${e.message}")
            }
        }
    }

    /**
     * Bugünün denetmeni gösterimini güncelle
     */
    private fun updateTodaysAuditorDisplay() {
        if (todaysAuditor != null) {
            binding.tvTodaysAuditor.text = "Bugünün Denetmeni: ${todaysAuditor!!.userName}"
            binding.tvTodaysAuditor.visibility = View.VISIBLE

            // Sadece bugünün denetmeni problem ekleyebilir
            val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
            val canAddProblem = currentFirebaseUser?.uid == todaysAuditor!!.userId

            binding.btnAddProblem.isEnabled = canAddProblem
            binding.btnAddProblem.alpha = if (canAddProblem) 1.0f else 0.5f

            if (!canAddProblem) {
                binding.tvAuditorInfo.text = "Sadece bugünün denetmeni problem ekleyebilir"
                binding.tvAuditorInfo.visibility = View.VISIBLE
            } else {
                binding.tvAuditorInfo.visibility = View.GONE
            }
        } else {
            binding.tvTodaysAuditor.text = "Bugün için atanmış denetmen yok"
            binding.tvTodaysAuditor.visibility = View.VISIBLE
            binding.btnAddProblem.isEnabled = false
            binding.btnAddProblem.alpha = 0.5f
            binding.tvAuditorInfo.text = "Lütfen grup ayarlarından haftalık denetmen ataması yapın"
            binding.tvAuditorInfo.visibility = View.VISIBLE
        }
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
                department = "Kalıp Üretim",
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
        // Grup bilgilerini göster
        if (selectedGroup != null) {
            binding.tvGroupName.text = selectedGroup!!.name
            binding.tvGroupName.visibility = View.VISIBLE
        }

        // Hoş geldin mesajı
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        val displayName = currentFirebaseUser?.displayName ?: currentUser?.name ?: "Kullanıcı"
        binding.tvWelcome.text = "Hoş Geldiniz, $displayName"

        // Bugünün tarihi
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr", "TR"))
        binding.tvDate.text = dateFormat.format(Date())

        // Kullanıcı bilgisi
        val userEmail = currentFirebaseUser?.email ?: currentUser?.email ?: "email@example.com"
        binding.tvUserInfo.text = "$displayName - $userEmail"

        // Profil fotoğrafı
        val photoUrl = currentFirebaseUser?.photoUrl
        if (photoUrl != null) {
            // Glide ile profil fotoğrafını yükle (gerçek uygulamada)
            // Glide.with(this).load(photoUrl).into(binding.ivUserProfile)
            binding.ivUserProfile.visibility = View.VISIBLE
        } else {
            binding.ivUserProfile.setImageResource(android.R.drawable.ic_menu_myplaces)
            binding.ivUserProfile.visibility = View.VISIBLE
        }

        // Grup ayarları butonunu göster
        binding.btnGroupSettings.visibility = if (currentGroupId.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Buton tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Problem ekleme butonu
        binding.btnAddProblem.setOnClickListener {
            // Sadece bugünün denetmeni problem ekleyebilir
            val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
            if (todaysAuditor != null && currentFirebaseUser?.uid == todaysAuditor!!.userId) {
                val intent = Intent(this, AddProblemActivity::class.java)
                intent.putExtra("group_id", currentGroupId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Sadece bugünün denetmeni problem ekleyebilir", Toast.LENGTH_SHORT).show()
            }
        }

        // Problemleri görüntüleme butonu
        binding.btnViewProblems.setOnClickListener {
            val intent = Intent(this, ProblemsListActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            startActivity(intent)
        }

        // Rapor oluşturma butonu
        binding.btnGenerateReport.setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            startActivity(intent)
        }

        // Grup ayarları butonu - YENİ!
        binding.btnGroupSettings.setOnClickListener {
            val intent = Intent(this, GroupSettingsActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            intent.putExtra("group_name", selectedGroup?.name ?: "Grup")
            startActivity(intent)
        }

        // Kullanıcı profili düzenleme butonu - YENİ!
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        // Kullanıcı değiştirme butonu - Test için
        binding.btnChangeUser.setOnClickListener {
            // Grup seçim ekranına geri dön
            val intent = Intent(this, GroupSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    /**
     * Profil düzenleme dialog'u göster
     */
    private fun showEditProfileDialog() {
        val intent = Intent(this, EditProfileActivity::class.java)
        startActivity(intent)
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
        loadTodaysAuditor() // Denetmen bilgilerini yenile
    }
}