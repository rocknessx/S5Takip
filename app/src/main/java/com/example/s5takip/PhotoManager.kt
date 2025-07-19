package com.fabrika.s5takip

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

/**
 * Fotoğraf işlemlerini yöneten sınıf
 * Fotoğrafları telefonda saklar ve yükler
 */
class PhotoManager(private val context: Context) {

    companion object {
        private const val PHOTOS_FOLDER = "s5_photos"
        private const val PROBLEMS_FOLDER = "problems"
        private const val SOLUTIONS_FOLDER = "solutions"
    }

    /**
     * Ana fotoğraf klasörünü oluştur
     */
    private fun createPhotosDirectory(): File {
        val photosDir = File(context.filesDir, PHOTOS_FOLDER)
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return photosDir
    }

    /**
     * Problem fotoğraflarını saklama klasörü
     */
    private fun createProblemsDirectory(): File {
        val problemsDir = File(createPhotosDirectory(), PROBLEMS_FOLDER)
        if (!problemsDir.exists()) {
            problemsDir.mkdirs()
        }
        return problemsDir
    }

    /**
     * Çözüm fotoğraflarını saklama klasörü
     */
    private fun createSolutionsDirectory(): File {
        val solutionsDir = File(createPhotosDirectory(), SOLUTIONS_FOLDER)
        if (!solutionsDir.exists()) {
            solutionsDir.mkdirs()
        }
        return solutionsDir
    }

    /**
     * Problem fotoğrafını kaydet
     * @param imageUri Seçilen fotoğrafın URI'si
     * @param problemId Problem ID'si (dosya adı için)
     * @return Kaydedilen dosyanın yolu
     */
    fun saveProblemPhoto(imageUri: Uri, problemId: String): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val fileName = "${problemId}_${System.currentTimeMillis()}.jpg"
            val file = File(createProblemsDirectory(), fileName)

            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Çözüm fotoğrafını kaydet
     * @param imageUri Seçilen fotoğrafın URI'si
     * @param solutionId Çözüm ID'si (dosya adı için)
     * @return Kaydedilen dosyanın yolu
     */
    fun saveSolutionPhoto(imageUri: Uri, solutionId: String): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val fileName = "${solutionId}_${System.currentTimeMillis()}.jpg"
            val file = File(createSolutionsDirectory(), fileName)

            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fotoğrafı ImageView'a yükle
     * @param imagePath Fotoğrafın dosya yolu
     * @param imageView Yüklenecek ImageView
     */
    fun loadPhoto(imagePath: String, imageView: ImageView) {
        if (imagePath.isNotEmpty() && File(imagePath).exists()) {
            Glide.with(context)
                .load(File(imagePath))
                .placeholder(android.R.drawable.ic_menu_gallery) // Yüklenirken gösterilecek resim
                .error(android.R.drawable.ic_delete)            // Hata durumunda gösterilecek resim
                .into(imageView)
        } else {
            // Varsayılan resim göster
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    /**
     * Fotoğrafı sil
     * @param imagePath Silinecek fotoğrafın yolu
     * @return Silme işleminin başarılı olup olmadığı
     */
    fun deletePhoto(imagePath: String): Boolean {
        return try {
            if (imagePath.isNotEmpty()) {
                val file = File(imagePath)
                if (file.exists()) {
                    file.delete()
                } else {
                    true // Dosya zaten yok, başarılı sayılır
                }
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fotoğrafın var olup olmadığını kontrol et
     * @param imagePath Kontrol edilecek fotoğrafın yolu
     * @return Fotoğrafın var olup olmadığı
     */
    fun photoExists(imagePath: String): Boolean {
        return if (imagePath.isNotEmpty()) {
            File(imagePath).exists()
        } else {
            false
        }
    }

    /**
     * Toplam fotoğraf sayısını öğren
     * @return Problem ve çözüm fotoğraflarının toplam sayısı
     */
    fun getTotalPhotoCount(): Int {
        return try {
            val problemPhotos = createProblemsDirectory().listFiles()?.size ?: 0
            val solutionPhotos = createSolutionsDirectory().listFiles()?.size ?: 0
            problemPhotos + solutionPhotos
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Toplam fotoğraf boyutunu öğren (MB cinsinden)
     * @return Toplam fotoğraf boyutu
     */
    fun getTotalPhotoSize(): Double {
        return try {
            var totalSize = 0L

            // Problem fotoğrafları
            createProblemsDirectory().listFiles()?.forEach { file ->
                totalSize += file.length()
            }

            // Çözüm fotoğrafları
            createSolutionsDirectory().listFiles()?.forEach { file ->
                totalSize += file.length()
            }

            // Byte'ı MB'ye çevir
            totalSize / (1024.0 * 1024.0)
        } catch (e: Exception) {
            0.0
        }
    }

    /**
     * Eski fotoğrafları temizle (30 günden eski)
     * @return Silinen fotoğraf sayısı
     */
    fun cleanOldPhotos(): Int {
        return try {
            var deletedCount = 0
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)

            // Problem fotoğraflarını kontrol et
            createProblemsDirectory().listFiles()?.forEach { file ->
                if (file.lastModified() < thirtyDaysAgo) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            // Çözüm fotoğraflarını kontrol et
            createSolutionsDirectory().listFiles()?.forEach { file ->
                if (file.lastModified() < thirtyDaysAgo) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            deletedCount
        } catch (e: Exception) {
            0
        }
    }
}