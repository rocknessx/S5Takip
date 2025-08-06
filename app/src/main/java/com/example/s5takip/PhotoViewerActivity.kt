package com.fabrika.s5takip

import android.os.Bundle
import android.view.View

import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

import java.io.File

/**
 * Fotoğraf görüntüleme ekranı - Tam ekran fotoğraf gösterimi
 */
class PhotoViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
        const val EXTRA_PHOTO_TITLE = "photo_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tam ekran tema ayarları
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )

        setContentView(R.layout.activity_photo_viewer)

        // Intent'ten fotoğraf bilgilerini al
        val photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH) ?: ""
        val photoTitle = intent.getStringExtra(EXTRA_PHOTO_TITLE) ?: "Fotoğraf"

        // View'ları bul
        val ivPhoto = findViewById<ImageView>(R.id.iv_full_photo)
        val tvTitle = findViewById<TextView>(R.id.tv_photo_title)
        val btnClose = findViewById<View>(R.id.btn_close_viewer)

        // Başlığı ayarla
        tvTitle.text = photoTitle

        // Fotoğrafı yükle
        if (photoPath.isNotEmpty() && File(photoPath).exists()) {
            Glide.with(this)
                .load(File(photoPath))
                .into(ivPhoto)
        } else {
            ivPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Kapatma butonu
        btnClose.setOnClickListener {
            finish()
        }

        // Fotoğrafa tıklayınca da kapat
        ivPhoto.setOnClickListener {
            finish()
        }
    }
}




