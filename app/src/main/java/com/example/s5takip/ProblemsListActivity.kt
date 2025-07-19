package com.fabrika.s5takip

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fabrika.s5takip.databinding.ActivityProblemsListBinding
import java.text.SimpleDateFormat
import java.util.*


/**
 * Problem listesi ekranı - Gerçek problemleri gösterir
 */
class ProblemsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProblemsListBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var photoManager: PhotoManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var problemsAdapter: ProblemsAdapter

    private var currentUser: User? = null
    private var currentDate: String = getCurrentDate()
    private var problems = mutableListOf<Problem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlangıç ayarları
        initializeComponents()
        loadCurrentUser()
        setupRecyclerView()
        setupClickListeners()
        setupUI()
        loadProblems()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Problemler"
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
     * RecyclerView'ı ayarla
     */
    private fun setupRecyclerView() {
        problemsAdapter = ProblemsAdapter(
            problems = problems,
            photoManager = photoManager,
            databaseHelper = databaseHelper,  // DatabaseHelper eklendi
            onAddSolutionClick = { problem ->
                addSolutionToProblem(problem)
            },
            onViewDetailsClick = { problem ->
                viewProblemDetails(problem)
            }
        )

        binding.rvProblems.layoutManager = LinearLayoutManager(this)
        binding.rvProblems.adapter = problemsAdapter
    }

    /**
     * Tıklama olaylarını ayarla - Tarih değiştirme eklenmiş
     */
    private fun setupClickListeners() {
        // Tarih değiştirme butonu - ÇALİŞAN VERSİYON
        binding.btnChangeDate.setOnClickListener {
            showDatePicker()
        }
    }

    /**
     * Tarih seçici dialog'u göster
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            // Mevcut seçili tarihi parse et
            calendar.time = dateFormat.parse(currentDate) ?: Date()
        } catch (e: Exception) {
            // Hata durumunda bugünü kullan
            calendar.time = Date()
        }

        val datePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Seçilen tarihi kaydet
                calendar.set(year, month, dayOfMonth)
                currentDate = dateFormat.format(calendar.time)

                // UI'ı güncelle
                updateDateDisplay()

                // O tarihin problemlerini yükle
                loadProblems()

                // Kullanıcıya bilgi ver
                val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
                val formattedDate = displayFormat.format(calendar.time)
                Toast.makeText(this, "Tarih değiştirildi: $formattedDate", Toast.LENGTH_SHORT).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Başlık ayarla
        datePicker.setTitle("Problem Tarihini Seçin")
        datePicker.show()
    }

    /**
     * Tarih görünümünü güncelle
     */
    private fun updateDateDisplay() {
        val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            val date = inputFormat.parse(currentDate) ?: Date()
            val formattedDate = displayFormat.format(date)

            // Bugün mü kontrol et
            val today = inputFormat.format(Date())
            val displayText = if (currentDate == today) {
                "Bugün ($formattedDate)"
            } else {
                formattedDate
            }

            binding.tvSelectedDate.text = displayText

        } catch (e: Exception) {
            binding.tvSelectedDate.text = "Tarih seçilmedi"
        }
    }

    /**
     * UI'ı ayarla
     */
    private fun setupUI() {
        // Seçili tarihi göster
        val dateFormat = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("tr", "TR"))
        val formattedDate = try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = inputFormat.parse(currentDate)
            if (date != null) dateFormat.format(date) else "Bugün"
        } catch (e: Exception) {
            "Bugün"
        }

        binding.tvSelectedDate.text = formattedDate
        updateDateDisplay()
    }

    /**
     * Problemleri yükle - Güncellenmiş versiyon
     */
    private fun loadProblems() {
        try {
            // Seçili tarihin problemlerini getir
            val loadedProblems = databaseHelper.getProblemsForDate(currentDate)

            problems.clear()
            problems.addAll(loadedProblems)

            // RecyclerView'ı güncelle
            problemsAdapter.updateProblems(problems)

            // Boş durum kontrolü
            if (problems.isEmpty()) {
                binding.rvProblems.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE

                // Tarihe özel mesaj
                val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = inputFormat.parse(currentDate)
                val formattedDate = if (date != null) displayFormat.format(date) else "bu tarihte"

                Toast.makeText(this, "$formattedDate problem bulunmuyor", Toast.LENGTH_SHORT).show()
            } else {
                binding.rvProblems.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE

                Toast.makeText(this, "${problems.size} problem bulundu", Toast.LENGTH_SHORT).show()
            }

            // İstatistikleri güncelle
            updateStatistics()

        } catch (e: Exception) {
            Toast.makeText(this, "Problemler yüklenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * İstatistikleri güncelle - Seçili tarihe göre
     */
    private fun updateStatistics() {
        val stats = databaseHelper.getStatsForDate(currentDate)

        binding.tvStatTotal.text = stats.totalProblems.toString()
        binding.tvStatOpen.text = stats.openProblems.toString()
        binding.tvStatProgress.text = stats.inProgressProblems.toString()
        binding.tvStatResolved.text = (stats.resolvedProblems + stats.verifiedProblems).toString()

        // Debug log
        println("DEBUG: $currentDate için istatistikler güncellendi")
        println("DEBUG: Toplam: ${stats.totalProblems}, Açık: ${stats.openProblems}")
    }

    /**
     * Problema çözüm ekle
     */
    private fun addSolutionToProblem(problem: Problem) {
        val intent = Intent(this, AddSolutionActivity::class.java)
        intent.putExtra(AddSolutionActivity.EXTRA_PROBLEM_ID, problem.id)
        startActivity(intent)
    }

    /**
     * Problem detaylarını görüntüle
     */
    private fun viewProblemDetails(problem: Problem) {
        val intent = Intent(this, ProblemDetailActivity::class.java)
        intent.putExtra(ProblemDetailActivity.EXTRA_PROBLEM_ID, problem.id)
        startActivity(intent)
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

        // Problemleri yeniden yükle (yeni problemler veya çözümler eklenmiş olabilir)
        loadProblems()

        // Adapter'ı da bilgilendir
        problemsAdapter.notifyDataSetChanged()
    }
}