package ir.naghshechi.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import ir.naghshechi.app.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val model: String = "gpt-4.1-mini", val haptics: Boolean = true, val sound: Boolean = false)

class Storage(private val context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "naghshechi.db").build()
    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        db.documents().read("state")?.let { AppJson.decodeFromString<AppState>(it) } ?: AppState()
    }
    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        db.documents().write(StoredDocument("state", AppJson.encodeToString(state)))
    }
    suspend fun preferences(): Preferences {
        val p = context.preferences.data.first()
        return Preferences(p[stringPreferencesKey("model")] ?: "gpt-4.1-mini",
            p[booleanPreferencesKey("haptics")] ?: true, p[booleanPreferencesKey("sound")] ?: false)
    }
    suspend fun preferences(p: Preferences) {
        context.preferences.edit {
            it[stringPreferencesKey("model")] = p.model
            it[booleanPreferencesKey("haptics")] = p.haptics
            it[booleanPreferencesKey("sound")] = p.sound
        }
    }
    fun catalogue(): List<Mission> = context.assets.open("missions.json").bufferedReader().use {
        AppJson.decodeFromString<List<Mission>>(it.readText())
    }
    fun close() = db.close()
}

class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("private_key", Context.MODE_PRIVATE)
    private val alias = "naghshechi.avalai.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun read(): String {
        val encoded = prefs.getString("value", null) ?: return ""
        return try {
            val parts = encoded.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { prefs.edit().remove("value").apply(); "" }
    }
    fun write(value: String) {
        if (value.isBlank()) { clear(); return }
        require(value.length <= 500)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("value", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    fun clear() { prefs.edit().clear().apply() }
}

object Backups {
    const val MAX_BYTES = 32 * 1024 * 1024
    fun export(state: AppState): String {
        val text=AppJson.encodeToString(state.copy(active=null))
        require(text.toByteArray().size<=MAX_BYTES){"فایل پشتیبان بیش از حد بزرگ است."}
        return text
    }
    fun decode(text: String): AppState {
        require(text.toByteArray().size <= MAX_BYTES) { "فایل پشتیبان بیش از حد بزرگ است." }
        val s = AppJson.decodeFromString<AppState>(text)
        require(s.schema == 1 && s.groups.size <= 100 && s.history.size <= 300 && s.generated.size <= 500)
        s.groups.forEach { g -> require(g.players.size <= 12 && g.name.length <= 40); g.players.forEach { require(it.name.length in 1..24) } }
        require(s.groups.map { it.id }.distinct().size == s.groups.size)
        s.history.forEach { session ->
            require(session.phase == Phase.RESULT && session.players.size in 4..12)
            val ids=session.players.map{it.id}.toSet()
            require(ids.size == session.players.size)
            require(session.players.all{it.name.length in 1..24})
            require(session.assignments.size==ids.size && session.assignments.map{it.playerId}.toSet()==ids)
            require(session.revealOrder.size==ids.size && session.revealOrder.toSet()==ids)
            require(session.guesses.all{it.by in ids && it.target in ids && it.by!=it.target && it.verdict!=Verdict.PENDING})
            require(session.guesses.map{it.by to it.target}.distinct().size==session.guesses.size)
            require(session.guesses.groupBy { it.by }.values.all { it.size <= GameRules.MAX_GUESSES })
            require(session.outcomes.keys==ids && session.outcomes.values.none{it==Outcome.PENDING})
            require(session.warnings.all{(id,count)->id in ids && count in 0..100})
            session.assignments.forEach{a->require(a.mission.title.length<=80 && a.mission.goal.length<=800)}
        }
        s.generated.forEach { require(it.title.length <= 80 && it.goal.length <= 800 && it.source == "ai") }
        return s.copy(active = null)
    }
}
