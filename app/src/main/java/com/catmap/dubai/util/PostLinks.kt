package com.catmap.dubai.util

import java.net.URI

/** Reject encoded paths, extra segments, queries, fragments, ports and user-info. */
object PostLinks {
    private val idPattern = Regex("[A-Za-z0-9_-]{1,128}")
    fun create(id: String): String { require(idPattern.matches(id)); return "catmap://post/$id" }
    fun parse(value: String?): String? = runCatching {
        val uri = URI(value ?: return null)
        if(uri.scheme != "catmap" || uri.rawAuthority != "post" || uri.rawQuery != null || uri.rawFragment != null) return null
        uri.rawPath?.removePrefix("/")?.takeIf { idPattern.matches(it) && uri.rawPath == "/$it" }
    }.getOrNull()
}
