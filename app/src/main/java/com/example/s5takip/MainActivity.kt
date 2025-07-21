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
 * Ana sayfa - Tam çalışır versiyon
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlangıç ayarları
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
     * Bugünün denetmeni gösterimini güncelle
     */
    private fun updateTodaysAuditorDisplay() {
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser

        // Grup sahibi kontrolü ekle
        var isGroupOwner = false
        if (currentGroupId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val membersResult = firebaseManager.getGroupMembers(currentGroupId)
                    if (membersResult.isSuccess) {
                        val members = membersResult.getOrNull() ?: emptyList()
                        val currentUserMember = members.find { it.userId == currentFirebaseUser?.uid }
                        isGroupOwner = currentUserMember?.role == GroupRoles.OWNER

                        runOnUiThread {
                            updateProblemAddPermission(isGroupOwner)
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

            // Bugünün denetmeni veya grup sahibi problem ekleyebilir
            val canAddProblem = currentFirebaseUser?.uid == todaysAuditor!!.userId || isGroupOwner

            binding.btnAddProblem.isEnabled = canAddProblem
            binding.btnAddProblem.alpha = if (canAddProblem) 1.0f else 0.5f

            if (!canAddProblem && !isGroupOwner) {
                binding.tvAuditorInfo.text = "Sadece bugünün denetmeni veya grup sahibi problem ekleyebilir"
                binding.tvAuditorInfo.visibility = View.VISIBLE
            } else {
                binding.tvAuditorInfo.visibility = View.GONE
            }
        } else {
            binding.tvTodaysAuditor.text = "Bugün için atanmış denetmen yok"
            binding.tvTodaysAuditor.visibility = View.VISIBLE

            // Grup sahibi denetmen atanmamış olsa bile problem ekleyebilir
            binding.btnAddProblem.isEnabled = isGroupOwner
            binding.btnAddProblem.alpha = if (isGroupOwner) 1.0f else 0.5f

            if (isGroupOwner) {
                binding.tvAuditorInfo.text = "Grup sahibi olarak problem ekleyebilirsiniz"
            } else {
                binding.tvAuditorInfo.text = "Lütfen grup ayarlarından haftalık denetmen ataması yapın"
            }
            binding.tvAuditorInfo.visibility = View.VISIBLE
        }
    }

    /**
     * Kullanıcı profilini yükle
     */
    private fun loadUserProfile() {
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        if (currentFirebaseUser != null) {
            // Kullanıcı adı
            binding.tvUserName.text = currentFirebaseUser.displayName ?: "Kullanıcı"
            binding.tvUserEmail.text = currentFirebaseUser.email ?: ""

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
        }
    }

    /**
     * Avatar harfini göster
     */
    private fun showAvatarLetter(user: com.google.firebase.auth.FirebaseUser) {
        val firstLetter = (user.displayName?.take(1) ?: user.email?.take(1) ?: "U").uppercase()
        binding.tvAvatarLetter.text = firstLetter
        binding.tvAvatarLetter.visibility = View.VISIBLE
        binding.ivUserProfile.visibility = View.GONE
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
     * Problem ekleme yetkisini güncelle
     */
    private fun updateProblemAddPermission(isGroupOwner: Boolean) {
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        val isTodaysAuditor = currentFirebaseUser?.uid == todaysAuditor?.userId

        val canAddProblem = isTodaysAuditor || isGroupOwner

        binding.btnAddProblem.isEnabled = canAddProblem
        binding.btnAddProblem.alpha = if (canAddProblem) 1.0f else 0.5f

        // setupClickListeners metodundaki problem ekleme butonunu da güncelle
        binding.btnAddProblem.setOnClickListener {
            if (canAddProblem) {
                val intent = Intent(this, AddProblemActivity::class.java)
                intent.putExtra("group_id", currentGroupId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Sadece bugünün denetmeni veya grup sahibi problem ekleyebilir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Buton tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Problem ekleme butonu
        binding.btnAddProblem.setOnClickListener {
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

        // Grup sohbeti butonu
        binding.btnGroupChat.setOnClickListener {
            val intent = Intent(this, GroupChatActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            intent.putExtra("group_name", selectedGroup?.name ?: "Grup")
            startActivity(intent)
        }

        // Grup ayarları butonu
        binding.btnGroupSettings.setOnClickListener {
            val intent = Intent(this, GroupSettingsActivity::class.java)
            intent.putExtra("group_id", currentGroupId)
            intent.putExtra("group_name", selectedGroup?.name ?: "Grup")
            startActivity(intent)
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
        val stats = databaseHelper.getStatsForDate(today)

        // İstatistikleri ekranda göster
        binding.tvTotalProblems.text = stats.totalProblems.toString()
        binding.tvResolvedProblems.text = (stats.resolvedProblems + stats.verifiedProblems).toString()
        binding.tvResolutionRate.text = "${stats.resolutionRate.toInt()}%"
    }

    /**
     * Activity sonucu işle
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_PROFILE_REQUEST && resultCode == RESULT_OK) {
            // Profil düzenlendikten sonra anında güncelle
            loadUserProfile()
            setupUI() // Hoş geldin mesajını da güncelle
        }
    }

    /**
     * Sayfa yeniden görünür olduğunda istatistikleri güncelle
     */
    override fun onResume() {
        super.onResume()
        updateStats()
        loadTodaysAuditor() // Denetmen bilgilerini yenile
        loadUserProfile() // Profil bilgilerini yenile
    }
}