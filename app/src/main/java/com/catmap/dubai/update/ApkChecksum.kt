package com.catmap.dubai.update

import java.io.File
import java.security.MessageDigest

object ApkChecksum {
    fun parse(text:String,assetName:String):String? {
        val match=Regex("^([A-Fa-f0-9]{64})(?:[ \\t]+\\*?([^/\\\\\\r\\n]+))?$").matchEntire(text.trim()) ?: return null
        val filename=match.groupValues[2]
        if(filename.isNotBlank() && filename!=assetName) return null
        return match.groupValues[1].lowercase()
    }
    fun digest(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(64*1024); while(true) {val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n)} }
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
    fun verify(file:File,sidecar:String,assetName:String):Boolean = parse(sidecar,assetName)?.let { expected ->
        MessageDigest.isEqual(expected.toByteArray(),digest(file).toByteArray())
    } ?: false
}
