package com.inkledger.app.data.backup

import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.data.db.CountDownEntity
import com.inkledger.app.data.db.GoalDepositEntity
import com.inkledger.app.data.db.GoalEntity
import com.inkledger.app.data.db.HabitCheckEntity
import com.inkledger.app.data.db.NoteEntity
import com.inkledger.app.data.db.TemplateEntity
import com.inkledger.app.data.db.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 换机/存档方案：全量数据 → JSON → AES-256-GCM 加密（PBKDF2 派生密钥）→ .inkbak 文件
 * 文件结构：MAGIC(7B "INKBAK1") + salt(16B) + iv(12B) + 密文
 */
object BackupManager {

    private val MAGIC = "INKBAK1".toByteArray()

    data class Bundle(
        val txs: List<TransactionEntity>,
        val categories: List<CategoryEntity>,
        val templates: List<TemplateEntity>,
        val goals: List<GoalEntity>,
        val deposits: List<GoalDepositEntity>,
        val checks: List<HabitCheckEntity>,
        val countdowns: List<CountDownEntity>,
        val notes: List<NoteEntity>,
        val media: List<MediaFile> = emptyList()   // 记事图片/倒数日背景/应用背景
    )

    data class MediaFile(val name: String, val data: String)  // data = Base64(文件字节)

    fun toJson(b: Bundle): String = JSONObject().apply {
        put("v", 1)
        put("transactions", JSONArray(b.txs.map {
            JSONObject()
                .put("a", it.amountMinor).put("t", it.type).put("c", it.categoryId)
                .put("n", it.note).put("ts", it.timestamp).put("ca", it.createdAt).put("s", it.source)
        }))
        put("categories", JSONArray(b.categories.map {
            JSONObject().put("id", it.id).put("n", it.name).put("e", it.emoji)
                .put("t", it.type).put("o", it.sortOrder)
        }))
        put("templates", JSONArray(b.templates.map {
            JSONObject().put("n", it.name).put("e", it.emoji).put("c", it.categoryId)
                .put("t", it.type).put("a", it.amountMinor).put("note", it.note)
                .put("u", it.useCount).put("ca", it.createdAt)
        }))
        put("goals", JSONArray(b.goals.map {
            JSONObject().put("id", it.id).put("k", it.kind).put("n", it.name).put("e", it.emoji)
                .put("ta", it.targetAmountMinor ?: JSONObject.NULL)
                .put("dl", it.deadlineEpochDay ?: JSONObject.NULL)
                .put("ar", it.archived).put("ca", it.createdAt)
        }))
        put("deposits", JSONArray(b.deposits.map {
            JSONObject().put("g", it.goalId).put("a", it.amountMinor).put("ts", it.timestamp)
        }))
        put("checks", JSONArray(b.checks.map {
            JSONObject().put("g", it.goalId).put("d", it.epochDay)
        }))
        put("countdowns", JSONArray(b.countdowns.map {
            JSONObject().put("id", it.id).put("n", it.name).put("e", it.emoji).put("d", it.targetEpochDay)
                .put("r", it.repeatYearly).put("p", it.pinned).put("bg", it.bgId)
                .put("bl", it.bgBlur).put("ca", it.createdAt)
        }))
        put("notes", JSONArray(b.notes.map {
            JSONObject().put("ti", it.title).put("b", it.body).put("p", it.pinned)
                .put("u", it.updatedAt).put("m", it.media).put("ca", it.createdAt)
        }))
        put("media", JSONArray(b.media.map {
            JSONObject().put("n", it.name).put("d", it.data)
        }))
    }.toString()

    fun fromJson(s: String): Bundle {
        val root = JSONObject(s)
        fun JSONArray.mapEach(f: (JSONObject) -> Unit) {
            for (i in 0 until length()) f(getJSONObject(i))
        }
        val txs = mutableListOf<TransactionEntity>()
        root.getJSONArray("transactions").mapEach {
            txs += TransactionEntity(
                amountMinor = it.getLong("a"), type = it.getInt("t"), categoryId = it.getLong("c"),
                note = it.optString("n"), timestamp = it.getLong("ts"),
                createdAt = it.getLong("ca"), source = it.optInt("s", 0)
            )
        }
        val cats = mutableListOf<CategoryEntity>()
        root.optJSONArray("categories")?.mapEach {
            cats += CategoryEntity(
                id = it.getLong("id"), name = it.getString("n"), emoji = it.getString("e"),
                type = it.getInt("t"), sortOrder = it.getInt("o")
            )
        }
        val templates = mutableListOf<TemplateEntity>()
        root.optJSONArray("templates")?.mapEach {
            templates += TemplateEntity(
                name = it.getString("n"), emoji = it.optString("e", "⭐"), categoryId = it.getLong("c"),
                type = it.optInt("t", 0), amountMinor = it.getLong("a"), note = it.optString("note"),
                useCount = it.optInt("u", 0), createdAt = it.optLong("ca", 0)
            )
        }
        val goals = mutableListOf<GoalEntity>()
        root.optJSONArray("goals")?.mapEach {
            goals += GoalEntity(
                id = it.getLong("id"), kind = it.getInt("k"), name = it.getString("n"),
                emoji = it.optString("e", "🎯"),
                targetAmountMinor = if (it.isNull("ta")) null else it.getLong("ta"),
                deadlineEpochDay = if (it.isNull("dl")) null else it.getLong("dl"),
                archived = it.optBoolean("ar", false), createdAt = it.optLong("ca", 0)
            )
        }
        val deposits = mutableListOf<GoalDepositEntity>()
        root.optJSONArray("deposits")?.mapEach {
            deposits += GoalDepositEntity(
                goalId = it.getLong("g"), amountMinor = it.getLong("a"), timestamp = it.getLong("ts")
            )
        }
        val checks = mutableListOf<HabitCheckEntity>()
        root.optJSONArray("checks")?.mapEach {
            checks += HabitCheckEntity(goalId = it.getLong("g"), epochDay = it.getLong("d"))
        }
        val countdowns = mutableListOf<CountDownEntity>()
        root.optJSONArray("countdowns")?.mapEach {
            countdowns += CountDownEntity(
                id = it.optLong("id", 0),
                name = it.getString("n"), emoji = it.optString("e", "⏳"),
                targetEpochDay = it.getLong("d"), repeatYearly = it.optBoolean("r", false),
                pinned = it.optBoolean("p", false), bgId = it.optInt("bg", 0),
                bgBlur = it.optInt("bl", 60),
                createdAt = it.optLong("ca", 0)
            )
        }
        val notes = mutableListOf<NoteEntity>()
        root.optJSONArray("notes")?.mapEach {
            notes += NoteEntity(
                title = it.optString("ti"), body = it.optString("b"),
                pinned = it.optBoolean("p", false), updatedAt = it.getLong("u"),
                media = it.optString("m"), createdAt = it.optLong("ca", 0)
            )
        }
        val media = mutableListOf<MediaFile>()
        root.optJSONArray("media")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                media += MediaFile(o.getString("n"), o.getString("d"))
            }
        }
        return Bundle(txs, cats, templates, goals, deposits, checks, countdowns, notes, media = media)
    }

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        return MAGIC + salt + iv + ct
    }

    /** 密码错误或文件损坏时返回 null */
    fun decrypt(data: ByteArray, password: String): ByteArray? {
        if (data.size < 7 + 16 + 12) return null
        if (!MAGIC.contentEquals(data.copyOfRange(0, 7))) return null
        val salt = data.copyOfRange(7, 23)
        val iv = data.copyOfRange(23, 35)
        val ct = data.copyOfRange(35, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
