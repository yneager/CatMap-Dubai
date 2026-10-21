package com.catmap.dubai.data

import java.io.File

/**
 * Best-effort, bounded cache for immutable image document IDs. Call from an IO dispatcher.
 * A photo's bytes never change once uploaded, so entries live long (each download is a billed Firestore read plus up to
 * 650 KB); reading an entry marks it recently used, so the size limit drops the photos nobody has looked at lately.
 */
internal class ImageDiskCache(
    private val directory: File,
    private val maxBytes: Long = 80L * 1024 * 1024,
    private val maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun file(key: String): File? =
        if (key.matches(Regex("[A-Za-z0-9_-]{1,128}"))) File(directory, key) else null

    @Synchronized fun read(key: String): ByteArray? {
        val target = file(key) ?: return null
        if (!target.isFile) return null
        if (clock() - target.lastModified() >= maxAgeMillis) {
            target.delete()
            return null
        }
        return runCatching {
            target.readBytes().takeIf { it.isNotEmpty() }?.also { target.setLastModified(clock()) }
        }.getOrElse {
            target.delete()
            null
        }
    }

    @Synchronized fun write(key: String, bytes: ByteArray) {
        val target = file(key) ?: return
        if (bytes.isEmpty() || bytes.size > maxBytes) return
        runCatching {
            directory.mkdirs()
            val temp = File.createTempFile("image-", ".tmp", directory)
            try {
                temp.writeBytes(bytes)
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) return@runCatching
                target.setLastModified(clock())
            } finally { temp.delete() }
            trim()
        }
    }

    @Synchronized fun remove(key: String) { file(key)?.delete() }

    @Synchronized fun clear() {
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (clock() - file.lastModified() >= maxAgeMillis || total > maxBytes) {
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }
}
