package com.fabrika.s5takip

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * SQLite veritabanı yönetim sınıfı - Tam hatasız versiyon
 * Tüm veriler telefonda saklanır
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "s5_takip.db"
        private const val DATABASE_VERSION = 3 // Versiyon artırıldı

        // Tablo isimleri
        private const val TABLE_USERS = "users"
        private const val TABLE_PROBLEMS = "problems"
        private const val TABLE_SOLUTIONS = "solutions"
    }

    /**
     * DatabaseHelper - Grup Problemleri Ayrımı Düzeltmesi
     * ✅ Her grubun kendi problemleri ayrı gösterilir
     */

// DatabaseHelper.kt içindeki bu fonksiyonları değiştir:

    /**
     * Problem ekle - Grup ID'si ile - DÜZELTİLMİŞ VERSİYON
     */
    fun insertProblem(problem: Problem): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", problem.id)
                put("group_id", problem.groupId) // ✅ Grup ID'si eklendi
                put("auditor_id", problem.auditorId)
                put("auditor_name", problem.auditorName)
                put("description", problem.description)
                put("location", problem.location)
                put("image_path", problem.imagePath)
                put("priority", problem.priority.name)
                put("status", problem.status.name)
                put("created_at", problem.createdAt)
                put("audit_date", getCurrentDateString())
            }

            val result = db.insert("problems", null, values)
            db.close()

            println("DEBUG: ✅ Problem kaydedildi - Grup ID: ${problem.groupId}")
            result != -1L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Belirli grup ve tarihteki problemleri getir - YENİ FONKSİYON
     */
    fun getProblemsForGroupAndDate(groupId: String, date: String): List<Problem> {
        val problems = mutableListOf<Problem>()

        try {
            val db = readableDatabase
            val cursor = db.query(
                "problems",
                null,
                "group_id = ? AND audit_date = ?", // ✅ Grup ID filtresi eklendi
                arrayOf(groupId, date),
                null, null,
                "created_at DESC"
            )

            while (cursor.moveToNext()) {
                val problem = Problem(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    groupId = cursor.getString(cursor.getColumnIndexOrThrow("group_id")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    priority = ProblemPriority.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("priority"))),
                    status = ProblemStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                    auditorId = cursor.getString(cursor.getColumnIndexOrThrow("auditor_id")),
                    auditorName = cursor.getString(cursor.getColumnIndexOrThrow("auditor_name")),
                    imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
                problems.add(problem)
            }

            cursor.close()
            db.close()

            println("DEBUG: ✅ Grup $groupId için $date tarihinde ${problems.size} problem bulundu")
        } catch (e: Exception) {
            e.printStackTrace()
            println("DEBUG: ❌ Grup problemleri yükleme hatası: ${e.message}")
        }

        return problems
    }

    /**
     * Grup için istatistik hesapla - YENİ FONKSİYON
     */
    fun getStatsForGroupAndDate(groupId: String, date: String): DailyStats {
        return try {
            val db = readableDatabase

            // Toplam problem sayısı (grup filtresli)
            val totalCursor = db.query(
                "problems",
                arrayOf("COUNT(*) as total"),
                "group_id = ? AND audit_date = ?",
                arrayOf(groupId, date),
                null, null, null
            )

            var totalProblems = 0
            if (totalCursor.moveToFirst()) {
                totalProblems = totalCursor.getInt(0)
            }
            totalCursor.close()

            // Durumlara göre sayılar (grup filtresli)
            val statusCursor = db.query(
                "problems",
                arrayOf("status", "COUNT(*) as count"),
                "group_id = ? AND audit_date = ?",
                arrayOf(groupId, date),
                "status",
                null,
                null
            )

            var openProblems = 0
            var inProgressProblems = 0
            var resolvedProblems = 0
            var verifiedProblems = 0

            while (statusCursor.moveToNext()) {
                val status = statusCursor.getString(0)
                val count = statusCursor.getInt(1)

                when (status) {
                    "OPEN" -> openProblems = count
                    "IN_PROGRESS" -> inProgressProblems = count
                    "RESOLVED" -> resolvedProblems = count
                    "VERIFIED" -> verifiedProblems = count
                }
            }

            statusCursor.close()
            db.close()

            println("DEBUG: ✅ Grup $groupId için $date tarihinde istatistikler hesaplandı")

            DailyStats(
                date = date,
                totalProblems = totalProblems,
                openProblems = openProblems,
                inProgressProblems = inProgressProblems,
                resolvedProblems = resolvedProblems,
                verifiedProblems = verifiedProblems
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("DEBUG: ❌ Grup istatistik hesaplama hatası: ${e.message}")

            // Hata durumunda sıfır değerlerle oluştur
            DailyStats(
                date = date,
                totalProblems = 0,
                openProblems = 0,
                inProgressProblems = 0,
                resolvedProblems = 0,
                verifiedProblems = 0
            )
        }
    }
    /**
     * Veritabanı tablosunu güncelle - Grup ID sütunu ekle
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Problemler tablosuna group_id sütunu ekle
            try {
                db.execSQL("ALTER TABLE problems ADD COLUMN group_id TEXT DEFAULT ''")
                println("DEBUG: ✅ problems tablosuna group_id sütunu eklendi")
            } catch (e: Exception) {
                println("DEBUG: group_id sütunu zaten var veya hata: ${e.message}")
            }

            // Çözümler tablosuna da group_id ekle
            try {
                db.execSQL("ALTER TABLE solutions ADD COLUMN group_id TEXT DEFAULT ''")
                println("DEBUG: ✅ solutions tablosuna group_id sütunu eklendi")
            } catch (e: Exception) {
                println("DEBUG: group_id sütunu zaten var veya hata: ${e.message}")
            }
        }

        // Eski tabloları silme yerine güncelleyici yaklaşım
        // db.execSQL("DROP TABLE IF EXISTS problems")
        // db.execSQL("DROP TABLE IF EXISTS solutions")
        // onCreate(db)
    }

    /**
     * Veritabanı ilk oluşturulduğunda çalışır - Düzeltilmiş versiyon
     */
    override fun onCreate(db: SQLiteDatabase) {
        // Kullanıcılar tablosu
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                department TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent()

        // Problemler tablosu - Grup ID'si ile
        val createProblemsTable = """
        CREATE TABLE problems (
            id TEXT PRIMARY KEY,
            group_id TEXT NOT NULL DEFAULT '', -- ✅ Grup ID sütunu eklendi
            auditor_id TEXT NOT NULL,
            auditor_name TEXT NOT NULL,
            description TEXT NOT NULL,
            location TEXT NOT NULL,
            image_path TEXT DEFAULT '',
            priority TEXT DEFAULT 'MEDIUM',
            status TEXT DEFAULT 'OPEN',
            created_at INTEGER NOT NULL,
            audit_date TEXT NOT NULL
        )
    """.trimIndent()

        // Çözümler tablosu - Grup ID'si ile
        val createSolutionsTable = """
        CREATE TABLE solutions (
            id TEXT PRIMARY KEY,
            group_id TEXT NOT NULL DEFAULT '', -- ✅ Grup ID sütunu eklendi
            problem_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            user_name TEXT NOT NULL,
            description TEXT NOT NULL,
            image_path TEXT DEFAULT '',
            created_at INTEGER NOT NULL,
            is_verified INTEGER DEFAULT 0,
            FOREIGN KEY (problem_id) REFERENCES problems (id)
        )
    """.trimIndent()

        // Tabloları oluştur
        db.execSQL(createUsersTable)
        db.execSQL(createProblemsTable)
        db.execSQL(createSolutionsTable)
    }



    // ==================== KULLANICI İŞLEMLERİ ====================

    /**
     * Yeni kullanıcı ekle
     */
    fun insertUser(user: User): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", user.id)
                put("name", user.name)
                put("email", user.email)
                put("department", user.department)
                put("role", user.role.name)
                put("created_at", user.createdAt)
            }

            val result = db.insert(TABLE_USERS, null, values)
            db.close()
            result != -1L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * ID ile kullanıcı getir
     */
    fun getUserById(userId: String): User? {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_USERS,
                null,
                "id = ?",
                arrayOf(userId),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                val user = User(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
                    department = cursor.getString(cursor.getColumnIndexOrThrow("department")),
                    role = UserRole.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("role"))),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
                cursor.close()
                db.close()
                user
            } else {
                cursor.close()
                db.close()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Tüm kullanıcıları listele
     */
    fun getAllUsers(): List<User> {
        val users = mutableListOf<User>()

        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_USERS, null, null, null, null, null, "name ASC")

            while (cursor.moveToNext()) {
                val user = User(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
                    department = cursor.getString(cursor.getColumnIndexOrThrow("department")),
                    role = UserRole.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("role"))),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
                users.add(user)
            }

            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return users
    }

    // ==================== PROBLEM İŞLEMLERİ ====================



    /**
     * Belirli tarihteki problemleri getir - Düzeltilmiş versiyon
     */
    fun getProblemsForDate(date: String): List<Problem> {
        val problems = mutableListOf<Problem>()

        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_PROBLEMS,
                null,
                "audit_date = ?",
                arrayOf(date),
                null, null,
                "created_at DESC"
            )

            while (cursor.moveToNext()) {
                val problem = Problem(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    priority = ProblemPriority.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("priority"))),
                    status = ProblemStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                    auditorId = cursor.getString(cursor.getColumnIndexOrThrow("auditor_id")),
                    auditorName = cursor.getString(cursor.getColumnIndexOrThrow("auditor_name")),
                    imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
                problems.add(problem)
            }

            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return problems
    }

    /**
     * Problem durumunu güncelle
     */
    fun updateProblemStatus(problemId: String, newStatus: ProblemStatus): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("status", newStatus.name)
            }

            val result = db.update(TABLE_PROBLEMS, values, "id = ?", arrayOf(problemId))
            db.close()
            result > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==================== ÇÖZÜM İŞLEMLERİ ====================

    /**
     * Yeni çözüm ekle
     */
    fun insertSolution(solution: Solution): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", solution.id)
                put("problem_id", solution.problemId)
                put("user_id", solution.userId)
                put("user_name", solution.userName)
                put("description", solution.description)
                put("image_path", solution.imagePath)
                put("created_at", solution.createdAt)
                put("is_verified", if (solution.isVerified) 1 else 0)
            }

            val result = db.insert(TABLE_SOLUTIONS, null, values)
            db.close()
            result != -1L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Belirli bir probleme ait çözümleri getir
     */
    fun getSolutionsForProblem(problemId: String): List<Solution> {
        val solutions = mutableListOf<Solution>()

        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_SOLUTIONS,
                null,
                "problem_id = ?",
                arrayOf(problemId),
                null, null,
                "created_at ASC"  // Eskiden yeniye sırala
            )

            while (cursor.moveToNext()) {
                val solution = Solution(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    problemId = cursor.getString(cursor.getColumnIndexOrThrow("problem_id")),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id")),
                    userName = cursor.getString(cursor.getColumnIndexOrThrow("user_name")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    isVerified = cursor.getInt(cursor.getColumnIndexOrThrow("is_verified")) == 1
                )
                solutions.add(solution)
            }

            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return solutions
    }

    // ==================== İSTATİSTİK İŞLEMLERİ ====================

    /**
     * Belirli bir tarih için istatistik hesapla - Düzeltilmiş versiyon
     */
    fun getStatsForDate(date: String): DailyStats {
        return try {
            val db = readableDatabase

            // Toplam problem sayısı
            val totalCursor = db.query(
                TABLE_PROBLEMS,
                arrayOf("COUNT(*) as total"),
                "audit_date = ?",
                arrayOf(date),
                null, null, null
            )

            var totalProblems = 0
            if (totalCursor.moveToFirst()) {
                totalProblems = totalCursor.getInt(0)
            }
            totalCursor.close()

            // Durumlara göre sayılar
            val statusCursor = db.query(
                TABLE_PROBLEMS,
                arrayOf("status", "COUNT(*) as count"),
                "audit_date = ?",
                arrayOf(date),
                "status",
                null,
                null
            )

            var openProblems = 0
            var inProgressProblems = 0
            var resolvedProblems = 0
            var verifiedProblems = 0

            while (statusCursor.moveToNext()) {
                val status = statusCursor.getString(0)
                val count = statusCursor.getInt(1)

                when (status) {
                    "OPEN" -> openProblems = count
                    "IN_PROGRESS" -> inProgressProblems = count
                    "RESOLVED" -> resolvedProblems = count
                    "VERIFIED" -> verifiedProblems = count
                }
            }

            statusCursor.close()
            db.close()

            DailyStats(
                date = date,
                totalProblems = totalProblems,
                openProblems = openProblems,
                inProgressProblems = inProgressProblems,
                resolvedProblems = resolvedProblems,
                verifiedProblems = verifiedProblems
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Hata durumunda sıfır değerlerle oluştur
            DailyStats(
                date = date,
                totalProblems = 0,
                openProblems = 0,
                inProgressProblems = 0,
                resolvedProblems = 0,
                verifiedProblems = 0
            )
        }
    }

    /**
     * Tüm tarihlerin listesini getir (rapor için)
     */
    fun getAllAuditDates(): List<String> {
        val dates = mutableListOf<String>()

        try {
            val db = readableDatabase
            val cursor = db.query(
                true, // DISTINCT
                TABLE_PROBLEMS,
                arrayOf("audit_date"),
                null, null, null, null,
                "audit_date DESC",
                null
            )

            while (cursor.moveToNext()) {
                val date = cursor.getString(cursor.getColumnIndexOrThrow("audit_date"))
                dates.add(date)
            }

            cursor.close()
            db.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return dates
    }

    // ==================== HELPER FONKSİYONLAR ====================

    /**
     * Mevcut tarihi string olarak döndür
     */
    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
}