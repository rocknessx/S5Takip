package com.fabrika.s5takip

import java.util.*

/**
 * Kullanıcı rolleri
 */
enum class UserRole {
    AUDITOR, // Denetmen
    USER     // Kullanıcı
}

/**
 * Problem öncelik seviyeleri
 */
enum class ProblemPriority {
    LOW,    // Düşük
    MEDIUM, // Orta
    HIGH,   // Yüksek
    CRITICAL // Kritik
}

/**
 * Problem durumları
 */
enum class ProblemStatus {
    OPEN,        // Açık
    IN_PROGRESS, // İşlemde
    RESOLVED,    // Çözüldü
    VERIFIED     // Doğrulandı
}

/**
 * Problem öncelik seviyesini Türkçe'ye çevir
 */
fun ProblemPriority.toTurkish(): String {
    return when (this) {
        ProblemPriority.LOW -> "Düşük"
        ProblemPriority.MEDIUM -> "Orta"
        ProblemPriority.HIGH -> "Yüksek"
        ProblemPriority.CRITICAL -> "Kritik"
    }
}

/**
 * Problem durumunu Türkçe'ye çevir
 */
fun ProblemStatus.toTurkish(): String {
    return when (this) {
        ProblemStatus.OPEN -> "Açık"
        ProblemStatus.IN_PROGRESS -> "İşlemde"
        ProblemStatus.RESOLVED -> "Çözüldü"
        ProblemStatus.VERIFIED -> "Doğrulandı"
    }
}

/**
 * Kullanıcı modeli
 */
