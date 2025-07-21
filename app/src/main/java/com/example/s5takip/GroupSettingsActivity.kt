package com.fabrika.s5takip

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
import java.util.*

/**
 * Grup ayarları ekranı - BASİT 3 ROL SİSTEMİ
 * OWNER: Her şeyi yapabilir
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

    // Basit yetki kontrolleri
    private var currentUserRole: String = GroupRoles.MEMBER
    private var canManageAuditors: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent'ten grup bilgilerini al
        groupId = intent.getStringExtra("group_id") ?: ""
        groupName = intent.getStringExtra("group_name") ?: "Grup"

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
    }

    /**
     * UI'ı ayarla
     */
    private fun setupUI() {
        binding.tvGroupNameTitle.text = groupName
        binding.btnShareInviteCode.visibility = View.GONE
    }

    /**
     * RecyclerView'ları ayarla - ROL DEĞİŞTİRME DESTEKLİ
     */
    private fun setupRecyclerViews() {
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

        // Haftalık program adapter
        weeklyScheduleAdapter = WeeklyScheduleAdapter(weeklyAuditors, groupMembers) { weekDay ->
            if (canManageAuditors) {
                showAuditorSelectionDialog(weekDay)
            } else {
                showReadOnlyScheduleInfo(weekDay)
            }
        }
        binding.rvWeeklySchedule.layoutManager = LinearLayoutManager(this)
        binding.rvWeeklySchedule.adapter = weeklyScheduleAdapter

        println("DEBUG: RecyclerView'lar ayarlandı - Rol değiştirme destekli")
    }

    /**
     * Üye rolünü değiştirme dialog'u - DÜZELTİLMİŞ VERSİYON
     */
    private fun showMemberRoleDialog(member: GroupMember) {
        println("DEBUG: ==================== ROL DEĞİŞTİRME DIALOG'U ====================")
        println("DEBUG: Seçilen üye: ${member.userName}")
        println("DEBUG: Mevcut rol: ${member.role}")
        println("DEBUG: Kullanıcı yetkisi - OWNER mu: ${currentUserRole == GroupRoles.OWNER}")

        // Sadece OWNER rol değiştirebilir
        if (currentUserRole != GroupRoles.OWNER) {
            showMemberProfile(member) // Sadece profil göster
            return
        }

        // Grup sahibinin rolü değiştirilemez
        if (member.role == GroupRoles.OWNER) {
            AlertDialog.Builder(this)
                .setTitle("❌ İşlem Yapılamaz")
                .setMessage("Grup sahibinin rolü değiştirilemez.\n\n${member.userName} grup sahibidir ve bu rol değiştirilemez.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }

        // Rol seçenekleri ve açıklamaları
        val roleOptions = arrayOf(
            "👤 Üye - Sadece görüntüleme yetkisi",
            "⭐ Yönetici - Denetmen ataması yapabilir"
        )

        val roleValues = arrayOf(
            GroupRoles.MEMBER,
            GroupRoles.ADMIN
        )

        // Mevcut rolün index'ini bul
        val currentRoleIndex = when (member.role) {
            GroupRoles.ADMIN -> 1
            else -> 0 // MEMBER veya bilinmeyen -> MEMBER
        }

        println("DEBUG: Mevcut rol index: $currentRoleIndex")
        println("DEBUG: Rol seçenekleri hazır, dialog açılıyor...")

        // Rol değiştirme dialog'u
        AlertDialog.Builder(this)
            .setTitle("${member.userName} - Rol Değiştir")
            .setSingleChoiceItems(roleOptions, currentRoleIndex) { dialog, which ->
                val newRole = roleValues[which]
                val newRoleName = when(newRole) {
                    GroupRoles.ADMIN -> "⭐ Yönetici"
                    else -> "👤 Üye"
                }

                println("DEBUG: ✅ Yeni rol seçildi: $newRole")

                dialog.dismiss()

                // Eğer aynı rol seçildiyse hiçbir şey yapma
                if (newRole == member.role) {
                    Toast.makeText(this, "${member.userName} zaten $newRoleName rolünde", Toast.LENGTH_SHORT).show()
                    return@setSingleChoiceItems
                }

                // Onay dialog'u göster
                showConfirmRoleChange(member, newRole, newRoleName)
            }
            .setNegativeButton("❌ İptal") { dialog, _ ->
                println("DEBUG: Rol değiştirme iptal edildi")
                dialog.dismiss()
            }
            .show()

        println("DEBUG: Rol değiştirme dialog'u gösterildi")
    }

    /**
     * Rol değişikliği onay dialog'u - AYRI FONKSİYON
     */
    private fun showConfirmRoleChange(member: GroupMember, newRole: String, newRoleName: String) {
        val oldRoleName = when(member.role) {
            GroupRoles.OWNER -> "👑 Grup Sahibi"
            GroupRoles.ADMIN -> "⭐ Yönetici"
            else -> "👤 Üye"
        }

        val confirmMessage = """
        ${member.userName} kullanıcısının rolü değiştirilecek:
        
        🔄 Eski Rol: $oldRoleName
        ➡️ Yeni Rol: $newRoleName
        
        Bu değişikliği onaylıyor musunuz?
    """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Rol Değişikliğini Onayla")
            .setMessage(confirmMessage)
            .setPositiveButton("✅ Evet, Değiştir") { _, _ ->
                performRoleChange(member, newRole, newRoleName)
            }
            .setNegativeButton("❌ İptal", null)
            .show()
    }

    /**
     * Rol değişikliğini gerçekleştir - İYİLEŞTİRİLMİŞ VERSİYON
     */
    private fun performRoleChange(member: GroupMember, newRole: String, newRoleName: String) {
        println("DEBUG: ==================== ROL DEĞİŞİKLİĞİ BAŞLADI ====================")
        println("DEBUG: Kullanıcı: ${member.userName}")
        println("DEBUG: Eski rol: ${member.role}")
        println("DEBUG: Yeni rol: $newRole")

        // Loading dialog göster
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("⏳ Rol Güncelleniyor")
            .setMessage("${member.userName} kullanıcısının rolü güncelleniyor...\n\nLütfen bekleyin.")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                // Yeni rol ile güncellenmiş üye objesi oluştur
                val updatedMember = member.copy(role = newRole)

                println("DEBUG: Firebase'e rol güncelleme isteği gönderiliyor...")
                val result = firebaseManager.updateGroupMember(updatedMember)

                runOnUiThread {
                    loadingDialog.dismiss()

                    if (result.isSuccess) {
                        println("DEBUG: ✅ Rol başarıyla güncellendi!")

                        // Local listeyi güncelle
                        val memberIndex = groupMembers.indexOf(member)
                        if (memberIndex != -1) {
                            groupMembers[memberIndex] = updatedMember
                            membersAdapter.notifyItemChanged(memberIndex)
                            println("DEBUG: Local liste güncellendi, adapter bilgilendirildi")
                        }

                        // Başarı mesajı göster
                        val successMessage = """
                        ✅ Rol değişikliği başarılı!
                        
                        👤 Kullanıcı: ${member.userName}
                        🎭 Yeni Rol: $newRoleName
                        
                        Yeni yetkiler hemen aktif olmuştur.
                    """.trimIndent()

                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("İşlem Tamamlandı")
                            .setMessage(successMessage)
                            .setPositiveButton("Tamam") { _, _ ->
                                // Grup verilerini yenile (güvenlik için)
                                loadGroupData()
                            }
                            .show()

                        // Eğer rol değişen kişi yönetici olduysa bilgilendir
                        if (newRole == GroupRoles.ADMIN) {
                            Toast.makeText(this@GroupSettingsActivity,
                                "🎉 ${member.userName} artık yönetici! Denetmen ataması yapabilir.",
                                Toast.LENGTH_LONG).show()
                        }

                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Bilinmeyen hata"
                        println("DEBUG: ❌ Rol güncelleme başarısız: $error")

                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("❌ Rol Güncellenemedi")
                            .setMessage("Rol güncellenirken hata oluştu:\n\n$error\n\nLütfen tekrar deneyin.")
                            .setPositiveButton("Tamam", null)
                            .setNeutralButton("Tekrar Dene") { _, _ ->
                                // Tekrar deneme seçeneği
                                showMemberRoleDialog(member)
                            }
                            .show()
                    }
                }

            } catch (e: Exception) {
                println("DEBUG: ❌ Rol güncelleme exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    loadingDialog.dismiss()

                    AlertDialog.Builder(this@GroupSettingsActivity)
                        .setTitle("💥 Beklenmeyen Hata")
                        .setMessage("Rol güncellenirken beklenmeyen bir hata oluştu:\n\n${e.message}")
                        .setPositiveButton("Tamam", null)
                        .setNeutralButton("Tekrar Dene") { _, _ ->
                            showMemberRoleDialog(member)
                        }
                        .show()
                }
            }
        }
    }

    /**
     * Üye profilini göster - BASİTLEŞTİRİLMİŞ VERSİYON
     */
    private fun showMemberProfile(member: GroupMember) {
        val roleText = when(member.role) {
            GroupRoles.OWNER -> "👑 Grup Sahibi"
            GroupRoles.ADMIN -> "⭐ Yönetici"
            else -> "👤 Üye"
        }

        val message = """
        👤 Ad: ${member.userName}
        📧 Email: ${member.userEmail}
        🎭 Rol: $roleText
        📅 Katılma: ${formatDate(member.joinedAt)}
        
        ${if (currentUserRole != GroupRoles.OWNER) "ℹ️ Rol değiştirmek için grup sahibi yetkisi gereklidir." else ""}
    """.trimIndent()

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("Üye Profili")
            .setMessage(message)
            .setPositiveButton("Tamam", null)

        // Sadece OWNER rol değiştirebilir ve üye OWNER değilse
        if (currentUserRole == GroupRoles.OWNER && member.role != GroupRoles.OWNER) {
            dialogBuilder.setNeutralButton("🎭 Rol Değiştir") { _, _ ->
                showMemberRoleDialog(member)
            }
        }

        dialogBuilder.show()
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Yenile butonu
        binding.btnRefreshData.setOnClickListener {
            loadGroupData()
        }

        // Davet kodu paylaş
        binding.btnShareInviteCode.setOnClickListener {
            if (currentUserRole == GroupRoles.OWNER) {
                shareInviteCode()
            }
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
                binding.tvScheduleDescription.text = "👑 Grup sahibi olarak tüm ayarları yönetebilirsiniz. Günlere tıklayarak denetmen atayın."
            }
            GroupRoles.ADMIN -> {
                binding.btnShareInviteCode.visibility = View.GONE
                binding.tvScheduleDescription.text = "⭐ Yönetici olarak denetmen atamaları yapabilirsiniz. Günlere tıklayarak denetmen atayın."
            }
            else -> {
                binding.btnShareInviteCode.visibility = View.GONE
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
            "📅 Haftalık Denetmen Programı (Yönet)"
        } else {
            "📅 Haftalık Denetmen Programı (Görüntüle)"
        }
        binding.tvWeeklyScheduleTitle.text = titleText
    }
    /**
     * Denetmen seçim dialog'u - DÜZELTİLMİŞ VERSİYON
     * setMessage ve setSingleChoiceItems birlikte kullanılamaz!
     */
    private fun showAuditorSelectionDialog(weekDay: Int) {
        println("DEBUG: ==================== DENETMEN SEÇİM DIALOG'U ====================")
        println("DEBUG: canManageAuditors: $canManageAuditors")
        println("DEBUG: currentUserRole: $currentUserRole")

        if (!canManageAuditors) {
            showReadOnlyScheduleInfo(weekDay)
            return
        }

        val dayName = getDayName(weekDay)

        // TÜM ÜYELERİ GÖSTERİR - ROL FARKETMEZ
        val allMembers = groupMembers.filter {
            it.userId.isNotEmpty() && it.userName.isNotEmpty()
        }

        println("DEBUG: Toplam grup üyesi: ${groupMembers.size}")
        println("DEBUG: Denetmen seçimi için filtrelenmiş üye sayısı: ${allMembers.size}")

        allMembers.forEachIndexed { index, member ->
            println("DEBUG: $index. ${member.userName} (${member.role}) - ID: ${member.userId}")
        }

        if (allMembers.isEmpty()) {
            Toast.makeText(this, "❌ Grup üyesi bulunamadı!", Toast.LENGTH_LONG).show()
            println("DEBUG: ❌ Hiç grup üyesi bulunamadı!")
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

        println("DEBUG: Mevcut denetmen: ${currentAuditor?.auditorName ?: "Yok"}")
        println("DEBUG: Mevcut index: $currentIndex")
        println("DEBUG: Üye listesi hazır, dialog açılıyor...")

        // ✅ DÜZELTİLMİŞ DIALOG - setMessage kullanmıyoruz!
        AlertDialog.Builder(this)
            .setTitle("$dayName Denetmeni Seç\n\nGrup üyeleri arasından birini seçin:") // Başlıkta açıklama
            // .setMessage() - KULLANMIYORUZ! setSingleChoiceItems ile çelişir
            .setSingleChoiceItems(memberDisplayNames, currentIndex) { dialog, which ->
                val selectedMember = allMembers[which]

                println("DEBUG: ✅ Seçilen üye: ${selectedMember.userName} (${selectedMember.role})")

                // Onay dialog'u
                showConfirmAuditorAssignment(weekDay, selectedMember)

                dialog.dismiss()
            }
            .setNegativeButton("❌ İptal") { dialog, _ ->
                println("DEBUG: Dialog iptal edildi")
                dialog.dismiss()
            }
            .setNeutralButton("🗑️ Denetmen Kaldır") { dialog, _ ->
                dialog.dismiss()
                if (currentAuditor != null) {
                    showConfirmAuditorRemoval(weekDay, currentAuditor)
                } else {
                    Toast.makeText(this, "Bu gün için zaten denetmen yok", Toast.LENGTH_SHORT).show()
                }
            }
            .show()

        println("DEBUG: Dialog show() çağrıldı")
    }

    /**
     * Denetmen ataması onay dialog'u - AYRI FONKSİYON
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
            .setMessage(confirmMessage) // Burada setMessage kullanabiliriz
            .setPositiveButton("✅ Evet, Ata") { _, _ ->
                assignAuditorToDay(weekDay, selectedMember)
            }
            .setNegativeButton("❌ İptal", null)
            .show()
    }

    /**
     * Denetmen kaldırma onay dialog'u - AYRI FONKSİYON
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

                        weeklyScheduleAdapter.notifyDataSetChanged()

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

                        println("DEBUG: ❌ Denetmen atama hatası: ${result.exceptionOrNull()?.message}")
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
            weeklyScheduleAdapter.notifyDataSetChanged()

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
     * Davet kodunu paylaş
     */
    private fun shareInviteCode() {
        Toast.makeText(this, "Davet kodu paylaşma özelliği yakında eklenecek", Toast.LENGTH_SHORT).show()
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
     * Grup verilerini yükle
     */
    private fun loadGroupData() {
        binding.progressLoading.visibility = View.VISIBLE
        println("DEBUG: Grup verileri yükleniyor - Grup ID: $groupId")

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

                        // Yetki kontrolünü yap
                        checkUserPermissions()

                        // Debug log'larını yazdır
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
                        weeklyScheduleAdapter.notifyDataSetChanged()
                        println("DEBUG: ✅ Haftalık denetmenler yüklendi: ${auditors.size} atama")
                    }
                } else {
                    println("DEBUG: ❌ Haftalık denetmenler yüklenemedi: ${auditorsResult.exceptionOrNull()?.message}")
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
 * Haftalık program için adapter - 7 GÜN
 */
class WeeklyScheduleAdapter(
    private val weeklyAuditors: List<WeeklyAuditor>,
    private val members: List<GroupMember>,
    private val onDayClick: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<WeeklyScheduleAdapter.DayViewHolder>() {

    private val daysOfWeek = listOf(
        1 to "Pazartesi", 2 to "Salı", 3 to "Çarşamba", 4 to "Perşembe",
        5 to "Cuma", 6 to "Cumartesi", 7 to "Pazar"
    )

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

        val assignedAuditor = weeklyAuditors.find { it.weekDay == dayNumber }
        val auditorName = if (assignedAuditor != null) {
            members.find { it.userId == assignedAuditor.auditorId }?.userName ?: "Bilinmeyen"
        } else {
            "Atanmamış"
        }

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

        val dayText = android.widget.TextView(context)
        dayText.text = if (isToday) "🔴 $dayName (BUGÜN)" else dayName
        dayText.textSize = 16f
        dayText.setTypeface(null, android.graphics.Typeface.BOLD)
        val dayColor = if (isToday) R.color.white else R.color.primary
        dayText.setTextColor(androidx.core.content.ContextCompat.getColor(context, dayColor))
        dayText.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        val auditorText = android.widget.TextView(context)
        auditorText.text = auditorName
        auditorText.textSize = 14f
        val auditorColor = if (isToday) {
            R.color.white
        } else if (assignedAuditor != null) {
            R.color.black
        } else {
            R.color.gray_dark
        }
        auditorText.setTextColor(androidx.core.content.ContextCompat.getColor(context, auditorColor))

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

        holder.cardView.setOnClickListener {
            onDayClick(dayNumber)
        }
    }

    override fun getItemCount(): Int = daysOfWeek.size
}