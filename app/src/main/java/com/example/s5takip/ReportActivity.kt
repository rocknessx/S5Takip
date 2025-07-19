package com.fabrika.s5takip

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.fabrika.s5takip.databinding.ActivityReportBinding
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Rapor oluşturma ekranı
 * Denetmenler günlük/haftalık/aylık raporları PDF olarak oluşturup paylaşabilir
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences

    private var currentUser: User? = null
    private var selectedDate: String = getCurrentDate()
    private var reportType: ReportType = ReportType.DAILY
    private var reportData: ReportData? = null

    enum class ReportType {
        DAILY, WEEKLY, MONTHLY
    }

    data class ReportData(
        val title: String,
        val period: String,
        val problems: List<Problem>,
        val stats: DailyStats,
        val generatedBy: String,
        val generatedAt: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlangıç ayarları
        initializeComponents()
        loadCurrentUser()
        setupClickListeners()
        updateDateDisplay()
        generatePreview()

        // Başlık çubuğunu ayarla
        supportActionBar?.title = "Rapor Oluştur"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Bileşenleri başlat
     */
    private fun initializeComponents() {
        databaseHelper = DatabaseHelper(this)
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

        // Eğer kullanıcı yoksa veya denetmen değilse, geri dön
        if (currentUser == null || currentUser?.role != UserRole.AUDITOR) {
            Toast.makeText(this, "Bu işlem için denetmen yetkisi gerekli", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    /**
     * Tıklama olaylarını ayarla
     */
    private fun setupClickListeners() {
        // Rapor türü seçimi
        binding.rgReportType.setOnCheckedChangeListener { _, checkedId ->
            reportType = when (checkedId) {
                R.id.rb_weekly -> ReportType.WEEKLY
                R.id.rb_monthly -> ReportType.MONTHLY
                else -> ReportType.DAILY
            }
            updateDateDisplay()
            generatePreview()
        }

        // Tarih seçimi
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        // Rapor önizleme
        binding.btnPreviewReport.setOnClickListener {
            generatePreview()
        }

        // PDF oluşturma - Gelişmiş fotoğraflı versiyon
        binding.btnGeneratePdf.setOnClickListener {
            generateAdvancedPdfReport()
        }

        // Rapor paylaşma
        binding.btnShareReport.setOnClickListener {
            shareReport()
        }
    }

    /**
     * Tarih seçici göster
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            calendar.time = dateFormat.parse(selectedDate) ?: Date()
        } catch (e: Exception) {
            calendar.time = Date()
        }

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDate = dateFormat.format(calendar.time)
                updateDateDisplay()
                generatePreview()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePicker.show()
    }

    /**
     * Tarih görünümünü güncelle
     */
    private fun updateDateDisplay() {
        val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR"))
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            val date = inputFormat.parse(selectedDate) ?: Date()
            val formattedDate = displayFormat.format(date)

            val displayText = when (reportType) {
                ReportType.DAILY -> "📅 $formattedDate"
                ReportType.WEEKLY -> "📆 ${formattedDate} haftası"
                ReportType.MONTHLY -> "🗓️ ${SimpleDateFormat("MMMM yyyy", Locale("tr", "TR")).format(date)}"
            }

            binding.tvSelectedDate.text = displayText
        } catch (e: Exception) {
            binding.tvSelectedDate.text = "Bugün"
        }
    }

    /**
     * Rapor önizlemesi oluştur
     */
    private fun generatePreview() {
        try {
            // Rapor verilerini hazırla
            val problems = getProblemsForPeriod()
            val stats = calculateStats(problems)

            reportData = ReportData(
                title = getReportTitle(),
                period = binding.tvSelectedDate.text.toString(),
                problems = problems,
                stats = stats,
                generatedBy = currentUser?.name ?: "Bilinmeyen",
                generatedAt = getCurrentDate()
            )

            // Önizleme verilerini güncelle
            binding.tvPreviewTotal.text = stats.totalProblems.toString()
            binding.tvPreviewResolved.text = (stats.resolvedProblems + stats.verifiedProblems).toString()
            binding.tvPreviewRate.text = "${stats.resolutionRate.toInt()}%"

            // Problem listesi önizlemesi
            val problemsText = if (problems.isEmpty()) {
                "Bu dönemde problem bulunmuyor"
            } else {
                problems.take(3).joinToString("\n") {
                    "• ${it.description.take(50)}${if (it.description.length > 50) "..." else ""}"
                } + if (problems.size > 3) "\n... ve ${problems.size - 3} problem daha" else ""
            }

            binding.tvPreviewProblems.text = problemsText

            // Butonları aktif et
            val hasData = problems.isNotEmpty()
            binding.btnGeneratePdf.isEnabled = hasData
            binding.btnShareReport.isEnabled = hasData

            if (!hasData) {
                Toast.makeText(this, "Seçilen dönemde problem bulunmuyor", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Önizleme oluşturulurken hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Belirtilen döneme ait problemleri getir
     */
    private fun getProblemsForPeriod(): List<Problem> {
        return when (reportType) {
            ReportType.DAILY -> {
                databaseHelper.getProblemsForDate(selectedDate)
            }
            ReportType.WEEKLY -> {
                // Haftalık: Seçilen tarihten 7 gün geriye
                val problems = mutableListOf<Problem>()
                val calendar = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                try {
                    calendar.time = dateFormat.parse(selectedDate) ?: Date()

                    for (i in 0 until 7) {
                        val dateStr = dateFormat.format(calendar.time)
                        problems.addAll(databaseHelper.getProblemsForDate(dateStr))
                        calendar.add(Calendar.DAY_OF_MONTH, -1)
                    }
                } catch (e: Exception) {
                    // Hata durumunda sadece bugünkü problemler
                    problems.addAll(databaseHelper.getProblemsForDate(selectedDate))
                }

                problems
            }
            ReportType.MONTHLY -> {
                // Aylık: Seçilen ayın tüm günleri (basitleştirilmiş)
                val problems = mutableListOf<Problem>()
                val calendar = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                try {
                    calendar.time = dateFormat.parse(selectedDate) ?: Date()
                    calendar.set(Calendar.DAY_OF_MONTH, 1) // Ayın başı

                    val month = calendar.get(Calendar.MONTH)
                    while (calendar.get(Calendar.MONTH) == month) {
                        val dateStr = dateFormat.format(calendar.time)
                        problems.addAll(databaseHelper.getProblemsForDate(dateStr))
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }
                } catch (e: Exception) {
                    problems.addAll(databaseHelper.getProblemsForDate(selectedDate))
                }

                problems
            }
        }
    }

    /**
     * İstatistikleri hesapla
     */
    private fun calculateStats(problems: List<Problem>): DailyStats {
        val totalProblems = problems.size
        val openProblems = problems.count { it.status == ProblemStatus.OPEN }
        val inProgressProblems = problems.count { it.status == ProblemStatus.IN_PROGRESS }
        val resolvedProblems = problems.count { it.status == ProblemStatus.RESOLVED }
        val verifiedProblems = problems.count { it.status == ProblemStatus.VERIFIED }

        return DailyStats(
            date = selectedDate,
            totalProblems = totalProblems,
            openProblems = openProblems,
            inProgressProblems = inProgressProblems,
            resolvedProblems = resolvedProblems,
            verifiedProblems = verifiedProblems
        )
    }

    /**
     * Rapor başlığını oluştur
     */
    private fun getReportTitle(): String {
        return when (reportType) {
            ReportType.DAILY -> "Günlük 5S Takip Raporu"
            ReportType.WEEKLY -> "Haftalık 5S Takip Raporu"
            ReportType.MONTHLY -> "Aylık 5S Takip Raporu"
        }
    }

    /**
     * Gelişmiş PDF rapor oluştur - KOMPAKT VERSİYON
     */
    private fun generateAdvancedPdfReport() {
        if (reportData == null) {
            Toast.makeText(this, "Önce rapor önizlemesi oluşturun", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "5S_Rapor_${selectedDate}_${System.currentTimeMillis()}.pdf"
            val file = File(getExternalFilesDir(null), fileName)

            val document = Document(PageSize.A4, 30f, 30f, 40f, 30f) // Daha küçük margin
            PdfWriter.getInstance(document, FileOutputStream(file))

            document.open()

            // Başlık sayfası
            addReportHeader(document)

            // İstatistikler sayfası
            addStatisticsSection(document)

            // Problem detayları (kompakt fotoğraflarla)
            addProblemsWithPhotos(document)

            document.close()

            Toast.makeText(this, "Kompakt PDF rapor oluşturuldu!", Toast.LENGTH_LONG).show()
            binding.btnShareReport.isEnabled = true

        } catch (e: Exception) {
            Toast.makeText(this, "PDF oluşturma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * Rapor başlığı ekle
     */
    private fun addReportHeader(document: Document) {
        try {
            // Ana başlık
            val titleFont = Font(Font.FontFamily.HELVETICA, 20f, Font.BOLD, BaseColor.BLUE)
            val title = Paragraph(reportData!!.title, titleFont)
            title.alignment = Element.ALIGN_CENTER
            document.add(title)

            document.add(Paragraph(" "))

            // Alt bilgiler
            val infoTable = PdfPTable(2)
            infoTable.widthPercentage = 100f

            infoTable.addCell(createInfoCell("Rapor Dönemi:", reportData!!.period))
            infoTable.addCell(createInfoCell("Oluşturan:", reportData!!.generatedBy))
            infoTable.addCell(createInfoCell("Oluşturma Tarihi:", formatDate(System.currentTimeMillis())))
            infoTable.addCell(createInfoCell("Departman:", currentUser?.department ?: ""))

            document.add(infoTable)
            document.add(Paragraph(" "))

        } catch (e: Exception) {
            // Hata durumunda basit başlık
            document.add(Paragraph(reportData!!.title))
            document.add(Paragraph(" "))
        }
    }

    /**
     * İstatistikler bölümü ekle
     */
    private fun addStatisticsSection(document: Document) {
        try {
            // İstatistik başlığı
            val statsTitle = Paragraph("GENEL İSTATİSTİKLER", Font(Font.FontFamily.HELVETICA, 16f, Font.BOLD))
            statsTitle.alignment = Element.ALIGN_CENTER
            document.add(statsTitle)
            document.add(Paragraph(" "))

            // İstatistik tablosu
            val statsTable = PdfPTable(4)
            statsTable.widthPercentage = 100f

            // Başlık satırı
            statsTable.addCell(createHeaderCell("Toplam"))
            statsTable.addCell(createHeaderCell("Açık"))
            statsTable.addCell(createHeaderCell("Çözülen"))
            statsTable.addCell(createHeaderCell("Başarı"))

            // Veri satırı
            statsTable.addCell(createDataCell("${reportData!!.stats.totalProblems}"))
            statsTable.addCell(createDataCell("${reportData!!.stats.openProblems}"))
            statsTable.addCell(createDataCell("${reportData!!.stats.resolvedProblems + reportData!!.stats.verifiedProblems}"))
            statsTable.addCell(createDataCell("%${reportData!!.stats.resolutionRate.toInt()}"))

            document.add(statsTable)
            document.add(Paragraph(" "))
            document.add(Paragraph(" "))

        } catch (e: Exception) {
            // Basit istatistik
            document.add(Paragraph("Toplam Problem: ${reportData!!.stats.totalProblems}"))
            document.add(Paragraph("Çözülen: ${reportData!!.stats.resolvedProblems + reportData!!.stats.verifiedProblems}"))
            document.add(Paragraph(" "))
        }
    }

    /**
     * Problemleri fotoğraflarla ekle - KOMPAKT VERSİYON
     */
    private fun addProblemsWithPhotos(document: Document) {
        if (reportData!!.problems.isEmpty()) {
            document.add(Paragraph("Bu dönemde problem bulunmuyor."))
            return
        }

        try {
            val problemsTitle = Paragraph("PROBLEM DETAYLARI", Font(Font.FontFamily.HELVETICA, 16f, Font.BOLD))
            problemsTitle.alignment = Element.ALIGN_CENTER
            document.add(problemsTitle)
            document.add(Paragraph(" "))

            reportData!!.problems.forEachIndexed { index, problem ->
                // Sadece 1. problem için yeni sayfa değil, diğerleri için kontrollü boşluk
                if (index > 0) {
                    // Sayfa sonuna yakınsa yeni sayfa, değilse sadece boşluk
                    document.add(Paragraph(" "))
                    document.add(Paragraph("═══════════════════════════════════════"))
                    document.add(Paragraph(" "))
                }

                addCompactProblemWithPhotos(document, problem, index + 1)
            }

        } catch (e: Exception) {
            // Basit problem listesi
            reportData!!.problems.forEachIndexed { index, problem ->
                document.add(Paragraph("${index + 1}. ${problem.description}"))
                document.add(Paragraph("Konum: ${problem.location}"))
                document.add(Paragraph(" "))
            }
        }
    }
    /**
     * Tek bir problemi KOMPAKT şekilde ekle
     */
    private fun addCompactProblemWithPhotos(document: Document, problem: Problem, problemNumber: Int) {
        try {
            // Problem başlığı - daha küçük
            val problemTitle = Paragraph("Problem #$problemNumber", Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD))
            document.add(problemTitle)

            // Problem bilgileri - tek satırda
            val infoText = "Açıklama: ${problem.description} | Konum: ${problem.location} | Durum: ${problem.status.toTurkish()}"
            document.add(Paragraph(infoText, Font(Font.FontFamily.HELVETICA, 9f)))
            document.add(Paragraph(" "))

            // FOTOĞRAFLAR - Daha küçük boyutlarda
            addCompactPhotoSection(document, problem)

            // ÇÖZÜMLER - Daha kısa
            addCompactSolutionsSection(document, problem)

        } catch (e: Exception) {
            // Basit problem bilgisi
            document.add(Paragraph("Problem $problemNumber: ${problem.description}"))
        }
    }

    /**
     * Tek bir problemi fotoğraflarıyla ekle
     */
    private fun addSingleProblemWithPhotos(document: Document, problem: Problem, problemNumber: Int) {
        try {
            // Problem başlığı
            val problemTitle = Paragraph("Problem #$problemNumber", Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD))
            document.add(problemTitle)
            document.add(Paragraph(" "))

            // Problem bilgileri
            document.add(Paragraph("Açıklama: ${problem.description}"))
            document.add(Paragraph("Konum: ${problem.location}"))
            document.add(Paragraph("Durum: ${problem.status.toTurkish()}"))
            document.add(Paragraph("Denetmen: ${problem.auditorName}"))
            document.add(Paragraph(" "))

            // FOTOĞRAFLAR - Sol: Problem, Sağ: Çözüm
            addPhotoSection(document, problem)

            // ÇÖZÜM ÖNERİLERİ
            addSolutionsSection(document, problem)

            document.add(Paragraph(" "))
            document.add(Paragraph("─────────────────────────────────────────────────")) // Ayırıcı çizgi
            document.add(Paragraph(" "))

        } catch (e: Exception) {
            // Basit problem bilgisi
            document.add(Paragraph("Problem $problemNumber: ${problem.description}"))
            document.add(Paragraph(" "))
        }
    }

    /**
     * Fotoğraf bölümü - Sol: Problem, Sağ: Çözüm
     */
    private fun addPhotoSection(document: Document, problem: Problem) {
        try {
            val photoTitle = Paragraph("FOTOĞRAFLAR", Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD))
            document.add(photoTitle)
            document.add(Paragraph(" "))

            // 2 sütunlu tablo (sol: problem, sağ: çözüm)
            val photoTable = PdfPTable(2)
            photoTable.widthPercentage = 100f
            photoTable.setWidths(floatArrayOf(1f, 1f)) // Eşit genişlik

            // Sol: Problem fotoğrafı
            val problemCell = PdfPCell()
            problemCell.addElement(Paragraph("Problem Fotoğrafı:", Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD)))

            if (problem.imagePath.isNotEmpty() && File(problem.imagePath).exists()) {
                try {
                    val problemImage = Image.getInstance(problem.imagePath)
                    problemImage.scaleToFit(200f, 150f)
                    problemCell.addElement(problemImage)
                } catch (e: Exception) {
                    problemCell.addElement(Paragraph("Fotoğraf yüklenemedi"))
                }
            } else {
                problemCell.addElement(Paragraph("Fotoğraf yok"))
            }

            // Sağ: Çözüm fotoğrafı
            val solutionCell = PdfPCell()
            solutionCell.addElement(Paragraph("Çözüm Fotoğrafı:", Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD)))

            // En son çözümün fotoğrafını al
            val solutions = databaseHelper.getSolutionsForProblem(problem.id)
            val lastSolutionWithPhoto = solutions.lastOrNull { it.imagePath.isNotEmpty() }

            if (lastSolutionWithPhoto != null && File(lastSolutionWithPhoto.imagePath).exists()) {
                try {
                    val solutionImage = Image.getInstance(lastSolutionWithPhoto.imagePath)
                    solutionImage.scaleToFit(200f, 150f)
                    solutionCell.addElement(solutionImage)
                } catch (e: Exception) {
                    solutionCell.addElement(Paragraph("Fotoğraf yüklenemedi"))
                }
            } else {
                solutionCell.addElement(Paragraph("Çözüm fotoğrafı yok"))
            }

            photoTable.addCell(problemCell)
            photoTable.addCell(solutionCell)

            document.add(photoTable)
            document.add(Paragraph(" "))

        } catch (e: Exception) {
            document.add(Paragraph("Fotoğraflar yüklenemedi"))
            document.add(Paragraph(" "))
        }
    }
    /**
     * Kompakt fotoğraf bölümü - Daha küçük boyutlar
     */
    private fun addCompactPhotoSection(document: Document, problem: Problem) {
        try {
            // 2 sütunlu tablo (sol: problem, sağ: çözüm) - daha küçük
            val photoTable = PdfPTable(2)
            photoTable.widthPercentage = 90f
            photoTable.setWidths(floatArrayOf(1f, 1f))

            // Sol: Problem fotoğrafı - KÜÇÜK
            val problemCell = PdfPCell()
            problemCell.addElement(Paragraph("Problem:", Font(Font.FontFamily.HELVETICA, 8f, Font.BOLD)))

            if (problem.imagePath.isNotEmpty() && File(problem.imagePath).exists()) {
                try {
                    val problemImage = Image.getInstance(problem.imagePath)
                    problemImage.scaleToFit(120f, 90f) // Daha küçük boyut
                    problemCell.addElement(problemImage)
                } catch (e: Exception) {
                    problemCell.addElement(Paragraph("Fotoğraf yok", Font(Font.FontFamily.HELVETICA, 8f)))
                }
            } else {
                problemCell.addElement(Paragraph("Fotoğraf yok", Font(Font.FontFamily.HELVETICA, 8f)))
            }
            problemCell.minimumHeight = 100f

            // Sağ: Çözüm fotoğrafı - KÜÇÜK
            val solutionCell = PdfPCell()
            solutionCell.addElement(Paragraph("Çözüm:", Font(Font.FontFamily.HELVETICA, 8f, Font.BOLD)))

            // En son çözümün fotoğrafını al
            val solutions = databaseHelper.getSolutionsForProblem(problem.id)
            val lastSolutionWithPhoto = solutions.lastOrNull { it.imagePath.isNotEmpty() }

            if (lastSolutionWithPhoto != null && File(lastSolutionWithPhoto.imagePath).exists()) {
                try {
                    val solutionImage = Image.getInstance(lastSolutionWithPhoto.imagePath)
                    solutionImage.scaleToFit(120f, 90f) // Daha küçük boyut
                    solutionCell.addElement(solutionImage)
                } catch (e: Exception) {
                    solutionCell.addElement(Paragraph("Fotoğraf yok", Font(Font.FontFamily.HELVETICA, 8f)))
                }
            } else {
                solutionCell.addElement(Paragraph("Çözüm fotoğrafı yok", Font(Font.FontFamily.HELVETICA, 8f)))
            }
            solutionCell.minimumHeight = 100f

            photoTable.addCell(problemCell)
            photoTable.addCell(solutionCell)

            document.add(photoTable)

        } catch (e: Exception) {
            document.add(Paragraph("Fotoğraflar yüklenemedi", Font(Font.FontFamily.HELVETICA, 8f)))
        }
    }

    /**
     * Kompakt çözüm önerileri bölümü
     */
    private fun addCompactSolutionsSection(document: Document, problem: Problem) {
        try {
            val solutions = databaseHelper.getSolutionsForProblem(problem.id)

            if (solutions.isNotEmpty()) {
                val solutionsTitle = Paragraph("Çözümler (${solutions.size}):",
                    Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD))
                document.add(solutionsTitle)

                // En fazla 2 çözüm göster, daha kısa
                solutions.take(2).forEachIndexed { index, solution ->
                    val shortDesc = if (solution.description.length > 80) {
                        solution.description.take(80) + "..."
                    } else {
                        solution.description
                    }

                    document.add(Paragraph("${index + 1}. $shortDesc (${solution.userName})",
                        Font(Font.FontFamily.HELVETICA, 8f)))
                }

                if (solutions.size > 2) {
                    document.add(Paragraph("... ve ${solutions.size - 2} çözüm daha",
                        Font(Font.FontFamily.HELVETICA, 8f, Font.ITALIC)))
                }
            } else {
                document.add(Paragraph("Çözüm önerisi yok.", Font(Font.FontFamily.HELVETICA, 8f)))
            }

        } catch (e: Exception) {
            document.add(Paragraph("Çözümler yüklenemedi", Font(Font.FontFamily.HELVETICA, 8f)))
        }
    }

    /**
     * Çözüm önerileri bölümü
     */
    private fun addSolutionsSection(document: Document, problem: Problem) {
        try {
            val solutions = databaseHelper.getSolutionsForProblem(problem.id)

            if (solutions.isNotEmpty()) {
                val solutionsTitle = Paragraph("ÇÖZÜM ÖNERİLERİ (${solutions.size} adet):",
                    Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD))
                document.add(solutionsTitle)
                document.add(Paragraph(" "))

                solutions.forEachIndexed { index, solution ->
                    document.add(Paragraph("${index + 1}. ${solution.description}"))
                    document.add(Paragraph("   Öneren: ${solution.userName}"))
                    document.add(Paragraph("   Tarih: ${formatDate(solution.createdAt)}"))
                    if (solution.isVerified) {
                        document.add(Paragraph("   ✓ Denetmen tarafından onaylandı"))
                    }
                    document.add(Paragraph(" "))
                }
            } else {
                document.add(Paragraph("Henüz çözüm önerisi bulunmuyor."))
                document.add(Paragraph(" "))
            }

        } catch (e: Exception) {
            document.add(Paragraph("Çözümler yüklenemedi"))
            document.add(Paragraph(" "))
        }
    }

    /**
     * Raporu paylaş
     */
    private fun shareReport() {
        try {
            // En son oluşturulan PDF'i bul
            val filesDir = getExternalFilesDir(null)
            val pdfFiles = filesDir?.listFiles { file ->
                file.name.startsWith("5S_Rapor_") && file.name.endsWith(".pdf")
            }?.sortedByDescending { it.lastModified() }

            if (pdfFiles.isNullOrEmpty()) {
                Toast.makeText(this, "Önce PDF rapor oluşturun", Toast.LENGTH_SHORT).show()
                return
            }

            val latestPdf = pdfFiles.first()
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                latestPdf
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "5S Takip Raporu")
                putExtra(Intent.EXTRA_TEXT, "5S Takip sistemi tarafından oluşturulan rapor.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Raporu Paylaş"))

        } catch (e: Exception) {
            Toast.makeText(this, "Paylaşım hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Yardımcı fonksiyonlar
     */
    private fun createInfoCell(label: String, value: String): PdfPCell {
        val cell = PdfPCell()
        cell.addElement(Paragraph("$label $value", Font(Font.FontFamily.HELVETICA, 10f)))
        cell.border = Rectangle.NO_BORDER
        return cell
    }

    private fun createHeaderCell(text: String): PdfPCell {
        val cell = PdfPCell(Phrase(text, Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD)))
        cell.backgroundColor = BaseColor.LIGHT_GRAY
        cell.horizontalAlignment = Element.ALIGN_CENTER
        return cell
    }

    private fun createDataCell(text: String): PdfPCell {
        val cell = PdfPCell(Phrase(text, Font(Font.FontFamily.HELVETICA, 10f)))
        cell.horizontalAlignment = Element.ALIGN_CENTER
        return cell
    }

    /**
     * Geri buton basıldığında
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}