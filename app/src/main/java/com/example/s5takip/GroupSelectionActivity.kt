package com.fabrika.s5takip

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabrika.s5takip.databinding.ActivityGroupSelectionBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Grup seçim ekranı - Firebase entegreli ve type safe
 */
class GroupSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupSelectionBinding
    private lateinit var firebaseManager: FirebaseManager
    private lateinit var groupsAdapter: GroupsAdapter

    private var userGroups = mutableListOf<Group>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        println("DEBUG: GroupSelectionActivity başlatıldı")

        // Firebase manager'ı başlat
        firebaseManager = FirebaseManager.getInstance()

        // UI'ı ayarla
        setupUI()
        setupRecyclerView()
        setupClickListeners()
        loadUserGroups()
    }

    /**
     * UI'ı ayarla
     */
    private fun setupUI() {
        val currentUser = firebaseManager.getCurrentUser()
        binding.tvUserName.text = currentUser?.displayName ?: currentUser?.email ?: "Kullanıcı"

        println("DEBUG: UI ayarlandı - Kullanıcı: ${binding.tvUserName.text}")
    }

    /**
     * RecyclerView'ı ayarla
     */
    private fun setupRecyclerView() {
        groupsAdapter = GroupsAdapter(userGroups) { group ->
            joinGroup(group)
        }

        binding.rvGroups.layoutManager = LinearLayoutManager(this)
        binding.rvGroups.adapter = groupsAdapter

        println("DEBUG: RecyclerView ayarlandı")
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Grup oluştur
        binding.cvCreateGroup.setOnClickListener {
            println("DEBUG: Grup oluştur butonuna tıklandı")
            showCreateGroupDialog()
        }

        // Gruba katıl
        binding.cvJoinGroup.setOnClickListener {
            println("DEBUG: Gruba katıl butonuna tıklandı")
            showJoinGroupDialog()
        }

        // Çıkış yap
        binding.btnLogout.setOnClickListener {
            println("DEBUG: Çıkış butonuna tıklandı")
            performLogout()
        }

        println("DEBUG: Click listener'lar ayarlandı")
    }

    /**
     * Kullanıcının gruplarını yükle
     */
    private fun loadUserGroups() {
        println("DEBUG: Kullanıcı grupları yükleniyor...")

        lifecycleScope.launch {
            try {
                val result = firebaseManager.getUserGroups()

                if (result.isSuccess) {
                    val groups = result.getOrNull() ?: emptyList()
                    println("DEBUG: ${groups.size} grup bulundu")

                    userGroups.clear()
                    userGroups.addAll(groups)

                    runOnUiThread {
                        groupsAdapter.notifyDataSetChanged()

                        // Boş durum kontrolü
                        if (groups.isEmpty()) {
                            binding.rvGroups.visibility = View.GONE
                            binding.llEmptyGroups.visibility = View.VISIBLE
                            println("DEBUG: Boş grup listesi gösteriliyor")
                        } else {
                            binding.rvGroups.visibility = View.VISIBLE
                            binding.llEmptyGroups.visibility = View.GONE
                            println("DEBUG: ${groups.size} grup RecyclerView'da gösteriliyor")
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message
                    println("DEBUG: Grup yükleme hatası: $error")

                    runOnUiThread {
                        Toast.makeText(this@GroupSelectionActivity,
                            "Gruplar yüklenemedi: $error",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: loadUserGroups exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    Toast.makeText(this@GroupSelectionActivity,
                        "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Grup oluşturma dialog'u
     */
    private fun showCreateGroupDialog() {
        println("DEBUG: Grup oluşturma dialog'u gösteriliyor")

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.et_group_name)
        val etGroupDescription = dialogView.findViewById<TextInputEditText>(R.id.et_group_description)

        AlertDialog.Builder(this)
            .setTitle("Yeni Ekip Oluştur")
            .setView(dialogView)
            .setPositiveButton("Oluştur") { _, _ ->
                val groupName = etGroupName.text.toString().trim()
                val description = etGroupDescription.text.toString().trim()

                println("DEBUG: Dialog'dan grup bilgileri alındı - Ad: '$groupName', Açıklama: '$description'")

                if (groupName.isNotEmpty()) {
                    createGroup(groupName, description)
                } else {
                    Toast.makeText(this, "Ekip adı gerekli", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal") { _, _ ->
                println("DEBUG: Grup oluşturma iptal edildi")
            }
            .show()
    }

    /**
     * Gruba katılma dialog'u
     */
    private fun showJoinGroupDialog() {
        println("DEBUG: Gruba katılma dialog'u gösteriliyor")

        val dialogView = layoutInflater.inflate(R.layout.dialog_join_group, null)
        val etInviteCode = dialogView.findViewById<TextInputEditText>(R.id.et_invite_code)

        AlertDialog.Builder(this)
            .setTitle("Ekibe Katıl")
            .setView(dialogView)
            .setPositiveButton("Katıl") { _, _ ->
                val inviteCode = etInviteCode.text.toString().trim()

                println("DEBUG: Dialog'dan davet kodu alındı: '$inviteCode'")

                if (inviteCode.isNotEmpty()) {
                    joinGroupWithCode(inviteCode)
                } else {
                    Toast.makeText(this, "Davet kodu gerekli", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal") { _, _ ->
                println("DEBUG: Gruba katılma iptal edildi")
            }
            .show()
    }

    /**
     * Grup oluştur - TAMAMEN TYPE SAFE VERSİYON (Test fonksiyonu olmadan)
     */
    private fun createGroup(groupName: String, description: String) {
        println("DEBUG: ==================== GRUP OLUŞTURMA BAŞLADI ====================")
        println("DEBUG: Grup adı: '$groupName', Açıklama: '$description'")

        // Kullanıcı kontrolü
        val currentUser = firebaseManager.getCurrentUser()
        println("DEBUG: Firebase kullanıcı: ${currentUser?.email}")

        if (currentUser == null) {
            Toast.makeText(this, "❌ Giriş yapılmamış! Lütfen önce Gmail ile giriş yapın.", Toast.LENGTH_LONG).show()
            return
        }

        // Loading mesajı göster
        Toast.makeText(this, "⏳ Ekip oluşturuluyor... Lütfen bekleyin.", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                println("DEBUG: Firebase kullanıcı bilgileri:")
                println("DEBUG: - ID: ${currentUser.uid}")
                println("DEBUG: - Email: ${currentUser.email}")
                println("DEBUG: - DisplayName: ${currentUser.displayName}")

                // Firestore test kısmını atla, direkt grup oluştur
                println("DEBUG: Grup oluşturma çağrısı yapılıyor...")
                val result = firebaseManager.createGroup(groupName, description)

                runOnUiThread {
                    if (result.isSuccess) {
                        val group = result.getOrNull()
                        println("DEBUG: ✅ Grup başarıyla oluşturuldu!")
                        println("DEBUG: - Grup ID: ${group?.id}")
                        println("DEBUG: - Grup adı: ${group?.name}")
                        println("DEBUG: - Davet kodu: ${group?.inviteCode}")

                        Toast.makeText(this@GroupSelectionActivity,
                            "🎉 Ekip başarıyla oluşturuldu!\n\n" +
                                    "📋 Ekip Adı: ${group?.name}\n" +
                                    "🔑 Davet Kodu: ${group?.inviteCode}\n\n" +
                                    "Bu kodu paylaşarak ekibe üye davet edebilirsiniz!",
                            Toast.LENGTH_LONG).show()

                        // Grupları yeniden yükle
                        loadUserGroups()
                    } else {
                        val error = result.exceptionOrNull()
                        println("DEBUG: ❌ Grup oluşturma başarısız!")
                        println("DEBUG: Hata: ${error?.message}")
                        println("DEBUG: Hata türü: ${error?.javaClass?.simpleName}")
                        error?.printStackTrace()

                        // Daha detaylı hata mesajı
                        val errorMessage = when {
                            error?.message?.contains("PERMISSION_DENIED") == true ->
                                "Firestore izni reddedildi. Lütfen Firebase Console'da Firestore Rules'ı kontrol edin."
                            error?.message?.contains("network") == true ->
                                "İnternet bağlantısı sorunu. Lütfen bağlantınızı kontrol edin."
                            error?.message?.contains("timeout") == true ->
                                "Zaman aşımı. Lütfen tekrar deneyin."
                            else -> error?.message ?: "Bilinmeyen hata"
                        }

                        Toast.makeText(this@GroupSelectionActivity,
                            "❌ Ekip oluşturulamadı!\n\n" +
                                    "Hata: $errorMessage\n\n" +
                                    "Lütfen tekrar deneyin.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: ❌ EXCEPTION oluştu: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    Toast.makeText(this@GroupSelectionActivity,
                        "💥 Beklenmeyen hata!\n\n" +
                                "Detay: ${e.message}\n\n" +
                                "Lütfen uygulamayı yeniden başlatın.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }

        println("DEBUG: ==================== GRUP OLUŞTURMA FUNCTION BİTTİ ====================")
    }

    /**
     * Davet kodu ile gruba katıl
     */
    private fun joinGroupWithCode(inviteCode: String) {
        println("DEBUG: Davet kodu ile gruba katılma başlatıldı - Kod: '$inviteCode'")

        Toast.makeText(this, "Ekibe katılınıyor...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val result = firebaseManager.joinGroup(inviteCode)

                runOnUiThread {
                    if (result.isSuccess) {
                        val group = result.getOrNull()
                        println("DEBUG: Gruba başarıyla katıldı - Grup: ${group?.name}")

                        Toast.makeText(this@GroupSelectionActivity,
                            "✅ '${group?.name}' ekibine katıldınız!",
                            Toast.LENGTH_SHORT).show()

                        // Grupları yeniden yükle
                        loadUserGroups()
                    } else {
                        val error = result.exceptionOrNull()
                        println("DEBUG: Gruba katılma hatası: ${error?.message}")

                        Toast.makeText(this@GroupSelectionActivity,
                            "❌ Ekibe katılınamadı!\n\nHata: ${error?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: joinGroupWithCode exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    Toast.makeText(this@GroupSelectionActivity,
                        "💥 Beklenmeyen hata!\n\nDetay: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Gruba katıl (mevcut gruplardan)
     */
    private fun joinGroup(group: Group) {
        println("DEBUG: Mevcut gruptan seçildi - Grup: ${group.name}")

        // Bu gruba giriş yap ve MainActivity'ye git
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("selected_group_id", group.id)
        intent.putExtra("selected_group_name", group.name)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Çıkış yap
     */
    private fun performLogout() {
        println("DEBUG: Çıkış işlemi başlatıldı")

        lifecycleScope.launch {
            try {
                firebaseManager.signOut()

                runOnUiThread {
                    println("DEBUG: Çıkış başarılı, LoginActivity'ye yönlendiriliyor")

                    // Login ekranına geri dön
                    val intent = Intent(this@GroupSelectionActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                println("DEBUG: Çıkış hatası: ${e.message}")

                runOnUiThread {
                    Toast.makeText(this@GroupSelectionActivity,
                        "Çıkış hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Firestore bağlantısını test et (Debug için) - TYPE SAFE ÇÖZÜM
     */
    private fun testFirestoreConnection() {
        println("DEBUG: Firestore bağlantısı test ediliyor...")

        lifecycleScope.launch {
            try {
                // TYPE SAFE MAP - Explicit casting
                val testData = mutableMapOf<String, Any>()
                testData["test"] = "connection" as Any
                testData["timestamp"] = System.currentTimeMillis() as Any
                testData["user"] = (firebaseManager.getCurrentUser()?.email ?: "unknown") as Any

                val result = firebaseManager.testFirestoreWrite(testData)

                runOnUiThread {
                    if (result.isSuccess) {
                        Toast.makeText(this@GroupSelectionActivity,
                            "✅ Firestore bağlantısı başarılı!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@GroupSelectionActivity,
                            "❌ Firestore bağlantı hatası: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                println("DEBUG: Firestore test hatası: ${e.message}")

                runOnUiThread {
                    Toast.makeText(this@GroupSelectionActivity,
                        "💥 Firestore test hatası: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/**
 * Gruplar için geliştirilmiş adapter
 */
class GroupsAdapter(
    private val groups: List<Group>,
    private val onGroupClick: (Group) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardView: androidx.cardview.widget.CardView = view as androidx.cardview.widget.CardView
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): GroupViewHolder {
        println("DEBUG: GroupsAdapter - ViewHolder oluşturuluyor")

        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context)

        val layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
            androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(16, 8, 16, 8)
        cardView.layoutParams = layoutParams
        cardView.radius = 12f
        cardView.cardElevation = 4f
        cardView.useCompatPadding = true

        return GroupViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        println("DEBUG: GroupsAdapter - Grup bağlanıyor: ${group.name}")

        val context = holder.itemView.context

        // İç layout oluştur
        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.VERTICAL
        innerLayout.setPadding(24, 20, 24, 20)

        // Grup adı
        val titleText = android.widget.TextView(context)
        titleText.text = group.name
        titleText.textSize = 18f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        titleText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.primary))

        // Grup bilgileri
        val infoText = android.widget.TextView(context)
        infoText.text = "👥 ${group.memberCount} üye\n🔑 Kod: ${group.inviteCode}"
        infoText.textSize = 14f
        infoText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.gray_dark))
        infoText.setPadding(0, 8, 0, 0)

        // Açıklama (varsa)
        if (group.description.isNotEmpty()) {
            val descText = android.widget.TextView(context)
            descText.text = group.description
            descText.textSize = 12f
            descText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.gray_medium))
            descText.setPadding(0, 8, 0, 0)

            innerLayout.addView(titleText)
            innerLayout.addView(infoText)
            innerLayout.addView(descText)
        } else {
            innerLayout.addView(titleText)
            innerLayout.addView(infoText)
        }

        // CardView'ı temizle ve yeni içeriği ekle
        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)

        holder.cardView.setOnClickListener {
            println("DEBUG: Grup seçildi: ${group.name}")
            onGroupClick(group)
        }
    }

    override fun getItemCount(): Int {
        println("DEBUG: GroupsAdapter - Toplam grup sayısı: ${groups.size}")
        return groups.size
    }
}