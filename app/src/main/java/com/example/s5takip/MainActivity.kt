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
 * Ana sayfa - Tam çalışır versiyon - GÜNCELLENMİŞ
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

    companion object {
        private const val EDIT_PROFILE_REQUEST = 1001
        private const val GROUP_SETTINGS_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        loadGroupInfo()
        loadTodaysAuditor()
        setupUI()
        setupClickListeners()
        updateStats()
        loadUserProfile()
    }

    /**
     * Bileşenleri başlat
     */
    private fun initializeComponents() {
        databaseHelper = DatabaseHelper(this)
        sharedPreferences = getSharedPreferences("s5_takip_prefs", MODE_PRIVATE)
        firebaseManager = FirebaseManager.getInstance()
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
     * Bugünün denetmenini yükle - GÜNCELLENMİŞ VERSİYON
     */
    private fun loadTodaysAuditor() {
        if (currentGroupId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val result = firebaseManager.getWeeklyAuditors(currentGroupId)
                if (result.isSuccess) {
                    val weeklyAuditors = result.getOrNull() ?: emptyList()

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

                    val todaysAuditorAssignment = weeklyAuditors.find { it.weekDay == dayOfWeek }

                    if (todaysAuditorAssignment != null) {
                        val membersResult = firebaseManager.getGroupMembers(currentGroupId)
                        if (membersResult.isSuccess) {
                            val members = membersResult.getOrNull() ?: emptyList()
                            todaysAuditor = members.find { it.userId == todaysAuditorAssignment.auditorId }

                            runOnUiThread {
                                updateTodaysAuditorDisplay()
                            }
                        }
                    } else {
                        runOnUiThread {
                            updateTodaysAuditorDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Bugünün denetmeni yüklenirken hata: ${e.message}")
            }
        }
    }

    /**
     * Bugünün denetmeni gösterimini güncelle - GÜNCELLENMİŞ VERSİYON
     */
    private fun updateTodaysAuditorDisplay() {
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser

        var isGroupOwner = false
        var isGroupAdmin = false

        if (currentGroupId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val membersResult = firebaseManager.getGroupMembers(currentGroupId)
                    if (membersResult.isSuccess) {
                        val members = membersResult.getOrNull() ?: emptyList()
                        val currentUserMember = members.find { it.userId == currentFirebaseUser?.uid }

                        isGroupOwner = currentUserMember?.role == GroupRoles.OWNER
                        isGroupAdmin = currentUserMember?.role == GroupRoles.ADMIN

                        runOnUiThread {
                            updateProblemAddPermission(isGroupOwner, isGroupAdmin)
                        }
                    }
                } catch (e: Exception) {
                    println("DEBUG: Grup sahipliği kontrolü hatası: ${e.message}")
                }
            }
        }

        if (todaysAuditor != null) {
            binding.tvTodaysAuditor.text = "Bugün Denetmen: ${todaysAuditor!!.userName}"
            binding.tvTodaysAuditor.visibility = View.VISIBLE

            val isTodaysAuditor = currentFirebaseUser?.uid == todaysAuditor!!.userId
            val canAddProblem = isTodaysAuditor || isGroupOwner || isGroupAdmin

            binding.btnAddProblem.isEnabled = canAddProblem
            binding.btnAddProblem.alpha = if (canAddProblem) 1.0f else 0.5f

            when {
                isTodaysAuditor -> {
                    binding.tvAuditorInfo.text = "Siz bugünün denetmenisiniz, problem ekleyebilirsiniz"
                    binding.tvAuditorInfo.visibility = View.VISIBLE
                }
                isGroupOwner -> {
                    binding.tvAuditorInfo.text = "Grup sahibi olarak her zaman problem ekleyebilirsiniz"
                    binding.tvAuditorInfo.visibility = View.VISIBLE
                }
                isGroupAdmin -> {
                    binding.tvAuditorInfo.text = "Yönetici olarak problem ekleyebilirsiniz"
                    binding.tvAuditorInfo.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvAuditorInfo.text = "Sadece bugünün denetmeni, grup sahibi veya yönetici problem ekleyebilir"
                    binding.tvAuditorInfo.visibility = View.VISIBLE
                }
            }
        } else {
            binding.tvTodaysAuditor.text = "Bugün için atanmış denetmen yok"
            binding.tvTodaysAuditor.visibility = View.VISIBLE

            val canAddProblem = isGroupOwner || isGroupAdmin
            binding.btnAddProblem.isEnabled = canAddProblem
            binding.btnAddProblem.alpha = if (canAddProblem) 1.0f else 0.5f

            when {
                isGroupOwner -> {
                    binding.tvAuditorInfo.text = "Grup sahibi olarak problem ekleyebilirsiniz"
                }
                isGroupAdmin -> {
                    binding.tvAuditorInfo.text = "Yönetici olarak problem ekleyebilirsiniz"
                }
                else -> {
                    binding.tvAuditorInfo.text = "Lütfen grup ayarlarından haftalık denetmen ataması yapın"
                }
            }
            binding.tvAuditorInfo.visibility = View.VISIBLE
        }
    }

    /**
     * Kullanıcı profilini yükle - GÜNCELLENMİŞ VERSİYON
     * Grup değişikliklerini yansıtır
     */
    private fun loadUserProfile() {
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        if (currentFirebaseUser != null) {
            // Kullanıcı adı - güncel Firebase profilinden al
            val displayName = currentFirebaseUser.displayName ?: "Kullanıcı"
            binding.tvUserName.text = displayName
            binding.tvUserEmail.text = currentFirebaseUser.email ?: ""

            println("DEBUG: Kullanıcı profili yüklendi: $displayName")

            // Profil fotoğrafı - Basit yükleme
            val photoUrl = currentFirebaseUser.photoUrl
            if (photoUrl != null) {
                try {
                    binding.ivUserProfile.setImageURI(photoUrl)
                    binding.ivUserProfile.visibility = View.VISIBLE
                    binding.tvAvatarLetter.visibility = View.GONE
                } catch (e: Exception) {
                    showAvatarLetter(currentFirebaseUser)
                }
            } else {
                showAvatarLetter(currentFirebaseUser)
            }

            // Hoş geldin mesajını güncelle
            binding.tvWelcome.text = "Hoş Geldiniz, $displayName"
        }
    }

    /**
     * Avatar harfini göster - DÜZELTILMIŞ: önce fotoğrafı dene
     */
    private fun showAvatarLetter(user: com.google.firebase.auth.FirebaseUser) {
        // ✅ Önce profil fotoğrafını yüklemeyi dene
        val photoUrl = user.photoUrl
        if (photoUrl != null) {
            try {
                println("DEBUG: Profil fotoğrafı yükleniyor: $photoUrl")
                binding.ivUserProfile.setImageURI(photoUrl)
                binding.ivUserProfile.visibility = View.VISIBLE
                binding.tvAvatarLetter.visibility = View.GONE
                println("DEBUG: ✅ Profil fotoğrafı başarıyla yüklendi")
                return
            } catch (e: Exception) {
                println("DEBUG: Profil fotoğrafı yükleme hatası: ${e.message}")
            }
        }

        // Fotoğraf yüklenemediyse harf göster
        val firstLetter = (user.displayName?.take(1) ?: user.email?.take(1) ?: "U").uppercase()
        binding.tvAvatarLetter.text = firstLetter
        binding.tvAvatarLetter.visibility = View.VISIBLE
        binding.ivUserProfile.visibility = View.GONE
        println("DEBUG: Avatar harf gösteriliyor: $firstLetter")
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
        val displayName = currentFirebaseUser?.displayName ?: "Kullanıcı"
        binding.tvWelcome.text = "Hoş Geldiniz, $displayName"

        // Bugünün tarihi
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr", "TR"))
        binding.tvDate.text = dateFormat.format(Date())

        // Grup ayarları butonunu göster
        binding.btnGroupSettings.visibility = if (currentGroupId.isNotEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Problem ekleme yetkisini güncelle - GÜNCELLENMİŞ VERSİYON
     */
    private fun updateProblemAddPermission(isGroupOwner: Boolean, isGroupAdmin: Boolean = false) {
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        val isTodaysAuditor = currentFirebaseUser?.uid == todaysAuditor?.userId

        val canAddProblem = isTodaysAuditor || isGroupOwner || isGroupAdmin

        binding.btnAddProblem.isEnabled = canAddProblem
        binding.btnAddProblem.alpha = if (canAddProblem) 1.0f else 0.5f

        println("DEBUG: Problem ekleme yetkisi güncellendi - Ekleyebilir: $canAddProblem")
    }

    /**
     * Buton tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Problem ekleme butonu - GÜNCELLENMİŞ VERSİYON
        binding.btnAddProblem.setOnClickListener {
            if (binding.btnAddProblem.isEnabled) {
                val intent = Intent(this, AddProblemActivity::class.java)
                intent.putExtra("group_id", currentGroupId) // ✅ Grup ID'si gönder
                startActivity(intent)
            } else {
                Toast.makeText(this, "Sadece bugünün denetmeni, grup sahibi veya yönetici problem ekleyebilir", Toast.LENGTH_SHORT).show()
            }
        }

        // Problemleri görüntüleme butonu - Grup ID'si ile
        binding.btnViewProblems.setOnClickListener {
            val intent = Intent(this, ProblemsListActivity::class.java)
            intent.putExtra("group_id", currentGroupId) // ✅ Grup ID'si gönder
            startActivity(intent)
        }

        // Rapor oluşturma butonu - Grup ID'si ile
        binding.btnGenerateReport.setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            intent.putExtra("group_id", currentGroupId) // ✅ Grup ID'si gönder
            startActivity(intent)
        }

        // Grup sohbeti butonu
        binding.btnGroupChat.setOnClickListener {
            val intent = Intent(this, GroupChatActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            intent.putExtra("group_name", selectedGroup?.name ?: "Grup")
            startActivity(intent)
        }

        // Grup ayarları butonu - GÜNCELLENMİŞ VERSİYON
        binding.btnGroupSettings.setOnClickListener {
            val intent = Intent(this, GroupSettingsActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            intent.putExtra("group_name", selectedGroup?.name ?: "Grup")
            startActivityForResult(intent, GROUP_SETTINGS_REQUEST)
        }

        // Profil düzenleme butonu
        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivityForResult(intent, EDIT_PROFILE_REQUEST)
        }

        // Çıkış butonu
        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    /**
     * Çıkış işlemi
     */
    private fun performLogout() {
        lifecycleScope.launch {
            try {
                firebaseManager.signOut()

                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Çıkış hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * İstatistikleri güncelle
     */
    private fun updateStats() {
        val today = getCurrentDate()
        val stats = if (currentGroupId.isNotEmpty()) {
            databaseHelper.getStatsForGroupAndDate(currentGroupId, today)
        } else {
            databaseHelper.getStatsForDate(today) // Fallback eski fonksiyon
        }

        // İstatistikleri ekranda göster
        binding.tvTotalProblems.text = stats.totalProblems.toString()
        binding.tvResolvedProblems.text = (stats.resolvedProblems + stats.verifiedProblems).toString()
        binding.tvResolutionRate.text = "${stats.resolutionRate.toInt()}%"
    }

    /**
     * Activity sonucu işle - GÜNCELLENMİŞ VERSİYON
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            EDIT_PROFILE_REQUEST -> {
                if (resultCode == RESULT_OK) {
                    println("DEBUG: Profil düzenleme tamamlandı, kullanıcı bilgileri yenileniyor")
                    // Profil düzenlendikten sonra anında güncelle
                    loadUserProfile()
                    setupUI() // Hoş geldin mesajını da güncelle

                    // Bugünün denetmeni bilgilerini de yenile (isim değişmiş olabilir)
                    loadTodaysAuditor()
                }
            }
            GROUP_SETTINGS_REQUEST -> {
                if (resultCode == RESULT_OK) {
                    println("DEBUG: Grup ayarları değişti, denetmen bilgileri yenileniyor")
                    // Grup ayarları değişti, denetmen bilgilerini yenile
                    loadTodaysAuditor()
                }
            }
        }
    }

    /**
     * Sayfa yeniden görünür olduğunda - GÜNCELLENMİŞ VERSİYON
     */
    override fun onResume() {
        super.onResume()
        println("DEBUG: MainActivity onResume - Bilgiler yenileniyor")

        updateStats()
        loadTodaysAuditor() // Denetmen bilgilerini yenile
        loadUserProfile() // Profil bilgilerini yenile

        // Firebase kullanıcısını yeniden kontrol et (profil güncellemeleri için)
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        currentFirebaseUser?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                println("DEBUG: Firebase kullanıcı bilgileri yenilendi")
                loadUserProfile()
            }
        }
    }
}