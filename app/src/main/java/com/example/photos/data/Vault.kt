package com.example.photos.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateTaken: Long,
    val origPath: String,
    val addedAt: Long,
)

/**
 * Locked folder. Files are AES-256-GCM encrypted (chunked) with a key that lives in the
 * Android Keystore and never leaves the device. Access to the UI is gated by a separate
 * app password (PBKDF2 hash, with growing lockouts) and, optionally, biometrics.
 */
class Vault(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val dir = File(app.filesDir, "vault").apply { mkdirs() }
    private val viewDir = File(app.cacheDir, "vault_view").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    private val thumbCache = LruCache<String, Bitmap>(300)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private val _configured = MutableStateFlow(prefs.contains(P_HASH))
    val configured: StateFlow<Boolean> = _configured.asStateFlow()
    private val _biometric = MutableStateFlow(prefs.getBoolean(P_BIO, false))
    val biometricOn: StateFlow<Boolean> = _biometric.asStateFlow()
    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    val isConfigured: Boolean get() = _configured.value
    val isUnlocked: Boolean get() = _unlocked.value

    // ---------- password ----------

    fun setPassword(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(P_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(P_HASH, Base64.encodeToString(hash(pw, salt), Base64.NO_WRAP))
            .putInt(P_FAILS, 0).putLong(P_LOCK, 0L)
            .apply()
        _configured.value = true
    }

    /** Pure check, no lockout bookkeeping (used when changing the password). */
    fun checkPassword(pw: String): Boolean {
        val salt = prefs.getString(P_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val stored = prefs.getString(P_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return MessageDigest.isEqual(hash(pw, salt), stored)
    }

    /** Returns null on success, otherwise a message for the user. Blocking: call off the main thread. */
    fun tryUnlock(pw: String): String? {
        val now = System.currentTimeMillis()
        val lockUntil = prefs.getLong(P_LOCK, 0L)
        if (now < lockUntil) return "Too many attempts. Try again in ${(lockUntil - now) / 1000 + 1}s."
        if (checkPassword(pw)) {
            prefs.edit().putInt(P_FAILS, 0).putLong(P_LOCK, 0L).apply()
            _unlocked.value = true
            return null
        }
        val fails = prefs.getInt(P_FAILS, 0) + 1
        val lock = if (fails >= 5) 30_000L * (1L shl minOf(fails - 5, 6)) else 0L
        prefs.edit().putInt(P_FAILS, fails).putLong(P_LOCK, if (lock > 0) now + lock else 0L).apply()
        return if (lock > 0) "Too many attempts. Try again in ${lock / 1000}s."
        else "Wrong password. ${5 - fails} attempt(s) left before a delay."
    }

    /** Called after a successful biometric prompt or right after creating the password. */
    fun grantAccess() { _unlocked.value = true }

    fun setBiometric(on: Boolean) {
        prefs.edit().putBoolean(P_BIO, on).apply()
        _biometric.value = on
    }

    fun lock() {
        _unlocked.value = false
        _items.value = emptyList()
        thumbCache.evictAll()
        viewDir.listFiles()?.forEach { it.delete() }
    }

    private fun hash(pw: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pw.toCharArray(), salt, 150_000, 256)).encoded

    // ---------- index ----------

    private val indexFile get() = File(dir, "index.bin")

    suspend fun refreshItems() = withContext(Dispatchers.IO) { _items.value = readIndex() }

    private fun readIndex(): List<VaultItem> {
        val f = indexFile
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(String(decryptBytes(f.readBytes()), Charsets.UTF_8))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                VaultItem(
                    id = o.getString("id"), name = o.getString("name"), mime = o.getString("mime"),
                    isVideo = o.getBoolean("video"), size = o.getLong("size"),
                    width = o.getInt("w"), height = o.getInt("h"), durationMs = o.getLong("dur"),
                    dateTaken = o.getLong("taken"), origPath = o.getString("path"), addedAt = o.getLong("added"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(list: List<VaultItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime)
                    .put("video", it.isVideo).put("size", it.size).put("w", it.width).put("h", it.height)
                    .put("dur", it.durationMs).put("taken", it.dateTaken).put("path", it.origPath)
                    .put("added", it.addedAt),
            )
        }
        val tmp = File(dir, "index.tmp")
        tmp.writeBytes(encryptBytes(arr.toString().toByteArray(Charsets.UTF_8)))
        tmp.renameTo(indexFile)
    }

    // ---------- import / export ----------

    /** Encrypts copies of [items]. Returns the ones that succeeded with their vault ids. */
    suspend fun importAll(items: List<Media>): List<Pair<Media, String>> = withContext(Dispatchers.IO) {
        val done = ArrayList<Pair<Media, String>>()
        val added = ArrayList<VaultItem>()
        for (m in items) {
            val id = UUID.randomUUID().toString()
            val bin = File(dir, "$id.bin")
            val thumb = File(dir, "$id.thumb")
            try {
                val input = resolver.openInputStream(m.uri) ?: continue
                input.use { inp -> BufferedOutputStream(FileOutputStream(bin)).use { encryptStream(inp, it) } }
                runCatching { resolver.loadThumbnail(m.uri, Size(320, 320), null) }.getOrNull()?.let { bmp ->
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    thumb.writeBytes(encryptBytes(baos.toByteArray()))
                }
                added += VaultItem(
                    id, m.name, m.mime.ifBlank { if (m.isVideo) "video/mp4" else "image/jpeg" },
                    m.isVideo, m.size, m.width, m.height, m.durationMs, m.dateTaken, m.relativePath,
                    System.currentTimeMillis(),
                )
                done += m to id
            } catch (e: Exception) {
                bin.delete(); thumb.delete()
            }
        }
        if (added.isNotEmpty()) synchronized(this@Vault) {
            val all = readIndex() + added
            writeIndex(all)
            _items.value = all
        }
        done
    }

    suspend fun removeAll(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val drop = ids.toSet()
        synchronized(this@Vault) {
            drop.forEach { id ->
                File(dir, "$id.bin").delete()
                File(dir, "$id.thumb").delete()
                thumbCache.remove(id)
            }
            val all = readIndex().filterNot { it.id in drop }
            writeIndex(all)
            _items.value = all
        }
    }

    /** Decrypts back into the gallery (MediaStore), then removes it from the vault. */
    suspend fun restore(item: VaultItem): Boolean = withContext(Dispatchers.IO) {
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val allowed = if (item.isVideo) listOf("DCIM/", "Movies/", "Pictures/") else listOf("DCIM/", "Pictures/")
        val path = if (allowed.any { item.origPath.startsWith(it) }) item.origPath
        else (if (item.isVideo) "Movies/Restored/" else "Pictures/Restored/")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            if (item.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext false
        try {
            FileInputStream(File(dir, "${item.id}.bin")).buffered().use { inp ->
                resolver.openOutputStream(dest)?.use { out -> decryptStream(inp, out) } ?: error("no output")
            }
            resolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(dest, null, null) }
            return@withContext false
        }
        removeAll(listOf(item.id))
        true
    }

    // ---------- reading for the UI ----------

    suspend fun loadThumb(id: String): Bitmap? = withContext(Dispatchers.IO) {
        thumbCache.get(id) ?: runCatching {
            val bytes = decryptBytes(File(dir, "$id.thumb").readBytes())
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { thumbCache.put(id, it) }
    }

    /** Decrypts to a temporary file (wiped when the vault locks) so Coil/ExoPlayer can read it. */
    suspend fun decryptToCache(item: VaultItem): File? = withContext(Dispatchers.IO) {
        val ext = item.name.substringAfterLast('.', "bin")
        val out = File(viewDir, "${item.id}.$ext")
        if (out.exists() && out.length() > 0) return@withContext out
        try {
            FileInputStream(File(dir, "${item.id}.bin")).buffered().use { inp ->
                BufferedOutputStream(FileOutputStream(out)).use { decryptStream(inp, it) }
            }
            out
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    // ---------- crypto ----------

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun aad(index: Long): ByteArray = ByteBuffer.allocate(8).putLong(index).array()

    private fun readUpTo(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun encryptStream(input: InputStream, output: OutputStream) {
        val key = secretKey()
        val buf = ByteArray(CHUNK)
        val dos = DataOutputStream(output)
        var index = 0L
        while (true) {
            val n = readUpTo(input, buf)
            if (n <= 0) break
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key)
            c.updateAAD(aad(index))
            val ct = c.doFinal(buf, 0, n)
            dos.write(c.iv)
            dos.writeInt(ct.size)
            dos.write(ct)
            index++
        }
        dos.flush()
    }

    private fun decryptStream(input: InputStream, output: OutputStream) {
        val key = secretKey()
        val dis = DataInputStream(input)
        var index = 0L
        while (true) {
            val first = input.read()
            if (first < 0) break
            val iv = ByteArray(12)
            iv[0] = first.toByte()
            dis.readFully(iv, 1, 11)
            val ct = ByteArray(dis.readInt())
            dis.readFully(ct)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.updateAAD(aad(index))
            output.write(c.doFinal(ct))
            index++
        }
    }

    private fun encryptBytes(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        encryptStream(ByteArrayInputStream(bytes), out)
        return out.toByteArray()
    }

    private fun decryptBytes(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        decryptStream(ByteArrayInputStream(bytes), out)
        return out.toByteArray()
    }

    private companion object {
        const val KEY_ALIAS = "photos_vault_key"
        const val CHUNK = 256 * 1024
        const val P_SALT = "salt"
        const val P_HASH = "hash"
        const val P_FAILS = "fails"
        const val P_LOCK = "lock_until"
        const val P_BIO = "biometric"
    }
}
