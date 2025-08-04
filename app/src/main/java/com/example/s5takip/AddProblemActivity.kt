package com.fabrika.s5takip

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.fabrika.s5takip.databinding.ActivityAddProblemBinding
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.auth.FirebaseAuth

/**
 * Problem ekleme ekranı - Son güncel versiyon
 */
class AddProblemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProblemBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var photoManager: PhotoManager
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUser: User? = null
    private var selectedImageUri: Uri? = null
    private var selectedPriority: ProblemPriority = ProblemPriority.MEDIUM
    private var currentGroupId: String = "" // ✅ Grup ID'si

    // Fotoğraf seçme için launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            if (selectedImageUri != null) {
                // Fotoğrafı göster
                binding.ivProblemPhoto.setImageURI(selectedImageUri)
                binding.ivProblemPhoto.alpha = 1.0f
                binding.tvPhotoInstruction.text = "✓ Fotoğraf seçildi"

                // Kaydet butonunu kontrol et
                checkFormValidity()
            }
        } else if (result.resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, "Fotoğraf seçilirken hata: ${ImagePicker.getError(result.data)}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProblemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Intent'ten grup ID'sini al
        currentGroupId = intent.getStringExtra("group_id") ?: ""
        println("DEBUG: AddProblemActivity başlatıldı - Grup ID: $currentGroupId")

        if (currentGroupId.isEmpty()) {
            Toast.makeText(this, "Grup bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Başlangıç ayarları
        initializeComponents()
        loadCurrentUser()
        setupClickListeners()
        setupTextWatchers()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Problem Ekle"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Bileşenleri başlat
     */
    private fun initializeComponents() {
        databaseHelper = DatabaseHelper(this)
        photoManager = PhotoManager(this)
        sharedPreferences = getSharedPreferences("s5_takip_prefs", MODE_PRIVATE)

        println("DEBUG: Bileşenler başlatıldı")
    }

    /**
     * Mevcut kullanıcıyı yükle
     */
    private fun loadCurrentUser() {
        val userId = sharedPreferences.getString("current_user_id", null)
        println("DEBUG: Kullanıcı ID'si SharedPreferences'tan alındı: $userId")

        if (userId != null) {
            currentUser = databaseHelper.getUserById(userId)
            println("DEBUG: Veritabanından kullanıcı alındı: ${currentUser?.name}")
        }

        // Eğer kullanıcı yoksa test kullanıcısı oluştur
        if (currentUser == null) {
            println("DEBUG: Kullanıcı bulunamadı, test kullanıcısı oluşturuluyor...")
            createTestUser()
        }

        // Eğer hala kullanıcı yoksa veya denetmen değilse, geri dön
        if (currentUser == null || currentUser?.role != UserRole.AUDITOR) {
            println("DEBUG: Denetmen yetkisi yok, aktivite kapatılıyor")
            Toast.makeText(this, "Bu işlem için denetmen yetkisi gerekli", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        println("DEBUG: Kullanıcı başarıyla yüklendi: ${currentUser?.name} (${currentUser?.role})")
    }

    /**
     * Test kullanıcısı oluştur
     */
    private fun createTestUser() {
        try {
            val testUser = User(
                name = "Test Denetmen",
                email = "test@fabrika.com",
                department = "Kalıp Üretim", // Department parametresi eklendi
                role = UserRole.AUDITOR
            )

            val success = databaseHelper.insertUser(testUser)
            if (success) {
                currentUser = testUser
                // Kullanıcıyı SharedPreferences'a kaydet
                sharedPreferences.edit()
                    .putString("current_user_id", testUser.id)
                    .apply()

                println("DEBUG: Test kullanıcısı başarıyla oluşturuldu: ${testUser.name}")
            } else {
                println("DEBUG: Test kullanıcısı oluşturulamadı")
            }
        } catch (e: Exception) {
            println("DEBUG: Test kullanıcısı oluşturma hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Fotoğraf çekme/seçme
        binding.llPhotoContainer.setOnClickListener {
            println("DEBUG: Fotoğraf seçme butonuna tıklandı")
            openImagePicker()
        }

        // Öncelik seçimi
        binding.rgPriority.setOnCheckedChangeListener { _, checkedId ->
            selectedPriority = when (checkedId) {
                R.id.rb_low -> ProblemPriority.LOW
                R.id.rb_high -> ProblemPriority.HIGH
                else -> ProblemPriority.MEDIUM
            }
            println("DEBUG: Öncelik seçildi: $selectedPriority")
        }

        // İptal butonu
        binding.btnCancel.setOnClickListener {
            println("DEBUG: İptal butonuna tıklandı")
            finish()
        }

        // Kaydet butonu
        binding.btnSaveProblem.setOnClickListener {
            println("DEBUG: Kaydet butonuna tıklandı")
            saveProblem()
        }

        println("DEBUG: Click listener'lar ayarlandı")
    }

    /**
     * Metin değişikliklerini izle
     */
    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkFormValidity()
            }
        }

        binding.etProblemDescription.addTextChangedListener(textWatcher)
        binding.etLocation.addTextChangedListener(textWatcher)

        println("DEBUG: Text watcher'lar ayarlandı")
    }

    /**
     * Fotoğraf seçici/çekici aç
     */
    private fun openImagePicker() {
        try {
            ImagePicker.with(this)
                .crop()                    // Kırpma özelliği
                .compress(1024)            // Dosya boyutunu küçült
                .maxResultSize(1080, 1080) // Maksimum çözünürlük
                .createIntent { intent ->
                    println("DEBUG: ImagePicker intent başlatılıyor")
                    imagePickerLauncher.launch(intent)
                }
        } catch (e: Exception) {
            println("DEBUG: ImagePicker açma hatası: ${e.message}")
            Toast.makeText(this, "Fotoğraf seçici açılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Form geçerliliğini kontrol et
     */
    private fun checkFormValidity() {
        val description = binding.etProblemDescription.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()

        val isFormValid = description.isNotEmpty() &&
                location.isNotEmpty() &&
                selectedImageUri != null

        binding.btnSaveProblem.isEnabled = isFormValid

        println("DEBUG: Form geçerliliği: $isFormValid (Açıklama: ${description.isNotEmpty()}, Konum: ${location.isNotEmpty()}, Fotoğraf: ${selectedImageUri != null})")
    }


    private fun saveProblem() {
        val description = binding.etProblemDescription.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()

        println("DEBUG: Problem kaydetme başladı - Grup ID: $currentGroupId")

        // Form kontrolü
        if (description.isEmpty() || location.isEmpty() || selectedImageUri == null) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun ve fotoğraf seçin", Toast.LENGTH_SHORT).show()
            return
        }

        // Firebase kullanıcısını al
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser
        if (currentFirebaseUser == null) {
            println("DEBUG: Firebase kullanıcı null, problem kaydedilemez")
            Toast.makeText(this, "Giriş yapılmamış", Toast.LENGTH_SHORT).show()
            return
        }

        // Kaydet butonunu deaktif et
        binding.btnSaveProblem.isEnabled = false
        binding.btnSaveProblem.text = "Kaydediliyor..."

        try {
            // ✅ Problem objesi oluştur - GRUP ID'Sİ İLE
            val problem = Problem(
                groupId = currentGroupId, // ✅ Grup ID'si eklendi
                description = description,
                location = location,
                priority = selectedPriority,
                status = ProblemStatus.OPEN,
                auditorId = currentFirebaseUser.uid,
                auditorName = currentFirebaseUser.displayName ?: currentFirebaseUser.email ?: "Denetmen",
                imagePath = "" // Önce boş, fotoğraf kaydedildikten sonra güncellenecek
            )

            println("DEBUG: Problem objesi oluşturuldu - Grup ID: ${problem.groupId}, Denetmen: ${problem.auditorName}")

            // Fotoğrafı kaydet
            println("DEBUG: Fotoğraf kaydediliyor...")
            val savedImagePath = photoManager.saveProblemPhoto(selectedImageUri!!, problem.id)

            if (savedImagePath != null) {
                println("DEBUG: Fotoğraf başarıyla kaydedildi: $savedImagePath")

                // Problem'in fotoğraf yolunu güncelle
                val updatedProblem = problem.copy(imagePath = savedImagePath)

                // Veritabanına kaydet
                println("DEBUG: Problem veritabanına kaydediliyor - Grup ID: ${updatedProblem.groupId}")
                val success = databaseHelper.insertProblem(updatedProblem)

                if (success) {
                    println("DEBUG: ✅ Problem başarıyla kaydedildi - Grup: ${updatedProblem.groupId}, Denetmen: ${updatedProblem.auditorName}")
                    Toast.makeText(this,
                        "✅ Problem başarıyla kaydedildi!\n\n" +
                                "📋 Grup: $currentGroupId\n" +
                                "👤 Denetmen: ${updatedProblem.auditorName}",
                        Toast.LENGTH_LONG).show()

                    // Başarılı kayıt sonrası ana sayfaya dön
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    println("DEBUG: ❌ Problem veritabanına kaydedilemedi")
                    Toast.makeText(this, "Problem kaydedilemedi", Toast.LENGTH_SHORT).show()

                    // Hata durumunda fotoğrafı sil
                    photoManager.deletePhoto(savedImagePath)
                }
            } else {
                println("DEBUG: ❌ Fotoğraf kaydedilemedi")
                Toast.makeText(this, "Fotoğraf kaydedilemedi", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            println("DEBUG: ❌ Problem kaydetme hatası: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // Buton durumunu eski haline getir
            binding.btnSaveProblem.isEnabled = true
            binding.btnSaveProblem.text = "Kaydet"
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