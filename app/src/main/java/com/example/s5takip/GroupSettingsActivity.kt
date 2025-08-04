package com.fabrika.s5takip

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabrika.s5takip.databinding.ActivityGroupSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Grup ayarları ekranı - 7 GÜN ZORLA GÖSTERİM VERSİYONU
 * OWNER: Her şeyi yapabilir + Grubu silebilir
 * ADMIN: Denetmen ataması yapabilir
 * MEMBER: Sadece görüntüleyebilir
 */
class GroupSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupSettingsBinding
    private lateinit var firebaseManager: FirebaseManager

    private var groupId: String = ""
    private var groupName: String = ""
    private var groupMembers = mutableListOf<GroupMember>()
    private var weeklyAuditors = mutableListOf<WeeklyAuditor>()
    private lateinit var membersAdapter: GroupMembersAdapter
    private lateinit var weeklyScheduleAdapter: WeeklyScheduleAdapter

    // Grup bilgisi (silme işlemi için gerekli)
    private var selectedGroup: Group? = null

    // Basit yetki kontrolleri
    private var currentUserRole: String = GroupRoles.MEMBER
    private var canManageAuditors: Boolean = false

    companion object {
        private const val GROUP_SETTINGS_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent'ten grup bilgilerini al
        groupId = intent.getStringExtra("group_id") ?: ""
        groupName = intent.getStringExtra("group_name") ?: "Grup"

        // Grup objesi oluştur (silme işlemi için gerekli)
        if (groupId.isNotEmpty()) {
            selectedGroup = Group(
                id = groupId,
                name = groupName
            )
        }

        if (groupId.isEmpty()) {
            Toast.makeText(this, "Grup bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Firebase manager'ı başlat
        firebaseManager = FirebaseManager.getInstance()

        // UI'ı ayarla
        setupUI()
        setupRecyclerViews()
        setupClickListeners()
        loadGroupData()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "$groupName - Ayarlar"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        println("DEBUG: ✅ GroupSettingsActivity başlatıldı - Her zaman 7 gün gösterilecek")
    }

    /**
     * UI'ı ayarla
     */
    private fun setupUI() {
        binding.tvGroupNameTitle.text = groupName
        binding.btnShareInviteCode.visibility = View.GONE
        binding.btnDeleteGroup.visibility = View.GONE
    }

    /**
     * RecyclerView'ları ayarla - 7 GÜN ZORLA GÖSTERİM
     */
    private fun setupRecyclerViews() {
        println("DEBUG: ==================== RECYCLERVIEW SETUP - 7 GÜN ZORLA====================")

        // Grup üyeleri adapter - Rol değiştirme için güncellenmiş
        membersAdapter = GroupMembersAdapter(groupMembers) { member ->
            println("DEBUG: Üyeye tıklandı: ${member.userName} (${member.role})")

            // OWNER ise rol değiştirme dialog'u, değilse profil göster
            if (currentUserRole == GroupRoles.OWNER && member.role != GroupRoles.OWNER) {
                showMemberRoleDialog(member)
            } else {
                showMemberProfile(member)
            }
        }
        binding.rvGroupMembers.layoutManager = LinearLayoutManager(this)
        binding.rvGroupMembers.adapter = membersAdapter

        // ✅ YENİ: Haftalık program adapter - 7 GÜN ZORLA GÖSTERİM
        weeklyScheduleAdapter = WeeklyScheduleAdapter(weeklyAuditors, groupMembers) { weekDay ->
            println("DEBUG: Gün tıklandı: $weekDay (${getDayName(weekDay)})")
            println("DEBUG: canManageAuditors: $canManageAuditors")

            if (canManageAuditors) {
                println("DEBUG: Yönetici - denetmen atama dialog'u açılıyor")
                showAuditorSelectionDialog(weekDay)
            } else {
                println("DEBUG: Normal kullanıcı - sadece görüntüleme")
                showReadOnlyScheduleInfo(weekDay)
            }
        }
        binding.rvWeeklySchedule.layoutManager = LinearLayoutManager(this)
        binding.rvWeeklySchedule.adapter = weeklyScheduleAdapter

        println("DEBUG: ✅ RecyclerView'lar başarıyla ayarlandı - WeeklyScheduleAdapter her zaman 7 gün gösterecek")
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        binding.btnShareInviteCode.setOnClickListener {
            shareInviteCode()
        }

        binding.btnDeleteGroup.setOnClickListener {
            showDeleteGroupConfirmation()
        }
    }

    /**
     * Kullanıcının yetkilerini kontrol et - BASİTLEŞTİRİLMİŞ
     */
    private fun checkUserPermissions() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val currentUserMember = groupMembers.find { it.userId == currentUserId }

        currentUserRole = currentUserMember?.role ?: GroupRoles.MEMBER
        canManageAuditors = (currentUserRole == GroupRoles.OWNER || currentUserRole == GroupRoles.ADMIN)

        println("DEBUG: ==================== YETKİ KONTROLÜ ====================")
        println("DEBUG: Mevcut kullanıcı ID: $currentUserId")
        println("DEBUG: Mevcut kullanıcı rolü: $currentUserRole")
        println("DEBUG: Denetmen ataması yapabilir mi: $canManageAuditors")
        println("DEBUG: ==================================================")

        updateUIBasedOnPermissions()
    }

    /**
     * Yetkilere göre UI'ı güncelle - BASİTLEŞTİRİLMİŞ
     */
    private fun updateUIBasedOnPermissions() {
        when (currentUserRole) {
            GroupRoles.OWNER -> {
                binding.btnShareInviteCode.visibility = View.VISIBLE
                binding.btnDeleteGroup.visibility = View.VISIBLE
                binding.tvScheduleDescription.text = "👑 Grup sahibi olarak tüm ayarları yönetebilirsiniz. Günlere tıklayarak denetmen atayın."
            }
            GroupRoles.ADMIN -> {
                binding.btnShareInviteCode.visibility = View.GONE
                binding.btnDeleteGroup.visibility = View.GONE
                binding.tvScheduleDescription.text = "⭐ Yönetici olarak denetmen atamaları yapabilirsiniz. Günlere tıklayarak denetmen atayın."
            }
            else -> {
                binding.btnShareInviteCode.visibility = View.GONE
                binding.btnDeleteGroup.visibility = View.GONE
                binding.tvScheduleDescription.text = "👤 Haftalık denetmen programını görüntülüyorsunuz. Atama yetkisi için grup sahibi ile iletişime geçin."
            }
        }

        updateWeeklyScheduleTitle()
    }

    /**
     * Haftalık program başlığını güncelle
     */
    private fun updateWeeklyScheduleTitle() {
        val titleText = if (canManageAuditors) {
            "📅 Haftalık Denetmen Programı (7 Gün - Yönet)"
        } else {
            "📅 Haftalık Denetmen Programı (7 Gün - Görüntüle)"
        }
        binding.tvWeeklyScheduleTitle.text = titleText
    }

    /**
     * Üye rol değiştirme dialog'u
     */
    private fun showMemberRoleDialog(member: GroupMember) {
        val roles = arrayOf("👤 Normal Üye", "⭐ Yönetici")
        val currentRoleIndex = if (member.role == GroupRoles.ADMIN) 1 else 0

        AlertDialog.Builder(this)
            .setTitle("${member.userName} - Rol Değiştir")
            .setSingleChoiceItems(roles, currentRoleIndex) { dialog, which ->
                val newRole = if (which == 1) GroupRoles.ADMIN else GroupRoles.MEMBER

                dialog.dismiss()
                showConfirmRoleChange(member, newRole)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * Rol değiştirme onayı
     */
    private fun showConfirmRoleChange(member: GroupMember, newRole: String) {
        val roleText = if (newRole == GroupRoles.ADMIN) "Yönetici" else "Normal Üye"

        AlertDialog.Builder(this)
            .setTitle("Rol Değiştir")
            .setMessage("${member.userName} kişisinin rolünü '$roleText' yapmak istiyorsunuz. Onaylıyor musunuz?")
            .setPositiveButton("Evet") { _, _ ->
                updateMemberRole(member, newRole)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * Üye rolünü güncelle
     */
    private fun updateMemberRole(member: GroupMember, newRole: String) {
        lifecycleScope.launch {
            try {
                val updatedMember = member.copy(role = newRole)
                val result = firebaseManager.updateGroupMember(updatedMember)

                runOnUiThread {
                    if (result.isSuccess) {
                        // Listede güncelle
                        val index = groupMembers.indexOf(member)
                        if (index != -1) {
                            groupMembers[index] = updatedMember
                            membersAdapter.notifyItemChanged(index)
                        }

                        Toast.makeText(this@GroupSettingsActivity,
                            "✅ ${member.userName} rolü güncellendi", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@GroupSettingsActivity,
                            "❌ Rol güncellenemedi: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@GroupSettingsActivity,
                        "💥 Hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Üye profil bilgilerini göster
     */
    private fun showMemberProfile(member: GroupMember) {
        val roleText = when (member.role) {
            GroupRoles.OWNER -> "👑 Grup Sahibi"
            GroupRoles.ADMIN -> "⭐ Yönetici"
            else -> "👤 Normal Üye"
        }

        val message = """
        👤 Ad: ${member.userName}
        📧 E-posta: ${member.userEmail}
        🏷️ Rol: $roleText
        📅 Katılma: ${formatDate(member.joinedAt)}
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Üye Bilgileri")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    /**
     * Denetmen seçim dialog'u - DÜZELTİLMİŞ VERSİYON
     */
    private fun showAuditorSelectionDialog(weekDay: Int) {
        println("DEBUG: ==================== DENETMEN SEÇİM DIALOG'U ====================")
        println("DEBUG: canManageAuditors: $canManageAuditors")
        println("DEBUG: currentUserRole: $currentUserRole")
        println("DEBUG: Seçilen gün: ${getDayName(weekDay)}")

        val dayName = getDayName(weekDay)

        // TÜM ÜYELERİ GÖSTERİR - ROL FARKETMEZ
        val allMembers = groupMembers.filter {
            it.userId.isNotEmpty() && it.userName.isNotEmpty()
        }

        println("DEBUG: Toplam grup üyesi: ${groupMembers.size}")
        println("DEBUG: Denetmen seçimi için filtrelenmiş üye sayısı: ${allMembers.size}")

        if (allMembers.isEmpty()) {
            Toast.makeText(this, "❌ Grup üyesi bulunamadı!", Toast.LENGTH_LONG).show()
            return
        }

        // Yetki kontrolü
        if (!canManageAuditors) {
            showReadOnlyScheduleInfo(weekDay)
            return
        }

        // Üye adları ve rolleri
        val memberDisplayNames = allMembers.map { member ->
            val roleEmoji = when(member.role) {
                GroupRoles.OWNER -> "👑"
                GroupRoles.ADMIN -> "⭐"
                else -> "👤"
            }
            "$roleEmoji ${member.userName}"
        }.toTypedArray()

        // Mevcut denetmeni bul
        val currentAuditor = weeklyAuditors.find { it.weekDay == weekDay }
        val currentIndex = if (currentAuditor != null) {
            allMembers.indexOfFirst { it.userId == currentAuditor.auditorId }
        } else {
            -1
        }

        AlertDialog.Builder(this)
            .setTitle("$dayName Denetmeni Seç\n\nGrup üyeleri arasından birini seçin:")
            .setSingleChoiceItems(memberDisplayNames, currentIndex) { dialog, which ->
                val selectedMember = allMembers[which]
                dialog.dismiss()
                showConfirmAuditorAssignment(weekDay, selectedMember)
            }
            .setNegativeButton("❌ İptal", null)
            .setNeutralButton("🗑️ Denetmen Kaldır") { dialog, _ ->
                dialog.dismiss()
                if (currentAuditor != null) {
                    showConfirmAuditorRemoval(weekDay, currentAuditor)
                } else {
                    Toast.makeText(this, "Bu gün için zaten denetmen yok", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * Denetmen ataması onay dialog'u
     */
    private fun showConfirmAuditorAssignment(weekDay: Int, selectedMember: GroupMember) {
        val dayName = getDayName(weekDay)

        val confirmMessage = """
            ${selectedMember.userName} kişisini $dayName günü denetmeni yapmak istiyorsunuz.
            
            Bu kişi o gün:
            • Problem ekleyebilir
            • Çözümleri değerlendirebilir
            
            Onaylıyor musunuz?
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Denetmen Atamasını Onayla")
            .setMessage(confirmMessage)
            .setPositiveButton("✅ Evet, Ata") { _, _ ->
                assignAuditorToDay(weekDay, selectedMember)
            }
            .setNegativeButton("❌ İptal", null)
            .show()
    }

    /**
     * Denetmen kaldırma onay dialog'u
     */
    private fun showConfirmAuditorRemoval(weekDay: Int, auditor: WeeklyAuditor) {
        val dayName = getDayName(weekDay)

        AlertDialog.Builder(this)
            .setTitle("Denetmen Kaldır")
            .setMessage("$dayName günü denetmenini (${auditor.auditorName}) kaldırmak istiyorsunuz.\n\nOnaylıyor musunuz?")
            .setPositiveButton("✅ Evet, Kaldır") { _, _ ->
                removeAuditorFromDay(weekDay)
            }
            .setNegativeButton("❌ İptal", null)
            .show()
    }

    /**
     * Sadece okuma modunda program bilgisi göster
     */
    private fun showReadOnlyScheduleInfo(weekDay: Int) {
        val dayName = getDayName(weekDay)
        val auditor = weeklyAuditors.find { it.weekDay == weekDay }

        val message = if (auditor != null) {
            val member = groupMembers.find { it.userId == auditor.auditorId }
            "📅 $dayName günü denetmeni:\n👤 ${member?.userName ?: "Bilinmeyen"}"
        } else {
            "📅 $dayName günü için henüz denetmen atanmamış."
        }

        AlertDialog.Builder(this)
            .setTitle("Denetmen Bilgisi")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    /**
     * Denetmeni güne ata
     */
    private fun assignAuditorToDay(weekDay: Int, member: GroupMember) {
        val dayName = getDayName(weekDay)
        println("DEBUG: $dayName günü için denetmen atanıyor: ${member.userName}")

        lifecycleScope.launch {
            try {
                val existingAuditor = weeklyAuditors.find { it.weekDay == weekDay }

                val weeklyAuditor = WeeklyAuditor(
                    id = existingAuditor?.id ?: UUID.randomUUID().toString(),
                    groupId = groupId,
                    weekDay = weekDay,
                    auditorId = member.userId,
                    auditorName = member.userName,
                    assignedBy = firebaseManager.getCurrentUser()?.uid ?: "",
                    assignedAt = System.currentTimeMillis()
                )

                val result = firebaseManager.saveWeeklyAuditor(weeklyAuditor)

                runOnUiThread {
                    if (result.isSuccess) {
                        // Listeyi güncelle
                        if (existingAuditor != null) {
                            val index = weeklyAuditors.indexOf(existingAuditor)
                            weeklyAuditors[index] = weeklyAuditor
                        } else {
                            weeklyAuditors.add(weeklyAuditor)
                        }

                        // ✅ Adapter'ı yenile - 7 gün zorla gösterilecek
                        weeklyScheduleAdapter.notifyDataSetChanged()
                        println("DEBUG: ✅ Adapter notifyDataSetChanged() çağrıldı - Her zaman 7 gün gösterilir")

                        val successMessage = """
                            ✅ Denetmen ataması başarılı!
                            
                            📅 Gün: $dayName
                            👤 Denetmen: ${member.userName}
                            
                            ${member.userName} artık $dayName günü problem ekleyebilir.
                        """.trimIndent()

                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("Atama Tamamlandı")
                            .setMessage(successMessage)
                            .setPositiveButton("Tamam", null)
                            .show()

                        println("DEBUG: ✅ $dayName günü denetmeni atandı: ${member.userName}")
                    } else {
                        Toast.makeText(this@GroupSettingsActivity,
                            "❌ Atama başarısız: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                println("DEBUG: ❌ assignAuditorToDay exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    Toast.makeText(this@GroupSettingsActivity,
                        "💥 Beklenmeyen hata: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Günden denetmeni kaldır
     */
    private fun removeAuditorFromDay(weekDay: Int) {
        val dayName = getDayName(weekDay)
        val auditorToRemove = weeklyAuditors.find { it.weekDay == weekDay }

        if (auditorToRemove != null) {
            println("DEBUG: $dayName günü denetmeni kaldırılıyor: ${auditorToRemove.auditorName}")

            weeklyAuditors.remove(auditorToRemove)

            // ✅ Adapter'ı yenile - 7 gün zorla gösterilecek
            weeklyScheduleAdapter.notifyDataSetChanged()
            println("DEBUG: ✅ Denetmen kaldırıldı, adapter yenilendi - Her zaman 7 gün gösterilir")

            val successMessage = """
                ✅ Denetmen kaldırıldı!
                
                📅 Gün: $dayName
                👤 Kaldırılan: ${auditorToRemove.auditorName}
                
                Bu gün artık denetmensiz.
            """.trimIndent()

            AlertDialog.Builder(this@GroupSettingsActivity)
                .setTitle("İşlem Tamamlandı")
                .setMessage(successMessage)
                .setPositiveButton("Tamam", null)
                .show()

            println("DEBUG: ✅ $dayName günü denetmeni kaldırıldı")
        } else {
            Toast.makeText(this, "Bu gün için zaten denetmen yok", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Grup silme onay dialog'u
     */
    private fun showDeleteGroupConfirmation() {
        if (selectedGroup == null) {
            Toast.makeText(this, "Grup bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val confirmMessage = """
            ⚠️ DİKKAT! Bu işlem geri alınamaz!
            
            "${selectedGroup!!.name}" grubunu tamamen silmek istiyorsunuz.
            
            Silinecekler:
            • Tüm grup üyeleri
            • Haftalık denetmen atamaları
            • Grup chat mesajları
            • Grup verileri
            
            Bu işlemi onaylıyor musunuz?
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🗑️ Grubu Sil")
            .setMessage(confirmMessage)
            .setPositiveButton("🗑️ Evet, Sil") { _, _ ->
                performGroupDeletion()
            }
            .setNegativeButton("❌ İptal", null)
            .setNeutralButton("⚠️ Uyarı") { _, _ ->
                Toast.makeText(this, "Bu işlem geri alınamaz! Emin olduğunuzda tekrar deneyin.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    /**
     * Grup silme işlemini gerçekleştir
     */
    private fun performGroupDeletion() {
        if (selectedGroup == null) {
            Toast.makeText(this, "Grup bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        // Loading dialog göster
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("🗑️ Grup Siliniyor")
            .setMessage("${selectedGroup!!.name} grubu siliniyor...\n\nLütfen bekleyin.")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                println("DEBUG: Grup silme işlemi başlatıldı - ID: ${selectedGroup!!.id}")

                val result = firebaseManager.deleteGroup(selectedGroup!!.id)

                runOnUiThread {
                    loadingDialog.dismiss()

                    if (result.isSuccess) {
                        println("DEBUG: ✅ Grup başarıyla silindi!")

                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("✅ Grup Silindi")
                            .setMessage("${selectedGroup!!.name} grubu başarıyla silindi.\n\nAna sayfaya yönlendiriliyorsunuz.")
                            .setPositiveButton("Ana Sayfaya Git") { _, _ ->
                                val intent = Intent(this@GroupSettingsActivity, GroupSelectionActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            .setCancelable(false)
                            .show()

                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Bilinmeyen hata"
                        println("DEBUG: ❌ Grup silme başarısız: $error")

                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("❌ Grup Silinemedi")
                            .setMessage("Grup silinirken hata oluştu:\n\n$error\n\nLütfen tekrar deneyin.")
                            .setPositiveButton("Tamam", null)
                            .setNeutralButton("Tekrar Dene") { _, _ ->
                                showDeleteGroupConfirmation()
                            }
                            .show()
                    }
                }

            } catch (e: Exception) {
                println("DEBUG: ❌ Grup silme exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    loadingDialog.dismiss()

                    AlertDialog.Builder(this@GroupSettingsActivity)
                        .setTitle("💥 Beklenmeyen Hata")
                        .setMessage("Grup silinirken beklenmeyen bir hata oluştu:\n\n${e.message}")
                        .setPositiveButton("Tamam", null)
                        .setNeutralButton("Tekrar Dene") { _, _ ->
                            showDeleteGroupConfirmation()
                        }
                        .show()
                }
            }
        }
    }

    /**
     * Davet kodunu paylaş
     */
    private fun shareInviteCode() {
        if (selectedGroup == null) {
            Toast.makeText(this, "Grup bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val inviteMessage = """
            🎉 ${selectedGroup!!.name} grubuna davet edildiniz!
            
            📱 5S Takip uygulamasını indirin
            🔑 Davet Kodu: ${getCurrentInviteCode()}
            
            Grup Ayarları → "Gruba Katıl" → Davet kodunu girin
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, inviteMessage)
            putExtra(Intent.EXTRA_SUBJECT, "${selectedGroup!!.name} Grubu Daveti")
        }

        startActivity(Intent.createChooser(shareIntent, "Davet Kodunu Paylaş"))
    }

    /**
     * Güncel davet kodunu al (basitleştirilmiş)
     */
    private fun getCurrentInviteCode(): String {
        return selectedGroup?.inviteCode ?: "123456"
    }

    /**
     * Gün adını döndür
     */
    private fun getDayName(weekDay: Int): String {
        return when (weekDay) {
            1 -> "Pazartesi"
            2 -> "Salı"
            3 -> "Çarşamba"
            4 -> "Perşembe"
            5 -> "Cuma"
            6 -> "Cumartesi"
            7 -> "Pazar"
            else -> "Bilinmeyen"
        }
    }

    /**
     * Debug: Grup üyelerini logla
     */
    private fun debugGroupMembers() {
        println("DEBUG: ==================== GRUP ÜYELERİ DEBUG ====================")
        println("DEBUG: Toplam üye sayısı: ${groupMembers.size}")

        if (groupMembers.isEmpty()) {
            println("DEBUG: ❌ GRUP ÜYELERİ LİSTESİ BOŞ!")
        } else {
            groupMembers.forEachIndexed { index, member ->
                println("DEBUG: $index. ${member.userName}")
                println("DEBUG:    - Email: ${member.userEmail}")
                println("DEBUG:    - Role: ${member.role}")
                println("DEBUG:    - User ID: ${member.userId}")
                println("DEBUG: ─────────────────────────────────────────")
            }
        }

        println("DEBUG: ==================== HAFTALIK DENETMENLER ====================")
        if (weeklyAuditors.isEmpty()) {
            println("DEBUG: Henüz hiç denetmen atanmamış")
        } else {
            weeklyAuditors.forEach { auditor ->
                println("DEBUG: ${getDayName(auditor.weekDay)}: ${auditor.auditorName}")
            }
        }
        println("DEBUG: ================================================================")
    }

    /**
     * Grup verilerini yükle - 7 GÜN ZORLA GÖSTERİM VERSİYONU
     */
    private fun loadGroupData() {
        binding.progressLoading.visibility = View.VISIBLE
        println("DEBUG: ✅ Grup verileri yükleniyor - Grup ID: $groupId (7 gün zorla gösterilecek)")

        lifecycleScope.launch {
            try {
                // 1. Grup üyelerini yükle
                val membersResult = firebaseManager.getGroupMembers(groupId)
                if (membersResult.isSuccess) {
                    val members = membersResult.getOrNull() ?: emptyList()
                    println("DEBUG: Firebase'den ${members.size} üye alındı")

                    groupMembers.clear()
                    groupMembers.addAll(members)

                    runOnUiThread {
                        membersAdapter.notifyDataSetChanged()
                        binding.tvMembersCount.text = "${groupMembers.size} üye"
                        checkUserPermissions()
                        debugGroupMembers()
                    }
                } else {
                    println("DEBUG: ❌ Grup üyeleri yüklenemedi: ${membersResult.exceptionOrNull()?.message}")
                    runOnUiThread {
                        Toast.makeText(this@GroupSettingsActivity,
                            "Grup üyeleri yüklenemedi: ${membersResult.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }

                // 2. Haftalık denetmenleri yükle
                val auditorsResult = firebaseManager.getWeeklyAuditors(groupId)
                if (auditorsResult.isSuccess) {
                    val auditors = auditorsResult.getOrNull() ?: emptyList()
                    weeklyAuditors.clear()
                    weeklyAuditors.addAll(auditors)

                    runOnUiThread {
                        // ✅ ZORLA 7 GÜN GÖSTERİM - Adapter her zaman 7 gün döndürür
                        weeklyScheduleAdapter.notifyDataSetChanged()
                        println("DEBUG: ✅ Haftalık program güncellendi: ${auditors.size} atama")
                        println("DEBUG: ✅ WeeklyScheduleAdapter HER ZAMAN 7 gün gösterecek (veriye bakılmaksızın)")
                    }
                } else {
                    println("DEBUG: ❌ Haftalık denetmenler yüklenemedi: ${auditorsResult.exceptionOrNull()?.message}")
                    // ✅ Hata olsa bile 7 gün sabit gösterilir
                    runOnUiThread {
                        weeklyScheduleAdapter.notifyDataSetChanged()
                        println("DEBUG: ✅ Hata olsa bile WeeklyScheduleAdapter 7 gün gösterecek")
                    }
                }

                runOnUiThread {
                    binding.progressLoading.visibility = View.GONE
                }

            } catch (e: Exception) {
                println("DEBUG: ❌ loadGroupData exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this@GroupSettingsActivity,
                        "Veri yüklenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()

                    // ✅ Exception olsa bile 7 gün sabit gösterilir
                    weeklyScheduleAdapter.notifyDataSetChanged()
                    println("DEBUG: ✅ Exception olsa bile WeeklyScheduleAdapter 7 gün gösterecek")
                }
            }
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_OK)
        super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_OK)
        onBackPressed()
        return true
    }
}

/**
 * Grup üyeleri için adapter - BASİTLEŞTİRİLMİŞ
 */
class GroupMembersAdapter(
    private val members: List<GroupMember>,
    private val onMemberClick: (GroupMember) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder>() {

    class MemberViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardView: androidx.cardview.widget.CardView = view as androidx.cardview.widget.CardView
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MemberViewHolder {
        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context)

        val layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
            androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(8, 4, 8, 4)
        cardView.layoutParams = layoutParams
        cardView.radius = 8f
        cardView.cardElevation = 4f

        return MemberViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        val context = holder.itemView.context

        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(16, 12, 16, 12)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        val avatarFrame = android.widget.FrameLayout(context)
        avatarFrame.layoutParams = android.widget.LinearLayout.LayoutParams(48, 48)

        val profileIcon = android.widget.TextView(context)
        profileIcon.text = member.userName.take(1).uppercase()
        profileIcon.textSize = 18f
        profileIcon.setTypeface(null, android.graphics.Typeface.BOLD)
        profileIcon.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.white))
        profileIcon.background = androidx.core.content.ContextCompat.getDrawable(context, R.color.primary)
        profileIcon.gravity = android.view.Gravity.CENTER
        profileIcon.layoutParams = android.widget.FrameLayout.LayoutParams(48, 48)

        avatarFrame.addView(profileIcon)

        val userInfo = android.widget.LinearLayout(context)
        userInfo.orientation = android.widget.LinearLayout.VERTICAL
        userInfo.setPadding(16, 0, 0, 0)

        val nameText = android.widget.TextView(context)
        nameText.text = member.userName
        nameText.textSize = 16f
        nameText.setTypeface(null, android.graphics.Typeface.BOLD)
        nameText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.black))

        val emailText = android.widget.TextView(context)
        emailText.text = member.userEmail
        emailText.textSize = 14f
        emailText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.gray_dark))

        val roleText = android.widget.TextView(context)
        val roleDisplayText = when (member.role) {
            GroupRoles.OWNER -> "👑 Grup Sahibi"
            GroupRoles.ADMIN -> "⭐ Yönetici"
            else -> "👤 Üye"
        }
        roleText.text = roleDisplayText
        roleText.textSize = 12f
        roleText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))

        userInfo.addView(nameText)
        userInfo.addView(emailText)
        userInfo.addView(roleText)

        innerLayout.addView(avatarFrame)
        innerLayout.addView(userInfo)

        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)

        holder.cardView.setOnClickListener {
            onMemberClick(member)
        }
    }

    override fun getItemCount(): Int = members.size
}

/**
 * ✅ Haftalık program için adapter - 7 GÜN ZORLA GÖSTERİM (SORUN ÇÖZÜLDÜ!)
 * HER ZAMAN 7 GÜN GÖZÜKMELİ - VERİYE BAKILMAKSIZIN
 */
class WeeklyScheduleAdapter(
    private val weeklyAuditors: List<WeeklyAuditor>,
    private val members: List<GroupMember>,
    private val onDayClick: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<WeeklyScheduleAdapter.DayViewHolder>() {

    // ✅ 7 günün hepsi HER ZAMAN gözükmeli - Sabit liste
    private val daysOfWeek = listOf(
        1 to "Pazartesi", 2 to "Salı", 3 to "Çarşamba", 4 to "Perşembe",
        5 to "Cuma", 6 to "Cumartesi", 7 to "Pazar"
    )

    init {
        println("DEBUG: ✅ WeeklyScheduleAdapter oluşturuldu - 7 gün HER ZAMAN gösterilecek")
        println("DEBUG: ✅ Firebase'den gelen denetmen sayısı: ${weeklyAuditors.size}")
        println("DEBUG: ✅ Ama adapter HER ZAMAN 7 gün döndürecek")
        daysOfWeek.forEachIndexed { index, (dayNum, dayName) ->
            println("DEBUG: $index. $dayNum -> $dayName")
        }
    }

    class DayViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardView: androidx.cardview.widget.CardView = view as androidx.cardview.widget.CardView
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): DayViewHolder {
        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context)

        val layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
            androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(8, 4, 8, 4)
        cardView.layoutParams = layoutParams
        cardView.radius = 8f
        cardView.cardElevation = 4f

        return DayViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val (dayNumber, dayName) = daysOfWeek[position]
        val context = holder.itemView.context

        // ✅ Bu güne atanmış denetmeni bul (yoksa null)
        val assignedAuditor = weeklyAuditors.find { it.weekDay == dayNumber }
        val auditorName = if (assignedAuditor != null) {
            members.find { it.userId == assignedAuditor.auditorId }?.userName ?: "Bilinmeyen"
        } else {
            "Atanmamış" // ✅ Denetmen yoksa açık göster
        }

        // Bugün mü kontrol et
        val calendar = Calendar.getInstance()
        val todayNumber = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 0
        }

        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(16, 12, 16, 12)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        val isToday = dayNumber == todayNumber
        if (isToday) {
            innerLayout.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
        }

        // Gün adı
        val dayText = android.widget.TextView(context)
        dayText.text = if (isToday) "🔴 $dayName (BUGÜN)" else dayName
        dayText.textSize = 16f
        dayText.setTypeface(null, android.graphics.Typeface.BOLD)
        val dayColor = if (isToday) R.color.white else R.color.primary
        dayText.setTextColor(androidx.core.content.ContextCompat.getColor(context, dayColor))
        dayText.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        // Denetmen adı - ✅ Atanmamışsa da göster
        val auditorText = android.widget.TextView(context)
        auditorText.text = auditorName
        auditorText.textSize = 14f
        val auditorColor = if (isToday) {
            R.color.white
        } else if (assignedAuditor != null) {
            R.color.black // Atanmış
        } else {
            R.color.gray_dark // Atanmamış
        }
        auditorText.setTextColor(androidx.core.content.ContextCompat.getColor(context, auditorColor))

        // Tıklama ok işareti
        val arrowText = android.widget.TextView(context)
        arrowText.text = "›"
        arrowText.textSize = 20f
        val arrowColor = if (isToday) R.color.white else R.color.gray_medium
        arrowText.setTextColor(androidx.core.content.ContextCompat.getColor(context, arrowColor))

        innerLayout.addView(dayText)
        innerLayout.addView(auditorText)
        innerLayout.addView(arrowText)

        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)

        // ✅ Her güne tıklanabilir
        holder.cardView.setOnClickListener {
            println("DEBUG: ${dayName} günü tıklandı - Mevcut denetmen: $auditorName")
            onDayClick(dayNumber)
        }
    }

    // ✅ ✅ ✅ HER ZAMAN 7 gün döndür - ZORLA SABİT
    override fun getItemCount(): Int {
        println("DEBUG: ✅ ✅ ✅ getItemCount() = 7 (ZORLA SABİT)")
        println("DEBUG: Firebase'den gelen denetmen sayısı: ${weeklyAuditors.size}")
        println("DEBUG: Ama döndürülen item sayısı: 7 (sabit)")
        return 7 // ✅ ZORLA SABİT 7 GÜN - HİÇBİR KOŞULDA DEĞİŞMEZ!
    }
}