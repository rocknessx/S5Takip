package com.fabrika.s5takip

import android.view.LayoutInflater
import android.view.View
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Problemleri listede göstermek için adapter - Güncellenmiş versiyon
 */
class ProblemsAdapter(
    private var problems: List<Problem>,
    private val photoManager: PhotoManager,
    private val databaseHelper: DatabaseHelper,  // DatabaseHelper eklendi
    private val onAddSolutionClick: (Problem) -> Unit,
    private val onViewDetailsClick: (Problem) -> Unit
) : RecyclerView.Adapter<ProblemsAdapter.ProblemViewHolder>() {

    class ProblemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProblemId: TextView = view.findViewById(R.id.tv_problem_id)
        val tvProblemStatus: TextView = view.findViewById(R.id.tv_problem_status)
        val tvProblemDescription: TextView = view.findViewById(R.id.tv_problem_description)
        val tvProblemLocation: TextView = view.findViewById(R.id.tv_problem_location)
        val tvProblemTime: TextView = view.findViewById(R.id.tv_problem_time)
        val ivProblemPhoto: ImageView = view.findViewById(R.id.iv_problem_photo)
        val tvAuditorName: TextView = view.findViewById(R.id.tv_auditor_name)
        val tvSolutionsCount: TextView = view.findViewById(R.id.tv_solutions_count)
        val btnAddSolution: Button = view.findViewById(R.id.btn_add_solution)
        val btnViewDetails: Button = view.findViewById(R.id.btn_view_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProblemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_problem, parent, false)
        return ProblemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProblemViewHolder, position: Int) {
        val problem = problems[position]
        val context = holder.itemView.context

        // ... mevcut kod ...

        // Problem fotoğrafı
        if (problem.imagePath.isNotEmpty()) {
            photoManager.loadPhoto(problem.imagePath, holder.ivProblemPhoto)
        } else {
            holder.ivProblemPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // ✅ FOTOĞRAFA TIKLAMA - Problem detayına git
        holder.ivProblemPhoto.setOnClickListener {
            // Problem detayına git
            onViewDetailsClick(problem)
        }

        // ✅ Çözüm sayısını veritabanından al
        val solutionCount = try {
            val solutions = databaseHelper.getSolutionsForProblem(problem.id)
            solutions.size
        } catch (e: Exception) {
            0
        }

        // Çözüm sayısını göster
        val solutionText = when (solutionCount) {
            0 -> "💡 Henüz çözüm yok"
            1 -> "💡 1 çözüm önerisi"
            else -> "💡 $solutionCount çözüm önerisi"
        }
        holder.tvSolutionsCount.text = solutionText

        // Buton click'leri
        holder.btnAddSolution.setOnClickListener {
            onAddSolutionClick(problem)
        }

        holder.btnViewDetails.setOnClickListener {
            onViewDetailsClick(problem)
        }
    }

    override fun getItemCount(): Int = problems.size

    /**
     * Problem listesini güncelle
     */
    fun updateProblems(newProblems: List<Problem>) {
        problems = newProblems
        notifyDataSetChanged()
    }

    /**
     * Problem durumuna göre renk döndür
     */
    private fun getStatusColor(status: ProblemStatus, context: android.content.Context): Int {
        return when (status) {
            ProblemStatus.OPEN -> ContextCompat.getColor(context, R.color.status_open)
            ProblemStatus.IN_PROGRESS -> ContextCompat.getColor(context, R.color.status_in_progress)
            ProblemStatus.RESOLVED -> ContextCompat.getColor(context, R.color.status_resolved)
            ProblemStatus.VERIFIED -> ContextCompat.getColor(context, R.color.status_verified)
        }
    }

    /**
     * Timestamp'i saat:dakika formatına çevir
     */
    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }


}