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
 * Grup ayarları ekranı - HATASIZ VERSİYON
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
    private var isGroupOwner = false

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
     * RecyclerView'ları ayarla - DÜZELTİLMİŞ VERSİYON
     */
    private fun setupRecyclerViews() {
        // Grup üyeleri adapter
        membersAdapter = GroupMembersAdapter(groupMembers) { member ->
            if (isGroupOwner) {
                showMemberRoleDialog(member)
            } else {
                showMemberProfile(member)
            }
        }
        binding.rvGroupMembers.layoutManager = LinearLayoutManager(this)
        binding.rvGroupMembers.adapter = membersAdapter

        // Haftalık program adapter - 7 GÜN İLE
        weeklyScheduleAdapter = WeeklyScheduleAdapter(weeklyAuditors, groupMembers) { weekDay ->
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val currentUserMember = groupMembers.find { it.userId == currentUserId }
            val canManage = currentUserMember?.role == GroupRoles.OWNER || currentUserMember?.role == GroupRoles.ADMIN

            if (canManage) {
                showAuditorSelectionDialog(weekDay)
            } else {
                showReadOnlyScheduleInfo(weekDay)
            }
        }
        binding.rvWeeklySchedule.layoutManager = LinearLayoutManager(this)
        binding.rvWeeklySchedule.adapter = weeklyScheduleAdapter

        println("DEBUG: RecyclerView'lar ayarlandı - WeeklyScheduleAdapter item count: ${weeklyScheduleAdapter.itemCount}")
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
            if (isGroupOwner) {
                shareInviteCode()
            }
        }
    }

    /**
     * Üye rolünü değiştirme dialog'u
     */
    private fun showMemberRoleDialog(member: GroupMember) {
        if (!isGroupOwner) {
            showMemberProfile(member)
            return
        }

        if (member.role == GroupRoles.OWNER) {
            Toast.makeText(this, "Grup sahibinin rolü değiştirilemez", Toast.LENGTH_SHORT).show()
            return
        }

        val roles = arrayOf(
            "👤 Üye - Sadece görüntüleme yetkisi",
            "🔍 Denetmen - Problem ekleyebilir",
            "⭐ Yönetici - Denetmen ataması yapabilir"
        )

        val roleValues = arrayOf(
            GroupRoles.MEMBER,
            GroupRoles.AUDITOR,
            GroupRoles.ADMIN
        )

        val currentIndex = roleValues.indexOf(member.role)

        AlertDialog.Builder(this)
            .setTitle("${member.userName} - Rol Değiştir")
            .setMessage("Mevcut rol: ${getRoleDisplayName(member.role)}\n\nYeni rol seçin:")
            .setSingleChoiceItems(roles, currentIndex) { dialog, which ->
                val newRole = roleValues[which]

                AlertDialog.Builder(this@GroupSettingsActivity)
                    .setTitle("Rol Değişikliğini Onayla")
                    .setMessage("${member.userName} kullanıcısının rolü '${getRoleDisplayName(newRole)}' olarak değiştirilecek.\n\nOnaylıyor musunuz?")
                    .setPositiveButton("Evet, Değiştir") { _, _ ->
                        updateMemberRole(member, newRole)
                    }
                    .setNegativeButton("İptal", null)
                    .show()

                dialog.dismiss()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * Rol adını görüntüleme formatında döndür
     */
    private fun getRoleDisplayName(role: String): String {
        return when(role) {
            GroupRoles.OWNER -> "👑 Grup Sahibi"
            GroupRoles.ADMIN -> "⭐ Yönetici"
            GroupRoles.AUDITOR -> "🔍 Denetmen"
            GroupRoles.MEMBER -> "👤 Üye"
            else -> "❓ Bilinmeyen"
        }
    }

    /**
     * Üye rolünü güncelle
     */
    private fun updateMemberRole(member: GroupMember, newRole: String) {
        if (member.role == GroupRoles.OWNER) {
            Toast.makeText(this, "Grup sahibinin rolü değiştirilemez", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Rol Güncelleniyor")
            .setMessage("${member.userName} kullanıcısının rolü güncelleniyor...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                val updatedMember = member.copy(role = newRole)
                val result = firebaseManager.updateGroupMember(updatedMember)

                runOnUiThread {
                    loadingDialog.dismiss()

                    if (result.isSuccess) {
                        val index = groupMembers.indexOf(member)
                        if (index != -1) {
                            groupMembers[index] = updatedMember
                            membersAdapter.notifyItemChanged(index)
                        }

                        val roleText = getRoleDisplayName(newRole)

                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("✅ Rol Güncellendi")
                            .setMessage("${member.userName} artık $roleText rolünde!\n\nYeni yetkiler hemen aktif olacak.")
                            .setPositiveButton("Tamam", null)
                            .show()

                        println("DEBUG: ✅ Rol güncellendi: ${member.userName} -> $newRole")
                    } else {
                        AlertDialog.Builder(this@GroupSettingsActivity)
                            .setTitle("❌ Rol Güncellenemedi")
                            .setMessage("Rol güncellenirken hata oluştu:\n\n${result.exceptionOrNull()?.message}")
                            .setPositiveButton("Tamam", null)
                            .show()

                        println("DEBUG: ❌ Rol güncelleme hatası: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingDialog.dismiss()

                    AlertDialog.Builder(this@GroupSettingsActivity)
                        .setTitle("💥 Beklenmeyen Hata")
                        .setMessage("Rol güncellenirken beklenmeyen hata:\n\n${e.message}")
                        .setPositiveButton("Tamam", null)
                        .show()

                    println("DEBUG: 💥 Exception: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Grup sahipliği kontrolü yap
     */
    private fun checkGroupOwnership() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        lifecycleScope.launch {
            try {
                val membersResult = firebaseManager.getGroupMembers(groupId)
                if (membersResult.isSuccess) {
                    val members = membersResult.getOrNull() ?: emptyList()
                    val currentUserMember = members.find { it.userId == currentUserId }

                    isGroupOwner = currentUserMember?.role == GroupRoles.OWNER
                    val isAdmin = currentUserMember?.role == GroupRoles.ADMIN

                    runOnUiThread {
                        updateUIBasedOnPermissions(isAdmin)
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Grup sahipliği kontrolü hatası: ${e.message}")
            }
        }
    }

    /**
     * Yetkilere göre UI'ı güncelle
     */
    private fun updateUIBasedOnPermissions(isAdmin: Boolean = false) {
        val canManage = isGroupOwner || isAdmin

        if (isGroupOwner) {
            binding.btnShareInviteCode.visibility = View.VISIBLE
            binding.tvScheduleDescription.text = "Grup sahibi olarak tüm ayarları yönetebilirsiniz. Günlere tıklayarak denetmen seçin."
        } else if (isAdmin) {
            binding.btnShareInviteCode.visibility = View.VISIBLE
            binding.tvScheduleDescription.text = "Yönetici olarak denetmen atamaları yapabilirsiniz. Günlere tıklayarak denetmen seçin."
        } else {
            binding.btnShareInviteCode.visibility = View.GONE
            binding.tvScheduleDescription.text = "Haftalık denetmen programını görüntülüyorsunuz. Değişiklik yapmak için grup sahibi veya yönetici ile iletişime geçin."
        }

        weeklyScheduleAdapter = WeeklyScheduleAdapter(weeklyAuditors, groupMembers) { weekDay ->
            if (canManage) {
                showAuditorSelectionDialog(weekDay)
            } else {
                showReadOnlyScheduleInfo(weekDay)
            }
        }
        binding.rvWeeklySchedule.adapter = weeklyScheduleAdapter
    }

    /**
     * Haftalık program başlığını güncelle
     */
    private fun updateWeeklyScheduleTitle() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val currentUserMember = groupMembers.find { it.userId == currentUserId }
        val canManage = currentUserMember?.role == GroupRoles.OWNER || currentUserMember?.role == GroupRoles.ADMIN

        val titleText = if (canManage) {
            "📅 Haftalık Denetmen Programı (Yönet)"
        } else {
            "📅 Haftalık Denetmen Programı (Görüntüle)"
        }
        binding.tvWeeklyScheduleTitle.text = titleText
    }

    /**
     * Denetmen seçim dialog'u
     */
    private fun showAuditorSelectionDialog(weekDay: Int) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val currentUserMember = groupMembers.find { it.userId == currentUserId }
        val canManage = currentUserMember?.role == GroupRoles.OWNER || currentUserMember?.role == GroupRoles.ADMIN

        if (!canManage) {
            showReadOnlyScheduleInfo(weekDay)
            return
        }

        val dayName = getDayName(weekDay)

        val eligibleMembers = groupMembers.filter {
            it.role == GroupRoles.AUDITOR || it.role == GroupRoles.ADMIN || it.role == GroupRoles.OWNER
        }

        if (eligibleMembers.isEmpty()) {
            Toast.makeText(this, "Denetmen yetkisi olan üye bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val memberNames = eligibleMembers.map { member ->
            val roleText = when(member.role) {
                GroupRoles.OWNER -> " (Grup Sahibi)"
                GroupRoles.ADMIN -> " (Yönetici)"
                GroupRoles.AUDITOR -> " (Denetmen)"
                else -> ""
            }
            "${member.userName}$roleText"
        }.toTypedArray()

        val currentAuditor = weeklyAuditors.find { it.weekDay == weekDay }
        val currentIndex = if (currentAuditor != null) {
            eligibleMembers.indexOfFirst { it.userId == currentAuditor.auditorId }
        } else {
            -1
        }

        AlertDialog.Builder(this)
            .setTitle("$dayName Denetmeni Seç")
            .setSingleChoiceItems(memberNames, currentIndex) { dialog, which ->
                val selectedMember = eligibleMembers[which]
                assignAuditorToDay(weekDay, selectedMember)
                dialog.dismiss()
            }
            .setNegativeButton("İptal", null)
            .setNeutralButton("Kaldır") { _, _ ->
                removeAuditorFromDay(weekDay)
            }
            .show()
    }

    /**
     * Üye profilini göster
     */
    private fun showMemberProfile(member: GroupMember) {
        val message = """
            Ad: ${member.userName}
            Email: ${member.userEmail}
            Rol: ${member.role}
            Katılma Tarihi: ${formatDate(member.joinedAt)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Üye Profili")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
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
            "$dayName günü denetmeni: ${member?.userName ?: "Bilinmeyen"}"
        } else {
            "$dayName günü için henüz denetmen atanmamış."
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
                        if (existingAuditor != null) {
                            val index = weeklyAuditors.indexOf(existingAuditor)
                            weeklyAuditors[index] = weeklyAuditor
                        } else {
                            weeklyAuditors.add(weeklyAuditor)
                        }

                        weeklyScheduleAdapter.notifyDataSetChanged()

                        Toast.makeText(this@GroupSettingsActivity,
                            "${member.userName} ${getDayName(weekDay)} gününe atandı!",
                            Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@GroupSettingsActivity,
                            "Atama başarısız: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@GroupSettingsActivity,
                        "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Günden denetmeni kaldır
     */
    private fun removeAuditorFromDay(weekDay: Int) {
        val auditorToRemove = weeklyAuditors.find { it.weekDay == weekDay }
        if (auditorToRemove != null) {
            weeklyAuditors.remove(auditorToRemove)
            weeklyScheduleAdapter.notifyDataSetChanged()

            Toast.makeText(this, "${getDayName(weekDay)} denetmeni kaldırıldı", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Davet kodunu paylaş
     */
    private fun shareInviteCode() {
        Toast.makeText(this, "Davet kodu paylaşma özelliği eklenecek", Toast.LENGTH_SHORT).show()
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
     * Grup verilerini yükle
     */
    private fun loadGroupData() {
        binding.progressLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                checkGroupOwnership()

                val membersResult = firebaseManager.getGroupMembers(groupId)
                if (membersResult.isSuccess) {
                    val members = membersResult.getOrNull() ?: emptyList()
                    groupMembers.clear()
                    groupMembers.addAll(members)

                    runOnUiThread {
                        membersAdapter.notifyDataSetChanged()
                        binding.tvMembersCount.text = "${groupMembers.size} üye"

                        println("DEBUG: Grup üyeleri güncellendi:")
                        groupMembers.forEach { member ->
                            println("DEBUG: - ${member.userName} (${member.role})")
                        }
                    }
                }

                val auditorsResult = firebaseManager.getWeeklyAuditors(groupId)
                if (auditorsResult.isSuccess) {
                    val auditors = auditorsResult.getOrNull() ?: emptyList()
                    weeklyAuditors.clear()
                    weeklyAuditors.addAll(auditors)

                    runOnUiThread {
                        weeklyScheduleAdapter.notifyDataSetChanged()
                        updateWeeklyScheduleTitle()

                        // ✅ DEBUG: Adapter item count'unu kontrol et
                        println("DEBUG: WeeklyScheduleAdapter güncellendi - Item count: ${weeklyScheduleAdapter.itemCount}")
                    }
                }

                runOnUiThread {
                    binding.progressLoading.visibility = View.GONE
                }

            } catch (e: Exception) {
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
 * Grup üyeleri için adapter
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
            GroupRoles.AUDITOR -> "🔍 Denetmen"
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
 * Haftalık program için adapter - 7 GÜN DAHİL ✅
 */
class WeeklyScheduleAdapter(
    private val weeklyAuditors: List<WeeklyAuditor>,
    private val members: List<GroupMember>,
    private val onDayClick: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<WeeklyScheduleAdapter.DayViewHolder>() {

    // ✅ TÜM GÜNLER - 7 gün
    private val daysOfWeek = listOf(
        1 to "Pazartesi",
        2 to "Salı",
        3 to "Çarşamba",
        4 to "Perşembe",
        5 to "Cuma",
        6 to "Cumartesi",    // ✅ EKLENDI
        7 to "Pazar"         // ✅ EKLENDI
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

        println("DEBUG: Binding gün $position - $dayName (Gün numarası: $dayNumber)")

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
            Calendar.SATURDAY -> 6      // ✅ Cumartesi
            Calendar.SUNDAY -> 7        // ✅ Pazar
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

    override fun getItemCount(): Int {
        println("DEBUG: WeeklyScheduleAdapter getItemCount() çağrıldı - Dönen değer: ${daysOfWeek.size}")
        return daysOfWeek.size // ✅ Artık 7 döner (tüm günler)
    }

    companion object {
        fun getDayName(weekDay: Int): String {
            return when (weekDay) {
                1 -> "Pazartesi"
                2 -> "Salı"
                3 -> "Çarşamba"
                4 -> "Perşembe"
                5 -> "Cuma"
                6 -> "Cumartesi"    // ✅ EKLENDI
                7 -> "Pazar"        // ✅ EKLENDI
                else -> "Bilinmeyen"
            }
        }
    }
}