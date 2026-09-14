// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import dev.bananz0.opentvbridge.core.Diagnostics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows what the accessibility service most recently saw and did.
 *
 * The failure this exists for is a silent one: a launcher changes its view ids
 * and nothing happens any more. Without this, a user can only report "it
 * stopped working"; with it, they can paste the raw text the launcher gave us.
 */
class DiagnosticsActivity : Activity() {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        findViewById<Button>(R.id.copy).setOnClickListener { copy() }
        findViewById<Button>(R.id.clear).setOnClickListener {
            Diagnostics.log.clear()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val text = Diagnostics.log.render(::formatTime)
        findViewById<TextView>(R.id.log).text =
            text.ifBlank { getString(R.string.diagnostics_empty) }
    }

    private fun copy() {
        val text = Diagnostics.log.render(::formatTime)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.diagnostics_nothing_to_copy, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.diagnostics_clipboard_label), text),
        )
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(timestampMs: Long): String = timeFormat.format(Date(timestampMs))
}
