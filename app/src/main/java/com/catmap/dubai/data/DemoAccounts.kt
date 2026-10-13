package com.catmap.dubai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The demo accounts: "admin" and four users, for showing the app to an audience. They are created in
 * Firebase by tools/demo/create-demo-accounts.mjs (verified, with fixed ids and a custom claim) and
 * share one password, which is never in the repository. Sign in with the username "admin", then
 * switch between them in Settings.
 */
object DemoAccounts {
    const val DOMAIN = "demo.catmap.test"

    data class Account(val username: String, val displayName: String, val admin: Boolean = false) {
        val email: String get() = "$username@$DOMAIN"
    }

    val ADMIN = Account("admin", "CatMap Admin", admin = true)
    val USERS = listOf(
        Account("demo_ali", "Ali"),
        Account("demo_mohammad", "Mohammad"),
        Account("demo_anirudh", "Anirudh"),
        Account("demo_hassan", "Hassan"),
    )
    val ALL = listOf(ADMIN) + USERS

    /** The demo account for a typed username ("admin", "@demo_ali") or email address, if it is one. */
    fun find(input: String): Account? {
        val key = input.trim().removePrefix("@").lowercase()
        return ALL.firstOrNull { it.username == key || it.email == key }
    }

    fun isDemoEmail(email: String?): Boolean = email?.lowercase()?.endsWith("@$DOMAIN") == true
}

/**
 * Keeps the shared demo password on this device, encrypted with an Android Keystore key, so Settings
 * can switch accounts without asking again. It is saved only after a successful demo sign-in, never
 * leaves the device (backups are off for the whole app) and is wiped on an explicit log out.
 */
class DemoVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("demo_vault", Context.MODE_PRIVATE)
    private val _saved = MutableStateFlow(prefs.contains(KEY_DATA))
    /** True while a demo password is stored, which is what shows the account switcher. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(password: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val data = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            prefs.edit {
                putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                putString(KEY_DATA, Base64.encodeToString(data, Base64.NO_WRAP))
            }
            _saved.value = true
        }.onFailure { android.util.Log.w("CatMap", "Could not store the demo password", it) }
    }

    fun password(): String? = runCatching {
        val iv = Base64.decode(prefs.getString(KEY_IV, null) ?: return null, Base64.NO_WRAP)
        val data = Base64.decode(prefs.getString(KEY_DATA, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        prefs.edit { clear() }
        _saved.value = false
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "catmap_demo_vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
    }
}
