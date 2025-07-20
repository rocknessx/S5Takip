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
 * Grup ayarları ekranı - Üye görünümü ve grup sahibi için yönetim
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

        // Başlangıçta düzenleme butonlarını gizle
        binding.btnShareInviteCode.visibility = View.GONE
    }

    /**
     * RecyclerView'ları ayarla
     */
    private fun setupRecyclerViews() {
        // Grup üyeleri adapter - sadece görüntüleme
        membersAdapter = GroupMembersAdapter(groupMembers) { member ->
            showMemberProfile(member)
        }
        binding.rvGroupMembers.layoutManager = LinearLayoutManager(this)
        binding.rvGroupMembers.adapter = membersAdapter

        // Haftalık program adapter - düzenleme yetkisi kontrolü ile
        weeklyScheduleAdapter = WeeklyScheduleAdapter(weeklyAuditors, groupMembers) { weekDay ->
            if (isGroupOwner) {
                showAuditorSelectionDialog(weekDay)
            } else {
                showReadOnlyScheduleInfo(weekDay)
            }
        }
        binding.rvWeeklySchedule.layoutManager = LinearLayoutManager(this)
        binding.rvWeeklySchedule.adapter = weeklyScheduleAdapter
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Yenile butonu
        binding.btnRefreshData.setOnClickListener {
            loadGroupData()
        }

        // Davet kodu paylaş (sadece grup sahibi için)
        binding.btnShareInviteCode.setOnClickListener {
            if (isGroupOwner) {
                shareInviteCode()
            }
        }
    }

    /**
     * Grup verilerini yükle
     */
    private fun loadGroupData() {
        binding.progressLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Önce grup bilgilerini al ve yetki kontrolü yap
                checkGroupOwnership()

                // Grup üyelerini yükle
                val membersResult = firebaseManager.getGroupMembers(groupId)
                if (membersResult.isSuccess) {
                    val members = membersResult.getOrNull() ?: emptyList()
                    groupMembers.clear()
                    groupMembers.addAll(members)

                    runOnUiThread {
                        membersAdapter.notifyDataSetChanged()
                        binding.tvMembersCount.text = "${groupMembers.size} üye"
                    }
                }

                // Haftalık denetmenleri yükle
                val auditorsResult = firebaseManager.getWeeklyAuditors(groupId)
                if (auditorsResult.isSuccess) {
                    val auditors = auditorsResult.getOrNull() ?: emptyList()
                    weeklyAuditors.clear()
                    weeklyAuditors.addAll(auditors)

                    runOnUiThread {
                        weeklyScheduleAdapter.notifyDataSetChanged()
                        updateWeeklyScheduleTitle()
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

    /**
     * Grup sahipliği kontrolü yap
     */
    private fun checkGroupOwnership() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Basit kontrol - gerçek uygulamada Firestore'dan grup bilgilerini alın
        lifecycleScope.launch {
            try {
                // Grup üyelerini kontrol et
                val membersResult = firebaseManager.getGroupMembers(groupId)
                if (membersResult.isSuccess) {
                    val members = membersResult.getOrNull() ?: emptyList()
                    val currentUserMember = members.find { it.userId == currentUserId }

                    isGroupOwner = currentUserMember?.role == GroupRoles.OWNER

                    runOnUiThread {
                        updateUIBasedOnPermissions()
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
    private fun updateUIBasedOnPermissions() {
        if (isGroupOwner) {
            binding.btnShareInviteCode.visibility = View.VISIBLE
            binding.tvScheduleDescription.text = "Her güne bir denetmen atayın. Günlere tıklayarak denetmen seçebilirsiniz."
        } else {
            binding.btnShareInviteCode.visibility = View.GONE
            binding.tvScheduleDescription.text = "Haftalık denetmen programını görüntülüyorsunuz. Değişiklik yapmak için grup sahibi ile iletişime geçin."
        }
    }

    /**
     * Haftalık program başlığını güncelle
     */
    private fun updateWeeklyScheduleTitle() {
        val titleText = if (isGroupOwner) {
            "📅 Haftalık Denetmen Programı (Düzenle)"
        } else {
            "📅 Haftalık Denetmen Programı (Görüntüle)"
        }
        binding.tvWeeklyScheduleTitle.text = titleText
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
     * Denetmen seçim dialog'u (sadece grup sahibi için)
     */
    private fun showAuditorSelectionDialog(weekDay: Int) {
        if (!isGroupOwner) {
            showReadOnlyScheduleInfo(weekDay)
            return
        }

        val dayName = getDayName(weekDay)
        val memberNames = groupMembers.map { it.userName }.toTypedArray()

        if (memberNames.isEmpty()) {
            Toast.makeText(this, "Grup üyesi bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        // Mevcut denetmeni bul
        val currentAuditor = weeklyAuditors.find { it.weekDay == weekDay }
        val currentIndex = if (currentAuditor != null) {
            groupMembers.indexOfFirst { it.userId == currentAuditor.auditorId }
        } else {
            -1
        }

        AlertDialog.Builder(this)
            .setTitle("$dayName Denetmeni Seç")
            .setSingleChoiceItems(memberNames, currentIndex) { dialog, which ->
                val selectedMember = groupMembers[which]
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
     * Denetmeni güne ata
     */
    private fun assignAuditorToDay(weekDay: Int, member: GroupMember) {
        lifecycleScope.launch {
            try {
                // Mevcut atamaları kontrol et
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
        // Grup bilgilerini al ve davet kodunu paylaş
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
     * Geri buton basıldığında
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * Grup üyeleri için adapter - Sadece görüntüleme
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

        // İç layout oluştur
        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(16, 12, 16, 12)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        // Profil avatarı
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

        // Kullanıcı bilgileri
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

        // CardView'ı temizle ve yeni içeriği ekle
        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)

        holder.cardView.setOnClickListener {
            onMemberClick(member)
        }
    }

    override fun getItemCount(): Int = members.size
}

/**
 * Haftalık program için adapter - Güncellendi
 */
class WeeklyScheduleAdapter(
    private val weeklyAuditors: List<WeeklyAuditor>,
    private val members: List<GroupMember>,
    private val onDayClick: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<WeeklyScheduleAdapter.DayViewHolder>() {

    private val daysOfWeek = listOf(
        1 to "Pazartesi",
        2 to "Salı",
        3 to "Çarşamba",
        4 to "Perşembe",
        5 to "Cuma",
        6 to "Cumartesi",
        7 to "Pazar"
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

        // Bu güne atanmış denetmeni bul
        val assignedAuditor = weeklyAuditors.find { it.weekDay == dayNumber }
        val auditorName = if (assignedAuditor != null) {
            members.find { it.userId == assignedAuditor.auditorId }?.userName ?: "Bilinmeyen"
        } else {
            "Atanmamış"
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

        // İç layout oluştur
        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(16, 12, 16, 12)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        // Bugün ise farklı renk
        if (dayNumber == todayNumber) {
            innerLayout.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
        }

        // Gün adı
        val dayText = android.widget.TextView(context)
        dayText.text = if (dayNumber == todayNumber) "🔴 $dayName (BUGÜN)" else dayName
        dayText.textSize = 16f
        dayText.setTypeface(null, android.graphics.Typeface.BOLD)
        val dayColor = if (dayNumber == todayNumber) R.color.white else R.color.primary
        dayText.setTextColor(androidx.core.content.ContextCompat.getColor(context, dayColor))
        dayText.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        // Denetmen adı
        val auditorText = android.widget.TextView(context)
        auditorText.text = auditorName
        auditorText.textSize = 14f
        val auditorColor = if (dayNumber == todayNumber) {
            R.color.white
        } else if (assignedAuditor != null) {
            R.color.black
        } else {
            R.color.gray_dark
        }
        auditorText.setTextColor(androidx.core.content.ContextCompat.getColor(context, auditorColor))

        // Ok işareti
        val arrowText = android.widget.TextView(context)
        arrowText.text = "›"
        arrowText.textSize = 20f
        val arrowColor = if (dayNumber == todayNumber) R.color.white else R.color.gray_medium
        arrowText.setTextColor(androidx.core.content.ContextCompat.getColor(context, arrowColor))

        innerLayout.addView(dayText)
        innerLayout.addView(auditorText)
        innerLayout.addView(arrowText)

        // CardView'ı temizle ve yeni içeriği ekle
        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)

        holder.cardView.setOnClickListener {
            onDayClick(dayNumber)
        }
    }

    override fun getItemCount(): Int = daysOfWeek.size
}