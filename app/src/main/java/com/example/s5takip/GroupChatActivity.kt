package com.fabrika.s5takip

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabrika.s5takip.databinding.ActivityGroupChatBinding
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.*

/**
 * Grup sohbet ekranı - Tam çalışır versiyon
 */
class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private lateinit var firebaseManager: FirebaseManager

    private var groupId: String = ""
    private var groupName: String = ""
    private var chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private var selectedImageUri: Uri? = null

    // Fotoğraf seçme için launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            if (selectedImageUri != null) {
                sendImageMessage()
            }
        } else if (result.resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, "Fotoğraf seçilirken hata: ${ImagePicker.getError(result.data)}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
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
        setupRecyclerView()
        setupClickListeners()
        loadChatMessages()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "$groupName - Sohbet"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * UI'ı ayarla
     */
    private fun setupUI() {
        binding.tvChatTitle.text = "$groupName Sohbeti"

        // Kullanıcı bilgilerini göster
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            binding.tvCurrentUser.text = "Siz: ${currentUser.displayName ?: currentUser.email}"
        }
    }

    /**
     * RecyclerView'ı ayarla
     */
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.rvChatMessages.layoutManager = LinearLayoutManager(this).apply {
            // Yeni mesajlar altta görünsün
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = chatAdapter
    }

    /**
     * Click listener'ları ayarla
     */
    private fun setupClickListeners() {
        // Mesaj gönder butonu
        binding.btnSendMessage.setOnClickListener {
            sendTextMessage()
        }

        // Fotoğraf gönder butonu
        binding.btnSendPhoto.setOnClickListener {
            openImagePicker()
        }

        // Yenile butonu
        binding.btnRefreshChat.setOnClickListener {
            loadChatMessages()
        }
    }

    /**
     * Chat mesajlarını yükle
     */
    private fun loadChatMessages() {
        binding.progressLoading.visibility = View.VISIBLE
        println("DEBUG: Chat mesajları yükleniyor - Grup ID: $groupId")

        lifecycleScope.launch {
            try {
                val result = firebaseManager.getChatMessages(groupId, 50)

                println("DEBUG: FirebaseManager sonucu - Başarılı: ${result.isSuccess}")

                if (result.isSuccess) {
                    val messages = result.getOrNull() ?: emptyList()
                    println("DEBUG: ${messages.size} mesaj alındı")

                    chatMessages.clear()
                    chatMessages.addAll(messages)

                    runOnUiThread {
                        chatAdapter.notifyDataSetChanged()

                        // En son mesaja kaydır
                        if (chatMessages.isNotEmpty()) {
                            binding.rvChatMessages.scrollToPosition(chatMessages.size - 1)
                        }

                        // Boş durum kontrolü
                        if (chatMessages.isEmpty()) {
                            binding.tvEmptyChat.visibility = View.VISIBLE
                            println("DEBUG: Chat boş, boş durum mesajı gösteriliyor")
                        } else {
                            binding.tvEmptyChat.visibility = View.GONE
                            println("DEBUG: ${chatMessages.size} mesaj gösteriliyor")
                        }

                        binding.progressLoading.visibility = View.GONE
                    }
                } else {
                    val error = result.exceptionOrNull()
                    println("DEBUG: Chat mesajları yükleme hatası: ${error?.message}")

                    runOnUiThread {
                        binding.progressLoading.visibility = View.GONE

                        // Hata mesajını daha detaylı göster
                        val errorMessage = when {
                            error?.message?.contains("permission") == true ->
                                "Firestore izin hatası. Firebase Rules kontrol edin."
                            error?.message?.contains("network") == true ->
                                "İnternet bağlantısı sorunu"
                            else -> "Mesajlar yüklenemedi: ${error?.message}"
                        }

                        Toast.makeText(this@GroupChatActivity, errorMessage, Toast.LENGTH_LONG).show()

                        // Boş durum göster
                        binding.tvEmptyChat.visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                println("DEBUG: Chat yükleme exception: ${e.message}")
                e.printStackTrace()

                runOnUiThread {
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(this@GroupChatActivity,
                        "Beklenmeyen hata: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.tvEmptyChat.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Metin mesajı gönder
     */
    private fun sendTextMessage() {
        val messageText = binding.etMessageInput.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Lütfen bir mesaj yazın", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Giriş yapılmamış", Toast.LENGTH_SHORT).show()
            return
        }

        // Mesaj gönder butonunu deaktif et
        binding.btnSendMessage.isEnabled = false

        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: currentUser.email ?: "Kullanıcı",
            senderAvatar = currentUser.photoUrl?.toString() ?: "",
            message = messageText,
            messageType = "TEXT",
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            try {
                val result = firebaseManager.saveChatMessage(chatMessage)

                runOnUiThread {
                    if (result.isSuccess) {
                        // Mesajı listeye ekle
                        chatMessages.add(chatMessage)
                        chatAdapter.notifyItemInserted(chatMessages.size - 1)

                        // En son mesaja kaydır
                        binding.rvChatMessages.scrollToPosition(chatMessages.size - 1)

                        // Input'u temizle
                        binding.etMessageInput.text?.clear()

                        // Boş durum mesajını gizle
                        binding.tvEmptyChat.visibility = View.GONE

                    } else {
                        Toast.makeText(this@GroupChatActivity,
                            "Mesaj gönderilemedi: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT).show()
                    }

                    binding.btnSendMessage.isEnabled = true
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@GroupChatActivity,
                        "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnSendMessage.isEnabled = true
                }
            }
        }
    }

    /**
     * Fotoğraf seçici aç
     */
    private fun openImagePicker() {
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(800, 800)
            .createIntent { intent ->
                imagePickerLauncher.launch(intent)
            }
    }

    /**
     * Fotoğraf mesajı gönder
     */
    private fun sendImageMessage() {
        if (selectedImageUri == null) return

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Giriş yapılmamış", Toast.LENGTH_SHORT).show()
            return
        }

        // Fotoğraf gönder butonunu deaktif et
        binding.btnSendPhoto.isEnabled = false

        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: currentUser.email ?: "Kullanıcı",
            senderAvatar = currentUser.photoUrl?.toString() ?: "",
            message = selectedImageUri.toString(), // Gerçek uygulamada Firebase Storage'a yüklenip URL alınır
            messageType = "IMAGE",
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            try {
                val result = firebaseManager.saveChatMessage(chatMessage)

                runOnUiThread {
                    if (result.isSuccess) {
                        // Mesajı listeye ekle
                        chatMessages.add(chatMessage)
                        chatAdapter.notifyItemInserted(chatMessages.size - 1)

                        // En son mesaja kaydır
                        binding.rvChatMessages.scrollToPosition(chatMessages.size - 1)

                        // Boş durum mesajını gizle
                        binding.tvEmptyChat.visibility = View.GONE

                        Toast.makeText(this@GroupChatActivity, "Fotoğraf gönderildi!", Toast.LENGTH_SHORT).show()

                    } else {
                        Toast.makeText(this@GroupChatActivity,
                            "Fotoğraf gönderilemedi: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT).show()
                    }

                    binding.btnSendPhoto.isEnabled = true
                    selectedImageUri = null
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@GroupChatActivity,
                        "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnSendPhoto.isEnabled = true
                    selectedImageUri = null
                }
            }
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
 * Chat mesajları için adapter - Tam çalışır versiyon
 */
class ChatAdapter(
    private val messages: List<ChatMessage>
) : androidx.recyclerview.widget.RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardView: androidx.cardview.widget.CardView = view as androidx.cardview.widget.CardView
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context)

        val layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
            androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(8, 4, 8, 4)
        cardView.layoutParams = layoutParams
        cardView.radius = 8f
        cardView.cardElevation = 2f

        return MessageViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context
        val currentUser = FirebaseAuth.getInstance().currentUser

        // Kendi mesajımız mı kontrol et
        val isOwnMessage = currentUser?.uid == message.senderId

        // İç layout oluştur
        val innerLayout = android.widget.LinearLayout(context)
        innerLayout.orientation = android.widget.LinearLayout.HORIZONTAL
        innerLayout.setPadding(12, 8, 12, 8)
        innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        // Kendi mesajımızsa farklı renk
        if (isOwnMessage) {
            holder.cardView.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.primary)
            )
        } else {
            holder.cardView.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.white)
            )
        }

        // Avatar
        val avatarFrame = android.widget.FrameLayout(context)
        avatarFrame.layoutParams = android.widget.LinearLayout.LayoutParams(40, 40)

        val avatarText = android.widget.TextView(context)
        avatarText.text = message.senderName.take(1).uppercase()
        avatarText.textSize = 14f
        avatarText.setTypeface(null, android.graphics.Typeface.BOLD)
        avatarText.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.white))
        avatarText.background = if (isOwnMessage) {
            androidx.core.content.ContextCompat.getDrawable(context, R.color.accent)
        } else {
            androidx.core.content.ContextCompat.getDrawable(context, R.color.gray_dark)
        }
        avatarText.gravity = android.view.Gravity.CENTER
        avatarText.layoutParams = android.widget.FrameLayout.LayoutParams(40, 40)

        avatarFrame.addView(avatarText)

        // Mesaj içeriği
        val messageContent = android.widget.LinearLayout(context)
        messageContent.orientation = android.widget.LinearLayout.VERTICAL
        messageContent.setPadding(12, 0, 0, 0)
        val contentParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        messageContent.layoutParams = contentParams

        // Gönderen adı
        val senderText = android.widget.TextView(context)
        senderText.text = if (isOwnMessage) "Siz" else message.senderName
        senderText.textSize = 12f
        senderText.setTypeface(null, android.graphics.Typeface.BOLD)
        senderText.setTextColor(
            if (isOwnMessage)
                androidx.core.content.ContextCompat.getColor(context, R.color.white)
            else
                androidx.core.content.ContextCompat.getColor(context, R.color.primary)
        )

        // Mesaj içeriği
        if (message.messageType == "IMAGE") {
            // Fotoğraf mesajı
            val imageView = android.widget.ImageView(context)
            imageView.layoutParams = android.widget.LinearLayout.LayoutParams(
                200, 200
            )
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP

            // Basit URI yükleme
            try {
                val uri = Uri.parse(message.message)
                imageView.setImageURI(uri)
            } catch (e: Exception) {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            messageContent.addView(senderText)
            messageContent.addView(imageView)
        } else {
            // Metin mesajı
            val messageText = android.widget.TextView(context)
            messageText.text = message.message
            messageText.textSize = 14f
            messageText.setTextColor(
                if (isOwnMessage)
                    androidx.core.content.ContextCompat.getColor(context, R.color.white)
                else
                    androidx.core.content.ContextCompat.getColor(context, R.color.black)
            )

            messageContent.addView(senderText)
            messageContent.addView(messageText)
        }

        // Tarih
        val timeText = android.widget.TextView(context)
        timeText.text = formatTime(message.createdAt)
        timeText.textSize = 10f
        timeText.setTextColor(
            if (isOwnMessage)
                androidx.core.content.ContextCompat.getColor(context, R.color.white)
            else
                androidx.core.content.ContextCompat.getColor(context, R.color.gray_dark)
        )
        timeText.alpha = 0.7f
        messageContent.addView(timeText)

        innerLayout.addView(avatarFrame)
        innerLayout.addView(messageContent)

        // CardView'ı temizle ve yeni içeriği ekle
        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)
    }

    override fun getItemCount(): Int = messages.size

    /**
     * Zaman formatla
     */
    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}