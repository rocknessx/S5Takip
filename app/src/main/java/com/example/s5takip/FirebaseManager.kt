package com.fabrika.s5takip

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Firebase işlemlerini yöneten sınıf - Son güncel versiyon
 */
class FirebaseManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: FirebaseManager? = null

        fun getInstance(): FirebaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseManager().also { INSTANCE = it }
            }
        }
    }

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance("s5takip") // Özel database

    /**
     * Google Sign-In Client oluştur - Otomatik Web Client ID ile
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id)) // Otomatik
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Google hesabıyla Firebase'e giriş yap - Düzeltilmiş versiyon
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<AppUser> {
        return try {
            println("DEBUG: Google sign-in başladı - Account: ${account.email}")

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            println("DEBUG: Google credential oluşturuldu")

            val authResult = auth.signInWithCredential(credential).await()
            println("DEBUG: Firebase auth tamamlandı")

            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                println("DEBUG: Firebase kullanıcı oluşturuldu: ${firebaseUser.email}")

                val appUser = AppUser(
                    id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Kullanıcı",
                    avatarUrl = firebaseUser.photoUrl?.toString() ?: ""
                )

                println("DEBUG: AppUser objesi oluşturuldu: ${appUser.displayName}")

                // Kullanıcıyı Firestore'a kaydet
                try {
                    saveUserToFirestore(appUser)
                    println("DEBUG: Kullanıcı Firestore'a kaydedildi")
                } catch (e: Exception) {
                    println("DEBUG: Firestore kaydetme hatası (devam ediliyor): ${e.message}")
                    // Firestore hatası olsa bile devam et
                }

                Result.success(appUser)
            } else {
                println("DEBUG: Firebase kullanıcı null")
                Result.failure(Exception("Firebase giriş başarısız - kullanıcı null"))
            }
        } catch (e: Exception) {
            println("DEBUG: Google sign-in hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Kullanıcıyı Firestore'a kaydet
     */
    private suspend fun saveUserToFirestore(user: AppUser) {
        try {
            println("DEBUG: Kullanıcı Firestore'a kaydediliyor: ${user.email}")

            firestore.collection("users")
                .document(user.id)
                .set(user.toMap()) // Map kullan
                .await()

            println("DEBUG: Kullanıcı başarıyla kaydedildi")
        } catch (e: Exception) {
            println("DEBUG: Kullanıcı kaydetme hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Firestore bağlantısını test et - TYPE SAFE
     */
    suspend fun testFirestoreWrite(data: Map<String, Any>): Result<Unit> {
        return try {
            println("DEBUG: Firestore test yazma başladı")

            // Type safe Map oluştur
            val testData = hashMapOf<String, Any>().apply {
                put("test", "connection")
                put("timestamp", System.currentTimeMillis())
                put("status", "active")
            }

            firestore.collection("test")
                .document("connection_test")
                .set(testData)
                .await()

            println("DEBUG: Firestore test yazma başarılı")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Firestore test yazma hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Grup oluştur - Map ile düzeltilmiş versiyon
     */
    suspend fun createGroup(groupName: String, description: String): Result<Group> {
        return try {
            println("DEBUG: createGroup başladı - Ad: $groupName")

            val currentUser = auth.currentUser
            if (currentUser == null) {
                println("DEBUG: Kullanıcı null!")
                return Result.failure(Exception("Kullanıcı giriş yapmamış"))
            }

            println("DEBUG: Kullanıcı bulundu: ${currentUser.email}")

            val groupId = UUID.randomUUID().toString()
            val inviteCode = Group.generateInviteCode()

            val group = Group(
                id = groupId,
                name = groupName,
                description = description,
                inviteCode = inviteCode,
                ownerId = currentUser.uid,
                ownerName = currentUser.displayName ?: currentUser.email ?: "Kullanıcı",
                memberCount = 1
            )

            println("DEBUG: Grup objesi oluşturuldu - ID: $groupId, Kod: $inviteCode")

            // Grubu Map olarak Firestore'a kaydet
            println("DEBUG: Grup Map olarak Firestore'a kaydediliyor...")
            firestore.collection("groups")
                .document(groupId)
                .set(group.toMap()) // Map kullan
                .await()

            println("DEBUG: Grup başarıyla kaydedildi!")

            // Kurucuyu grup üyesi olarak ekle
            val ownerMembership = GroupMember(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                userId = currentUser.uid,
                userEmail = currentUser.email ?: "",
                userName = currentUser.displayName ?: currentUser.email ?: "Kullanıcı",
                userAvatar = currentUser.photoUrl?.toString() ?: "",
                role = GroupRoles.OWNER
            )

            println("DEBUG: Üyelik Map olarak kaydediliyor...")
            firestore.collection("group_members")
                .document(ownerMembership.id)
                .set(ownerMembership.toMap()) // Map kullan
                .await()

            println("DEBUG: Üyelik başarıyla kaydedildi!")
            println("DEBUG: Grup oluşturma tamamlandı!")

            Result.success(group)
        } catch (e: Exception) {
            println("DEBUG: createGroup hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Gruba katıl - Map ile düzeltilmiş versiyon
     */
    suspend fun joinGroup(inviteCode: String): Result<Group> {
        return try {
            println("DEBUG: joinGroup başladı - Kod: $inviteCode")

            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Kullanıcı giriş yapmamış"))
            }

            // Invite code ile grubu bul
            println("DEBUG: Davet kodu ile grup aranıyor...")
            val querySnapshot = firestore.collection("groups")
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                println("DEBUG: Geçersiz davet kodu")
                return Result.failure(Exception("Geçersiz davet kodu"))
            }

            // Map'ten Group'a dönüştür
            val groupMap = querySnapshot.documents.first().data
            val group = if (groupMap != null) {
                Group.fromMap(groupMap)
            } else {
                return Result.failure(Exception("Grup bilgisi alınamadı"))
            }

            println("DEBUG: Grup bulundu: ${group.name}")

            // Kullanıcı zaten üye mi kontrol et
            println("DEBUG: Mevcut üyelik kontrol ediliyor...")
            val memberCheck = firestore.collection("group_members")
                .whereEqualTo("groupId", group.id)
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()

            if (!memberCheck.isEmpty) {
                println("DEBUG: Kullanıcı zaten üye")
                return Result.failure(Exception("Zaten bu grubun üyesisiniz"))
            }

            // Kullanıcıyı gruba ekle
            val membership = GroupMember(
                id = UUID.randomUUID().toString(),
                groupId = group.id,
                userId = currentUser.uid,
                userEmail = currentUser.email ?: "",
                userName = currentUser.displayName ?: currentUser.email ?: "Kullanıcı",
                userAvatar = currentUser.photoUrl?.toString() ?: "",
                role = GroupRoles.MEMBER
            )

            println("DEBUG: Yeni üyelik Map olarak kaydediliyor...")
            firestore.collection("group_members")
                .document(membership.id)
                .set(membership.toMap()) // Map kullan
                .await()

            println("DEBUG: Gruba katılma başarılı!")
            Result.success(group)
        } catch (e: Exception) {
            println("DEBUG: joinGroup hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Kullanıcının gruplarını getir - Map ile düzeltilmiş versiyon
     */
    suspend fun getUserGroups(): Result<List<Group>> {
        return try {
            println("DEBUG: getUserGroups başladı")

            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Kullanıcı giriş yapmamış"))
            }

            // Kullanıcının üye olduğu grupları bul
            println("DEBUG: Kullanıcının üyelikleri aranıyor...")
            val memberships = firestore.collection("group_members")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()

            val groupIds = memberships.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    GroupMember.fromMap(data).groupId
                } else null
            }

            println("DEBUG: Bulunan grup ID'leri: ${groupIds.size} adet")

            if (groupIds.isEmpty()) {
                println("DEBUG: Kullanıcının hiç grubu yok")
                return Result.success(emptyList())
            }

            // Grup bilgilerini getir
            val groups = mutableListOf<Group>()
            for (groupId in groupIds) {
                println("DEBUG: Grup bilgisi alınıyor: $groupId")
                val groupDoc = firestore.collection("groups")
                    .document(groupId)
                    .get()
                    .await()

                val groupData = groupDoc.data
                if (groupData != null) {
                    val group = Group.fromMap(groupData)
                    groups.add(group)
                    println("DEBUG: Grup eklendi: ${group.name}")
                }
            }

            println("DEBUG: Toplam ${groups.size} grup bulundu")
            Result.success(groups)
        } catch (e: Exception) {
            println("DEBUG: getUserGroups hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Grup üyelerini getir
     */
    suspend fun getGroupMembers(groupId: String): Result<List<GroupMember>> {
        return try {
            println("DEBUG: getGroupMembers başladı - Grup ID: $groupId")

            val memberships = firestore.collection("group_members")
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val members = memberships.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    GroupMember.fromMap(data)
                } else null
            }

            println("DEBUG: ${members.size} üye bulundu")
            Result.success(members)
        } catch (e: Exception) {
            println("DEBUG: getGroupMembers hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Haftalık denetmen ataması kaydet
     */
    suspend fun saveWeeklyAuditor(weeklyAuditor: WeeklyAuditor): Result<Unit> {
        return try {
            println("DEBUG: Haftalık denetmen kaydediliyor...")

            firestore.collection("weekly_auditors")
                .document(weeklyAuditor.id)
                .set(weeklyAuditor.toMap())
                .await()

            println("DEBUG: Haftalık denetmen başarıyla kaydedildi")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Haftalık denetmen kaydetme hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Grup için haftalık denetmenleri getir
     */
    suspend fun getWeeklyAuditors(groupId: String): Result<List<WeeklyAuditor>> {
        return try {
            println("DEBUG: Haftalık denetmenler getiriliyor...")

            val auditors = firestore.collection("weekly_auditors")
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val weeklyAuditors = auditors.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    WeeklyAuditor.fromMap(data)
                } else null
            }

            println("DEBUG: ${weeklyAuditors.size} haftalık denetmen bulundu")
            Result.success(weeklyAuditors)
        } catch (e: Exception) {
            println("DEBUG: Haftalık denetmenler getirme hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Chat mesajı kaydet
     */
    suspend fun saveChatMessage(message: ChatMessage): Result<Unit> {
        return try {
            println("DEBUG: Chat mesajı kaydediliyor...")

            firestore.collection("chat_messages")
                .document(message.id)
                .set(message.toMap())
                .await()

            println("DEBUG: Chat mesajı başarıyla kaydedildi")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Chat mesajı kaydetme hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Grup chat mesajlarını getir
     */
    suspend fun getChatMessages(groupId: String, limit: Int = 50): Result<List<ChatMessage>> {
        return try {
            println("DEBUG: Chat mesajları getiriliyor...")

            val messages = firestore.collection("chat_messages")
                .whereEqualTo("groupId", groupId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val chatMessages = messages.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    ChatMessage.fromMap(data)
                } else null
            }.reversed() // Eski mesajdan yeniye sırala

            println("DEBUG: ${chatMessages.size} chat mesajı bulundu")
            Result.success(chatMessages)
        } catch (e: Exception) {
            println("DEBUG: Chat mesajları getirme hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Mevcut kullanıcıyı al
     */
    fun getCurrentUser() = auth.currentUser

    /**
     * Çıkış yap
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            println("DEBUG: Çıkış yapılıyor...")
            auth.signOut()
            println("DEBUG: Çıkış başarılı")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Çıkış hatası: ${e.message}")
            Result.failure(e)
        }
    }
}