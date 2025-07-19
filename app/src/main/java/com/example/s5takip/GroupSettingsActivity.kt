package com.fabrika.s5takip

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabrika.s5takip.databinding.ActivityGroupSettingsBinding
import kotlinx.coroutines.launch
import java.util.*

/**
 * Grup ayarları ekranı - Üye listesi ve haftalık denetmen ataması
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
    }

    /**
     * RecyclerView'ları ayarla
     */
    private fun setupRecyclerViews() {
        // Grup üyeleri adapter
        membersAdapter = GroupMembersAdapter(groupMembers) { member ->
            showMemberOptionsDialog(member)
        }
        binding.rvGroupMembers.layoutManager = LinearLayoutManager(this)
        binding.rvGroupMembers.adapter = membersAdapter

        // Haftalık program adapter
        weeklyScheduleAdapter = WeeklyScheduleAdapter(weeklyAuditors, groupMembers) { weekDay ->
            showAuditorSelectionDialog(weekDay)
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

        // Davet kodu paylaş
        binding.btnShareInviteCode.setOnClickListener {
            shareInviteCode()
        }
    }

    /**
     * Grup verilerini yükle
     */
    private fun loadGroupData() {
        binding.progressLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
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
     * Üye seçenekleri dialog'u
     */
    private fun showMemberOptionsDialog(member: GroupMember) {
        val options = arrayOf("Profili Görüntüle", "Haftalık Programa Ekle")

        AlertDialog.Builder(this)
            .setTitle(member.userName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showMemberProfile(member)
                    1 -> showWeeklyScheduleForMember(member)
                }
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
     * Üye için haftalık program göster
     */
    private fun showWeeklyScheduleForMember(member: GroupMember) {
        val memberDays = weeklyAuditors.filter { it.auditorId == member.userId }
        val message = if (memberDays.isNotEmpty()) {
            "Bu üye şu günlerde denetmen:\n" +
                    memberDays.joinToString("\n") { "${getDayName(it.weekDay)}" }
        } else {
            "Bu üye henüz hiçbir güne atanmamış"
        }

        AlertDialog.Builder(this)
            .setTitle("${member.userName} - Haftalık Program")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    /**
     * Denetmen seçim dialog'u
     */
    private fun showAuditorSelectionDialog(weekDay: Int) {
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

        // İç layout oluştur
        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(16, 12, 16, 12)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        // Profil ikonu
        val profileIcon = android.widget.TextView(context)
        profileIcon.text = member.userName.take(1).uppercase()
        profileIcon.textSize = 18f
        profileIcon.setTypeface(null, android.graphics.Typeface.BOLD)
        profileIcon.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.white))
        profileIcon.background = androidx.core.content.ContextCompat.getDrawable(context, R.color.primary)
        profileIcon.gravity = android.view.Gravity.CENTER
        profileIcon.width = 48
        profileIcon.height = 48

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
        roleText.text = member.role
        roleText.textSize = 12f
        roleText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
        roleText.setPadding(8, 4, 8, 4)
        roleText.background = androidx.core.content.ContextCompat.getDrawable(context, R.color.gray_light)

        userInfo.addView(nameText)
        userInfo.addView(emailText)
        userInfo.addView(roleText)

        innerLayout.addView(profileIcon)
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
 * Haftalık program için adapter
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

        // İç layout oluştur
        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(16, 12, 16, 12)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        // Gün adı
        val dayText = android.widget.TextView(context)
        dayText.text = dayName
        dayText.textSize = 16f
        dayText.setTypeface(null, android.graphics.Typeface.BOLD)
        dayText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))
        dayText.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        // Denetmen adı
        val auditorText = android.widget.TextView(context)
        auditorText.text = auditorName
        auditorText.textSize = 14f
        auditorText.setTextColor(
            if (assignedAuditor != null)
                androidx.core.content.ContextCompat.getColor(context, R.color.black)
            else
                androidx.core.content.ContextCompat.getColor(context, R.color.gray_dark)
        )

        // Ok işareti
        val arrowText = android.widget.TextView(context)
        arrowText.text = "›"
        arrowText.textSize = 20f
        arrowText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.gray_medium))

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