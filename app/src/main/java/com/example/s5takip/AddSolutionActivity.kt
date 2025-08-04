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
import com.fabrika.s5takip.databinding.ActivityAddSolutionBinding
import com.github.dhaval2404.imagepicker.ImagePicker

/**
 * Çözüm ekleme ekranı
 * Kullanıcılar problemlere çözüm önerileri ekleyebilir
 */
class AddSolutionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSolutionBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var photoManager: PhotoManager
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUser: User? = null
    private var problem: Problem? = null
    private var selectedImageUri: Uri? = null
    private var isImplemented = false // Çözüm uygulandı mı?

    companion object {
        const val EXTRA_PROBLEM_ID = "problem_id"
    }

    // Fotoğraf seçme için launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            if (selectedImageUri != null) {
                // Fotoğrafı göster
                binding.ivSolutionPhoto.setImageURI(selectedImageUri)
                binding.ivSolutionPhoto.alpha = 1.0f
                binding.tvSolutionPhotoInstruction.text = "✓ Çözüm fotoğrafı seçildi"

                // Kaydet butonunu kontrol et
                checkFormValidity()
            }
        } else if (result.resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, "Fotoğraf seçilirken hata: ${ImagePicker.getError(result.data)}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSolutionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlangıç ayarları
        initializeComponents()
        loadCurrentUser()
        loadProblem()
        setupClickListeners()
        setupTextWatchers()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Çözüm Ekle"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Bileşenleri başlat
     */
    private fun initializeComponents() {
        databaseHelper = DatabaseHelper(this)
        photoManager = PhotoManager(this)
        sharedPreferences = getSharedPreferences("s5_takip_prefs", MODE_PRIVATE)
    }

    /**
     * Mevcut kullanıcıyı yükle
     */
    private fun loadCurrentUser() {
        val userId = sharedPreferences.getString("current_user_id", null)
        if (userId != null) {
            currentUser = databaseHelper.getUserById(userId)
        }

        if (currentUser == null) {
            Toast.makeText(this, "Kullanıcı bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Debug: Kullanıcı bilgisi
        println("DEBUG: Mevcut kullanıcı: ${currentUser?.name} (${currentUser?.role})")
    }

    /**
     * Problem bilgilerini yükle
     */
    private fun loadProblem() {
        val problemId = intent.getStringExtra(EXTRA_PROBLEM_ID)
        if (problemId == null) {
            Toast.makeText(this, "Problem bilgisi bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Debug: Problem ID
        println("DEBUG: Yüklenen Problem ID: $problemId")

        // Problem'i veritabanından bul
        val problems = databaseHelper.getProblemsForDate(getCurrentDate())
        problem = problems.find { it.id == problemId }

        if (problem == null) {
            Toast.makeText(this, "Problem bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Debug: Problem bilgisi
        println("DEBUG: Problem bulundu: ${problem?.description?.take(50)}...")

        // Problem bilgilerini ekranda göster
        binding.tvProblemDescription.text = problem!!.description
        binding.tvProblemLocation.text = "📍 ${problem!!.location}"
    }

    /**
     * Tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Fotoğraf çekme/seçme
        binding.llSolutionPhotoContainer.setOnClickListener {
            openImagePicker()
        }

        // Çözüm durumu seçimi
        binding.rgSolutionStatus.setOnCheckedChangeListener { _, checkedId ->
            isImplemented = checkedId == R.id.rb_implemented

            // Debug: Durum seçimi
            println("DEBUG: Çözüm durumu değişti: ${if (isImplemented) "Uygulandı" else "Öneri"}")
        }

        // İptal butonu
        binding.btnCancelSolution.setOnClickListener {
            finish()
        }

        // Kaydet butonu
        binding.btnSaveSolution.setOnClickListener {
            saveSolution()
        }
    }

    /**
     * Metin değişikliklerini izle
     */
    private fun setupTextWatchers() {
        binding.etSolutionDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkFormValidity()
            }
        })
    }

    /**
     * Fotoğraf seçici/çekici aç
     */
    private fun openImagePicker() {
        ImagePicker.with(this)
            .crop()
            .compress(1024)
            .maxResultSize(1080, 1080)
            .createIntent { intent ->
                imagePickerLauncher.launch(intent)
            }
    }

    /**
     * Form geçerliliğini kontrol et
     */
    private fun checkFormValidity() {
        val description = binding.etSolutionDescription.text.toString().trim()

        // En az açıklama olması yeterli (fotoğraf opsiyonel)
        val isFormValid = description.isNotEmpty() && description.length >= 10

        binding.btnSaveSolution.isEnabled = isFormValid

        // Debug: Form durumu
        if (description.isNotEmpty()) {
            println("DEBUG: Açıklama uzunluğu: ${description.length}, Geçerli: $isFormValid")
        }
    }

    /**
     * Çözümü kaydet - DÜZELTILMIŞ: Firebase kullanıcısından isim al
     */
    private fun saveSolution() {
        val description = binding.etSolutionDescription.text.toString().trim()

        // Form kontrolü
        if (description.isEmpty() || description.length < 10) {
            Toast.makeText(this, "Lütfen en az 10 karakter çözüm açıklaması yazın", Toast.LENGTH_SHORT).show()
            return
        }

        if (problem == null) {
            Toast.makeText(this, "Problem bilgisi eksik", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Firebase kullanıcısını al - gerçek kullanıcı adı için
        val currentFirebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentFirebaseUser == null) {
            Toast.makeText(this, "Giriş yapılmamış", Toast.LENGTH_SHORT).show()
            return
        }

        // Debug: Kaydetme başlangıcı
        println("DEBUG: Çözüm kaydetme başladı...")
        println("DEBUG: Açıklama: ${description.take(50)}...")
        println("DEBUG: Fotoğraf var mı: ${selectedImageUri != null}")
        println("DEBUG: Uygulama durumu: $isImplemented")
        println("DEBUG: ✅ Çözümü yazan: ${currentFirebaseUser.displayName}")

        // Kaydet butonunu deaktif et
        binding.btnSaveSolution.isEnabled = false
        binding.btnSaveSolution.text = "Kaydediliyor..."

        try {
            // ✅ Çözüm objesi oluştur - GERÇEKLEŞTİREN KİŞİNİN BİLGİLERİYLE
            val solution = Solution(
                problemId = problem!!.id,
                userId = currentFirebaseUser.uid,
                userName = currentFirebaseUser.displayName ?: currentFirebaseUser.email ?: "Kullanıcı",
                description = description,
                imagePath = "", // Önce boş, fotoğraf varsa güncellenecek
                isVerified = false
            )

            // Debug: Çözüm objesi
            println("DEBUG: ✅ Çözüm objesi oluşturuldu:")
            println("DEBUG: - ID: ${solution.id}")
            println("DEBUG: - Kullanıcı ID: ${solution.userId}")
            println("DEBUG: - Kullanıcı Adı: ${solution.userName}")
            println("DEBUG: - Problem ID: ${solution.problemId}")

            var savedImagePath: String? = null

            // Fotoğraf varsa kaydet
            if (selectedImageUri != null) {
                println("DEBUG: Fotoğraf kaydediliyor...")
                savedImagePath = photoManager.saveSolutionPhoto(selectedImageUri!!, solution.id)
                println("DEBUG: Fotoğraf kaydedildi: $savedImagePath")
            }

            // Çözümün fotoğraf yolunu güncelle
            val updatedSolution = solution.copy(
                imagePath = savedImagePath ?: ""
            )

            // Debug: Final çözüm objesi
            println("DEBUG: ✅ Final çözüm objesi:")
            println("DEBUG: - Problem ID: ${updatedSolution.problemId}")
            println("DEBUG: - Kullanıcı: ${updatedSolution.userName}")
            println("DEBUG: - Fotoğraf: ${updatedSolution.imagePath}")

            // Veritabanına kaydet
            println("DEBUG: Veritabanına kaydediliyor...")
            val success = databaseHelper.insertSolution(updatedSolution)

            if (success) {
                // Debug: Başarılı kayıt
                println("DEBUG: ✅ Çözüm başarıyla kaydedildi!")
                println("DEBUG: ✅ Çözümü yazan: ${updatedSolution.userName}")

                // Eğer çözüm uygulandıysa, problem durumunu güncelle
                if (isImplemented) {
                    println("DEBUG: Problem durumu güncelleniyor...")
                    databaseHelper.updateProblemStatus(problem!!.id, ProblemStatus.RESOLVED)
                    println("DEBUG: Problem durumu RESOLVED olarak güncellendi")

                    Toast.makeText(this,
                        "✅ Çözüm kaydedildi ve problem çözüldü olarak işaretlendi!\n\n" +
                                "Çözümü yazan: ${updatedSolution.userName}",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this,
                        "✅ Çözüm önerisi başarıyla kaydedildi!\n\n" +
                                "Öneren: ${updatedSolution.userName}",
                        Toast.LENGTH_LONG).show()
                }

                // Debug: Kayıt sonrası kontrol
                val checkSolutions = databaseHelper.getSolutionsForProblem(problem!!.id)
                println("DEBUG: Kayıt sonrası bu problem için toplam çözüm sayısı: ${checkSolutions.size}")
                checkSolutions.forEach { sol ->
                    println("DEBUG: - ${sol.userName}: ${sol.description.take(30)}...")
                }

                // Başarılı kayıt sonrası geri dön
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                println("DEBUG: ❌ Veritabanına kayıt başarısız!")
                Toast.makeText(this, "❌ Çözüm kaydedilemedi", Toast.LENGTH_SHORT).show()

                // Hata durumunda fotoğrafı sil
                if (savedImagePath != null) {
                    photoManager.deletePhoto(savedImagePath)
                }
            }

        } catch (e: Exception) {
            println("DEBUG: ❌ Kaydetme hatası: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "💥 Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // Buton durumunu eski haline getir
            binding.btnSaveSolution.isEnabled = true
            binding.btnSaveSolution.text = "Çözümü Kaydet"
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