data class User(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val department: String = "Genel", // Default değer eklendi
    val role: UserRole,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Problem modeli
 */
data class Problem(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val location: String,
    val priority: ProblemPriority,
    val status: ProblemStatus = ProblemStatus.OPEN,
    val auditorId: String,
    val auditorName: String,
    val imagePath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Çözüm modeli
 */
data class Solution(
    val id: String = UUID.randomUUID().toString(),
    val problemId: String,
    val userId: String,
    val userName: String,
    val description: String,
    val imagePath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false
)

/**
 * Günlük istatistik modeli
 */
data class DailyStats(
    val date: String,
    val totalProblems: Int = 0,        // Default değer eklendi
    val openProblems: Int = 0,         // Default değer eklendi
    val inProgressProblems: Int = 0,   // Default değer eklendi
    val resolvedProblems: Int = 0,     // Default değer eklendi
    val verifiedProblems: Int = 0      // Default değer eklendi
) {
    val resolutionRate: Double
        get() = if (totalProblems > 0) {
            ((resolvedProblems + verifiedProblems).toDouble() / totalProblems.toDouble()) * 100
        } else {
            0.0
        }
}

/**
 * Mevcut tarihi yyyy-MM-dd formatında döndür
 */
fun getCurrentDate(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return sdf.format(Date())
}

/**
 * Timestamp'i tarih formatına çevir
 */
fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ==================== FIREBASE AUTH VE GRUP MODELLERİ ====================

/**
 * Uygulama Kullanıcısı (Firebase Auth ile)
 */
data class AppUser(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val avatarUrl: String = "",
    val currentGroupId: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    // Firebase için boş constructor
    constructor() : this("", "", "", "", "", "", "", 0L)

    // Firestore'a Map olarak kaydetmek için - TYPE SAFE
    fun toMap(): HashMap<String, Any> {
        return hashMapOf<String, Any>().apply {
            put("id", id)
            put("email", email)
            put("displayName", displayName)
            put("firstName", firstName)
            put("lastName", lastName)
            put("avatarUrl", avatarUrl)
            put("currentGroupId", currentGroupId)
            put("createdAt", createdAt)
        }
    }

    companion object {
        // Map'ten AppUser'a dönüştürmek için
        fun fromMap(map: Map<String, Any?>): AppUser {
            return AppUser(
                id = map["id"] as? String ?: "",
                email = map["email"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                firstName = map["firstName"] as? String ?: "",
                lastName = map["lastName"] as? String ?: "",
                avatarUrl = map["avatarUrl"] as? String ?: "",
                currentGroupId = map["currentGroupId"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Grup Modeli - Firestore uyumlu
 */
data class Group(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val memberCount: Int = 1
) {
    // Firebase için boş constructor
    constructor() : this("", "", "", "", "", "", 0L, 1)

    // Firestore'a Map olarak kaydetmek için - TYPE SAFE
    fun toMap(): HashMap<String, Any> {
        return hashMapOf<String, Any>().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("inviteCode", inviteCode)
            put("ownerId", ownerId)
            put("ownerName", ownerName)
            put("createdAt", createdAt)
            put("memberCount", memberCount)
        }
    }

    companion object {
        // Map'ten Group'a dönüştürmek için
        fun fromMap(map: Map<String, Any?>): Group {
            return Group(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                inviteCode = map["inviteCode"] as? String ?: "",
                ownerId = map["ownerId"] as? String ?: "",
                ownerName = map["ownerName"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                memberCount = (map["memberCount"] as? Number)?.toInt() ?: 1
            )
        }

        fun generateInviteCode(): String {
            return (100000..999999).random().toString()
        }
    }
}

/**
 * Grup Üyelik Modeli - Firestore uyumlu
 */
data class GroupMember(
    val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val role: String = "MEMBER",
    val joinedAt: Long = System.currentTimeMillis()
) {
    // Firebase için boş constructor
    constructor() : this("", "", "", "", "", "", "MEMBER", 0L)

    // Firestore'a Map olarak kaydetmek için - TYPE SAFE
    fun toMap(): HashMap<String, Any> {
        return hashMapOf<String, Any>().apply {
            put("id", id)
            put("groupId", groupId)
            put("userId", userId)
            put("userEmail", userEmail)
            put("userName", userName)
            put("userAvatar", userAvatar)
            put("role", role)
            put("joinedAt", joinedAt)
        }
    }

    companion object {
        // Map'ten GroupMember'a dönüştürmek için
        fun fromMap(map: Map<String, Any?>): GroupMember {
            return GroupMember(
                id = map["id"] as? String ?: "",
                groupId = map["groupId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                userEmail = map["userEmail"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                userAvatar = map["userAvatar"] as? String ?: "",
                role = map["role"] as? String ?: "MEMBER",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Grup İçi Roller
 */
object GroupRoles {
    const val OWNER = "OWNER"     // Grup kurucusu
    const val ADMIN = "ADMIN"     // Yönetici
    const val AUDITOR = "AUDITOR" // Denetmen
    const val MEMBER = "MEMBER"   // Üye
}

/**
 * Haftalık Denetmen Ataması
 */
data class WeeklyAuditor(
    val id: String = "",
    val groupId: String = "",
    val weekDay: Int = 1, // 1=Pazartesi, 7=Pazar
    val auditorId: String = "",
    val auditorName: String = "",
    val assignedBy: String = "",
    val assignedAt: Long = System.currentTimeMillis()
) {
    // Firebase için boş constructor
    constructor() : this("", "", 1, "", "", "", 0L)

    // Firestore'a Map olarak kaydetmek için - TYPE SAFE
    fun toMap(): HashMap<String, Any> {
        return hashMapOf<String, Any>().apply {
            put("id", id)
            put("groupId", groupId)
            put("weekDay", weekDay)
            put("auditorId", auditorId)
            put("auditorName", auditorName)
            put("assignedBy", assignedBy)
            put("assignedAt", assignedAt)
        }
    }

    companion object {
        // Map'ten WeeklyAuditor'a dönüştürmek için
        fun fromMap(map: Map<String, Any?>): WeeklyAuditor {
            return WeeklyAuditor(
                id = map["id"] as? String ?: "",
                groupId = map["groupId"] as? String ?: "",
                weekDay = (map["weekDay"] as? Number)?.toInt() ?: 1,
                auditorId = map["auditorId"] as? String ?: "",
                auditorName = map["auditorName"] as? String ?: "",
                assignedBy = map["assignedBy"] as? String ?: "",
                assignedAt = (map["assignedAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Chat Mesajı
 */
data class ChatMessage(
    val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val message: String = "",
    val messageType: String = "TEXT", // TEXT, IMAGE, SYSTEM
    val createdAt: Long = System.currentTimeMillis()
) {
    // Firebase için boş constructor
    constructor() : this("", "", "", "", "", "", "TEXT", 0L)

    // Firestore'a Map olarak kaydetmek için - TYPE SAFE
    fun toMap(): HashMap<String, Any> {
        return hashMapOf<String, Any>().apply {
            put("id", id)
            put("groupId", groupId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("senderAvatar", senderAvatar)
            put("message", message)
            put("messageType", messageType)
            put("createdAt", createdAt)
        }
    }

    companion object {
        // Map'ten ChatMessage'a dönüştürmek için
        fun fromMap(map: Map<String, Any?>): ChatMessage {
            return ChatMessage(
                id = map["id"] as? String ?: "",
                groupId = map["groupId"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                senderAvatar = map["senderAvatar"] as? String ?: "",
                message = map["message"] as? String ?: "",
                messageType = map["messageType"] as? String ?: "TEXT",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}