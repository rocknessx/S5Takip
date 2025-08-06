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
 * Firebase işlemlerini yöneten sınıf - Tamamen Düzeltilmiş Versiyon
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
    private val firestore = FirebaseFirestore.getInstance("s5takip")

    /**
     * Google Sign-In Client oluştur
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Google hesabıyla Firebase'e giriş yap - Basitleştirilmiş Versiyon
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

                // Firestore kaydetmeyi dene, ama hata olsa bile devam et
                try {
                    saveUserToFirestore(appUser)
                    println("DEBUG: Kullanıcı Firestore'a kaydedildi")
                } catch (e: Exception) {
                    println("DEBUG: Firestore kaydetme hatası (görmezden geliniyor): ${e.message}")
                    // Firestore hatası olsa bile kullanıcı girişini başarılı say
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
     * Kullanıcıyı Firestore'a kaydet - Hata toleranslı
     */
    private suspend fun saveUserToFirestore(user: AppUser) {
        try {
            println("DEBUG: Kullanıcı Firestore'a kaydediliyor: ${user.email}")

            val userData = hashMapOf<String, Any>(
                "id" to user.id,
                "email" to user.email,
                "displayName" to user.displayName,
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "avatarUrl" to user.avatarUrl,
                "currentGroupId" to user.currentGroupId,
                "createdAt" to user.createdAt
            )

            firestore.collection("users")
                .document(user.id)
                .set(userData)
                .await()

            println("DEBUG: Kullanıcı başarıyla kaydedildi")
        } catch (e: Exception) {
            println("DEBUG: Kullanıcı kaydetme hatası: ${e.message}")
            // Hatayı yeniden fırlat, böylece çağıran fonksiyon hata hakkında bilgi sahibi olur
            throw e
        }
    }



    /**
     * Firestore'a test yazma işlemi - GroupChatActivity için
     */
    suspend fun testFirestoreWrite(data: Map<String, Any>): Result<Unit> {
        return try {
            println("DEBUG: Firestore test yazma başladı")

            firestore.collection("test")
                .document("chat_test_${System.currentTimeMillis()}")
                .set(data)
                .await()

            println("DEBUG: Firestore test yazma başarılı")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Firestore test yazma hatası: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Grup oluştur - Sadeleştirilmiş versiyon
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

            // Grubu Firestore'a kaydet
            val groupData = hashMapOf<String, Any>(
                "id" to group.id,
                "name" to group.name,
                "description" to group.description,
                "inviteCode" to group.inviteCode,
                "ownerId" to group.ownerId,
                "ownerName" to group.ownerName,
                "createdAt" to group.createdAt,
                "memberCount" to group.memberCount
            )

            firestore.collection("groups")
                .document(groupId)
                .set(groupData)
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

            val memberData = hashMapOf<String, Any>(
                "id" to ownerMembership.id,
                "groupId" to ownerMembership.groupId,
                "userId" to ownerMembership.userId,
                "userEmail" to ownerMembership.userEmail,
                "userName" to ownerMembership.userName,
                "userAvatar" to ownerMembership.userAvatar,
                "role" to ownerMembership.role,
                "joinedAt" to ownerMembership.joinedAt
            )

            firestore.collection("group_members")
                .document(ownerMembership.id)
                .set(memberData)
                .await()

            println("DEBUG: Üyelik başarıyla kaydedildi!")
            Result.success(group)
        } catch (e: Exception) {
            println("DEBUG: createGroup hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    /**
     * Grubu ve ilişkili tüm verilerini sil
     */
    suspend fun deleteGroup(groupId: String): Result<Unit> {
        return try {
            println("DEBUG: deleteGroup başladı - Grup ID: $groupId")

            // Firestore işlemleri için bir batch oluştur
            val batch = firestore.batch()

            // 1. Grup dokümanını sil
            val groupRef = firestore.collection("groups").document(groupId)
            batch.delete(groupRef)
            println("DEBUG: Grup dokümanı silme işlemi batch'e eklendi")

            // 2. Grup üyelerini sil
            val membersSnapshot = firestore.collection("group_members")
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            for (document in membersSnapshot.documents) {
                batch.delete(document.reference)
            }
            println("DEBUG: ${membersSnapshot.size()} grup üyesi silme işlemi batch'e eklendi")

            // 3. Haftalık denetmenleri sil
            val auditorsSnapshot = firestore.collection("weekly_auditors")
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            for (document in auditorsSnapshot.documents) {
                batch.delete(document.reference)
            }
            println("DEBUG: ${auditorsSnapshot.size()} haftalık denetmen silme işlemi batch'e eklendi")

            // 4. Sohbet mesajlarını sil
            val messagesSnapshot = firestore.collection("chat_messages")
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            for (document in messagesSnapshot.documents) {
                batch.delete(document.reference)
            }
            println("DEBUG: ${messagesSnapshot.size()} sohbet mesajı silme işlemi batch'e eklendi")

            // Batch işlemlerini gerçekleştir
            batch.commit().await()
            println("DEBUG: Tüm silme işlemleri başarıyla tamamlandı")

            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: deleteGroup hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Gruba katıl
     */
    suspend fun joinGroup(inviteCode: String): Result<Group> {
        return try {
            println("DEBUG: joinGroup başladı - Kod: $inviteCode")

            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Kullanıcı giriş yapmamış"))
            }

            // Invite code ile grubu bul
            val querySnapshot = firestore.collection("groups")
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Geçersiz davet kodu"))
            }

            val groupMap = querySnapshot.documents.first().data
            val group = if (groupMap != null) {
                Group.fromMap(groupMap)
            } else {
                return Result.failure(Exception("Grup bilgisi alınamadı"))
            }

            // Kullanıcı zaten üye mi kontrol et
            val memberCheck = firestore.collection("group_members")
                .whereEqualTo("groupId", group.id)
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()

            if (!memberCheck.isEmpty) {
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

            val memberData = hashMapOf<String, Any>(
                "id" to membership.id,
                "groupId" to membership.groupId,
                "userId" to membership.userId,
                "userEmail" to membership.userEmail,
                "userName" to membership.userName,
                "userAvatar" to membership.userAvatar,
                "role" to membership.role,
                "joinedAt" to membership.joinedAt
            )

            firestore.collection("group_members")
                .document(membership.id)
                .set(memberData)
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
     * Kullanıcının gruplarını getir
     */
    suspend fun getUserGroups(): Result<List<Group>> {
        return try {
            println("DEBUG: getUserGroups başladı")

            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Kullanıcı giriş yapmamış"))
            }

            // Kullanıcının üye olduğu grupları bul
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

            if (groupIds.isEmpty()) {
                return Result.success(emptyList())
            }

            // Grup bilgilerini getir
            val groups = mutableListOf<Group>()
            for (groupId in groupIds) {
                val groupDoc = firestore.collection("groups")
                    .document(groupId)
                    .get()
                    .await()

                val groupData = groupDoc.data
                if (groupData != null) {
                    val group = Group.fromMap(groupData)
                    groups.add(group)
                }
            }

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

            Result.success(members)
        } catch (e: Exception) {
            println("DEBUG: getGroupMembers hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * ✅ YENİ: Grup üyesi bilgilerini güncelle
     */
    suspend fun updateGroupMember(member: GroupMember): Result<Unit> {
        return try {
            println("DEBUG: updateGroupMember başladı - Üye: ${member.userName}, Yeni rol: ${member.role}")

            val memberData = hashMapOf<String, Any>(
                "id" to member.id,
                "groupId" to member.groupId,
                "userId" to member.userId,
                "userEmail" to member.userEmail,
                "userName" to member.userName,
                "userAvatar" to member.userAvatar,
                "role" to member.role,
                "joinedAt" to member.joinedAt
            )

            firestore.collection("group_members")
                .document(member.id)
                .set(memberData)
                .await()

            println("DEBUG: ✅ Grup üyesi başarıyla güncellendi")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: ❌ updateGroupMember hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * ✅ YENİ: Kullanıcı profil bilgilerini güncelle (grup üyeliklerinde de güncellenir)
     */
    suspend fun updateUserProfile(userId: String, newDisplayName: String): Result<Unit> {
        return try {
            println("DEBUG: updateUserProfile başladı - User: $userId, Yeni ad: $newDisplayName")

            // 1. Kullanıcının kendi profil bilgilerini güncelle
            val userRef = firestore.collection("users").document(userId)
            userRef.update("displayName", newDisplayName).await()

            // 2. Kullanıcının tüm grup üyeliklerinde adını güncelle
            val memberships = firestore.collection("group_members")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            // Her grup üyeliğini güncelle
            for (document in memberships.documents) {
                document.reference.update("userName", newDisplayName).await()
            }

            // 3. Chat mesajlarında da güncelle (opsiyonel)
            val chatMessages = firestore.collection("chat_messages")
                .whereEqualTo("senderId", userId)
                .get()
                .await()

            for (document in chatMessages.documents) {
                document.reference.update("senderName", newDisplayName).await()
            }

            println("DEBUG: ✅ Kullanıcı profili ve grup üyelikleri güncellendi")
            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: ❌ updateUserProfile hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Haftalık denetmen ataması kaydet
     */
    suspend fun saveWeeklyAuditor(weeklyAuditor: WeeklyAuditor): Result<Unit> {
        return try {
            val auditorData = hashMapOf<String, Any>(
                "id" to weeklyAuditor.id,
                "groupId" to weeklyAuditor.groupId,
                "weekDay" to weeklyAuditor.weekDay,
                "auditorId" to weeklyAuditor.auditorId,
                "auditorName" to weeklyAuditor.auditorName,
                "assignedBy" to weeklyAuditor.assignedBy,
                "assignedAt" to weeklyAuditor.assignedAt
            )

            firestore.collection("weekly_auditors")
                .document(weeklyAuditor.id)
                .set(auditorData)
                .await()

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
            val messageData = hashMapOf<String, Any>(
                "id" to message.id,
                "groupId" to message.groupId,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderAvatar" to message.senderAvatar,
                "message" to message.message,
                "messageType" to message.messageType,
                "createdAt" to message.createdAt
            )

            firestore.collection("chat_messages")
                .document(message.id)
                .set(messageData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: Chat mesajı kaydetme hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Üyeyi gruptan çıkar
     */
    suspend fun removeMemberFromGroup(membershipId: String, groupId: String): Result<Unit> {
        return try {
            println("DEBUG: removeMemberFromGroup başladı - Membership ID: $membershipId, Grup ID: $groupId")

            // 1. Üyelik kaydını sil
            firestore.collection("group_members")
                .document(membershipId)
                .delete()
                .await()

            println("DEBUG: ✅ Üyelik kaydı silindi")

            // 2. Grup üye sayısını güncelle (opsiyonel)
            val groupRef = firestore.collection("groups").document(groupId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(groupRef)
                val currentCount = snapshot.getLong("memberCount") ?: 1
                transaction.update(groupRef, "memberCount", maxOf(currentCount - 1, 0))
            }.await()

            println("DEBUG: ✅ Grup üye sayısı güncellendi")

            Result.success(Unit)
        } catch (e: Exception) {
            println("DEBUG: ❌ removeMemberFromGroup hatası: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Grup chat mesajlarını getir
     */
    suspend fun getChatMessages(groupId: String, limit: Int = 50): Result<List<ChatMessage>> {
        return try {
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
            }.reversed()

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