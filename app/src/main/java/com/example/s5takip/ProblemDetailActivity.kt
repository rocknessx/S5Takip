package com.fabrika.s5takip

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabrika.s5takip.databinding.ActivityProblemDetailBinding

/**
 * Problem detay ekranı
 * Problemin tüm bilgilerini ve çözümlerini gösterir
 */
class ProblemDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProblemDetailBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var photoManager: PhotoManager
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUser: User? = null
    private var problem: Problem? = null
    private var solutions = mutableListOf<Solution>()

    companion object {
        const val EXTRA_PROBLEM_ID = "problem_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlangıç ayarları
        initializeComponents()
        loadCurrentUser()
        loadProblem()
        setupClickListeners()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Problem Detayı"
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

        // Problem'i veritabanından bul
        val problems = databaseHelper.getProblemsForDate(getCurrentDate())
        problem = problems.find { it.id == problemId }

        if (problem == null) {
            Toast.makeText(this, "Problem bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Problem bilgilerini ekranda göster
        displayProblemInfo()

        // Çözümleri yükle
        loadSolutions()
    }

    /**
     * Problem bilgilerini ekranda göster
     */
    private fun displayProblemInfo() {
        if (problem == null) return

        binding.tvDetailProblemId.text = "Problem #${problem!!.id.take(8)}"
        binding.tvDetailStatus.text = problem!!.status.toTurkish()
        binding.tvDetailDescription.text = problem!!.description
        binding.tvDetailLocation.text = problem!!.location
        binding.tvDetailAuditor.text = problem!!.auditorName
        binding.tvDetailPriority.text = problem!!.priority.toTurkish()

        // Tarih formatla
        binding.tvDetailDate.text = formatDate(problem!!.createdAt)

        // Durum rengini ayarla
        val statusColor = getStatusColor(problem!!.status)
        binding.tvDetailStatus.setBackgroundColor(statusColor)

        // Problem fotoğrafını yükle
        if (problem!!.imagePath.isNotEmpty()) {
            photoManager.loadPhoto(problem!!.imagePath, binding.ivDetailProblemPhoto)
        } else {
            binding.ivDetailProblemPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    /**
     * Çözümleri yükle - Güncellenmiş versiyon
     */
    private fun loadSolutions() {
        if (problem == null) return

        try {
            val loadedSolutions = databaseHelper.getSolutionsForProblem(problem!!.id)
            solutions.clear()
            solutions.addAll(loadedSolutions)

            // Debug için log ekle
            println("DEBUG: Problem ID: ${problem!!.id}")
            println("DEBUG: Bulunan çözüm sayısı: ${solutions.size}")

            // Çözüm sayısını güncelle
            binding.tvSolutionsCount.text = "${solutions.size} çözüm"

            if (solutions.isEmpty()) {
                binding.rvSolutions.visibility = View.GONE
                binding.llNoSolutions.visibility = View.VISIBLE
            } else {
                binding.llNoSolutions.visibility = View.GONE

                // Çözümleri göster
                showSolutionsInRecyclerView()
            }

        } catch (e: Exception) {
            println("DEBUG: Çözüm yükleme hatası: ${e.message}")
            Toast.makeText(this, "Çözümler yüklenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * Çözümleri RecyclerView'da göster
     */
    private fun showSolutionsInRecyclerView() {
        if (solutions.isEmpty()) return

        binding.rvSolutions.visibility = View.VISIBLE

        // Basit bir adapter oluştur
        val adapter = SolutionsAdapter(solutions, photoManager)
        binding.rvSolutions.layoutManager = LinearLayoutManager(this)
        binding.rvSolutions.adapter = adapter

        // Debug mesajı
        Toast.makeText(this, "✅ ${solutions.size} çözüm yüklendi", Toast.LENGTH_SHORT).show()
    }

    /**
     * Tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Çözüm ekleme butonu
        binding.btnDetailAddSolution.setOnClickListener {
            if (problem != null) {
                val intent = Intent(this, AddSolutionActivity::class.java)
                intent.putExtra(AddSolutionActivity.EXTRA_PROBLEM_ID, problem!!.id)
                startActivity(intent)
            }
        }

        // Durum değiştirme butonu
        binding.btnDetailEditStatus.setOnClickListener {
            if (currentUser?.role == UserRole.AUDITOR) {
                showStatusChangeDialog()
            } else {
                Toast.makeText(this, "Bu işlem için denetmen yetkisi gerekli", Toast.LENGTH_SHORT).show()
            }
        }

        // Hızlı durum değiştirme butonları (sadece denetmenler için)
        if (currentUser?.role == UserRole.AUDITOR) {
            binding.cvQuickStatus.visibility = View.VISIBLE

            binding.btnStatusProgress.setOnClickListener {
                updateProblemStatus(ProblemStatus.IN_PROGRESS)
            }

            binding.btnStatusResolved.setOnClickListener {
                updateProblemStatus(ProblemStatus.RESOLVED)
            }

            binding.btnStatusVerified.setOnClickListener {
                updateProblemStatus(ProblemStatus.VERIFIED)
            }
        } else {
            binding.cvQuickStatus.visibility = View.GONE
        }
    }

    /**
     * Durum değiştirme dialog'u göster
     */
    private fun showStatusChangeDialog() {
        if (problem == null) return

        val statusOptions = arrayOf(
            "🔴 Açık",
            "🟡 İşlemde",
            "🟢 Çözüldü",
            "🔵 Doğrulandı"
        )

        val statusValues = arrayOf(
            ProblemStatus.OPEN,
            ProblemStatus.IN_PROGRESS,
            ProblemStatus.RESOLVED,
            ProblemStatus.VERIFIED
        )

        // Mevcut durumun index'ini bul
        val currentIndex = statusValues.indexOf(problem!!.status)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Problem Durumunu Değiştir")
        builder.setMessage("Mevcut durum: ${problem!!.status.toTurkish()}\n\nYeni durum seçin:")

        builder.setSingleChoiceItems(statusOptions, currentIndex) { dialog, which ->
            val newStatus = statusValues[which]
            updateProblemStatus(newStatus)
            dialog.dismiss()
        }

        builder.setNegativeButton("İptal") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    /**
     * Problem durumunu güncelle
     */
    private fun updateProblemStatus(newStatus: ProblemStatus) {
        if (problem == null) return

        try {
            val success = databaseHelper.updateProblemStatus(problem!!.id, newStatus)

            if (success) {
                // Problem nesnesini güncelle
                problem = problem!!.copy(status = newStatus)

                // Ekranı yenile
                displayProblemInfo()

                // Başarı mesajı
                Toast.makeText(
                    this,
                    "Problem durumu '${newStatus.toTurkish()}' olarak güncellendi ✓",
                    Toast.LENGTH_SHORT
                ).show()

                // Debug log
                println("DEBUG: Problem durumu güncellendi: ${newStatus.toTurkish()}")

            } else {
                Toast.makeText(this, "Durum güncellenirken hata oluştu", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * Problem durumuna göre renk döndür
     */
    private fun getStatusColor(status: ProblemStatus): Int {
        return when (status) {
            ProblemStatus.OPEN -> ContextCompat.getColor(this, R.color.status_open)
            ProblemStatus.IN_PROGRESS -> ContextCompat.getColor(this, R.color.status_in_progress)
            ProblemStatus.RESOLVED -> ContextCompat.getColor(this, R.color.status_resolved)
            ProblemStatus.VERIFIED -> ContextCompat.getColor(this, R.color.status_verified)
        }
    }

    /**
     * Geri buton basıldığında
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * Aktivite yeniden görünür olduğunda
     */
    override fun onResume() {
        super.onResume()

        // Problem ve çözümleri yeniden yükle
        loadProblem()
    }
}

/**
 * Çözümler için basit adapter
 */
class SolutionsAdapter(
    private val solutions: List<Solution>,
    private val photoManager: PhotoManager
) : androidx.recyclerview.widget.RecyclerView.Adapter<SolutionsAdapter.SolutionViewHolder>() {

    class SolutionViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val cardView: androidx.cardview.widget.CardView = view as androidx.cardview.widget.CardView
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SolutionViewHolder {
        val context = parent.context
        val cardView = androidx.cardview.widget.CardView(context)
        val layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(
            androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
            androidx.recyclerview.widget.RecyclerView.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 0, 0, 16)
        cardView.layoutParams = layoutParams
        cardView.radius = 12f
        cardView.cardElevation = 4f

        return SolutionViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: SolutionViewHolder, position: Int) {
        val solution = solutions[position]
        val context = holder.itemView.context

        // İç layout oluştur
        val innerLayout = LinearLayout(context)
        innerLayout.orientation = LinearLayout.VERTICAL
        innerLayout.setPadding(16, 16, 16, 16)

        // Başlık
        val titleText = android.widget.TextView(context)
        titleText.text = "💡 Çözüm ${position + 1}"
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        titleText.setTextColor(ContextCompat.getColor(context, R.color.primary))

        // Kullanıcı ve tarih
        val userText = android.widget.TextView(context)
        userText.text = "${solution.userName} • ${formatDate(solution.createdAt)}"
        userText.textSize = 12f
        userText.setTextColor(ContextCompat.getColor(context, R.color.gray_dark))

        // Açıklama
        val descText = android.widget.TextView(context)
        descText.text = solution.description
        descText.textSize = 14f
        descText.setTextColor(android.graphics.Color.BLACK)

        // Layout'a ekle
        innerLayout.addView(titleText)

        // Boşluk
        val spacer1 = android.widget.Space(context)
        spacer1.layoutParams = LinearLayout.LayoutParams(0, 8)
        innerLayout.addView(spacer1)

        innerLayout.addView(userText)

        // Boşluk
        val spacer2 = android.widget.Space(context)
        spacer2.layoutParams = LinearLayout.LayoutParams(0, 12)
        innerLayout.addView(spacer2)

        innerLayout.addView(descText)

        // Fotoğraf varsa ekle
        if (solution.imagePath.isNotEmpty()) {
            val imageView = android.widget.ImageView(context)
            val imageParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
            imageParams.setMargins(0, 12, 0, 0)
            imageView.layoutParams = imageParams
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP

            photoManager.loadPhoto(solution.imagePath, imageView)
            innerLayout.addView(imageView)
        }

        // CardView'ı temizle ve yeni içeriği ekle
        holder.cardView.removeAllViews()
        holder.cardView.addView(innerLayout)
    }

    override fun getItemCount(): Int = solutions.size
}