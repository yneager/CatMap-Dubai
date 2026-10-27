package com.catmap.dubai.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** The privacy policy and terms of use, published with the web app (web/public) so Google Play can link to them too. */
object LegalLinks {
    const val PRIVACY = "https://catmap-dubai.web.app/privacy.html"
    const val TERMS = "https://catmap-dubai.web.app/terms.html"

    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
