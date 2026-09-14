// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * The licence notice the GPL expects a distributed binary to carry: what the
 * licence is, where the corresponding source is, the warranty disclaimer, and
 * the full licence text bundled in the APK itself so a recipient who only ever
 * has the binary still receives it.
 */
class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.source).text = getString(
            R.string.about_source,
            BuildConfig.SOURCE_URL,
            BuildConfig.VERSION_NAME,
        )

        findViewById<TextView>(R.id.licence).text = readLicence()
            ?: getString(R.string.about_licence_unavailable)
    }

    private fun readLicence(): String? = runCatching {
        resources.openRawResource(R.raw.agpl_3_0).use { it.readBytes().decodeToString() }
    }.getOrNull()
}
