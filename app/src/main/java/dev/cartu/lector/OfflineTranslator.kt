package dev.cartu.lector

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.fragment.app.FragmentActivity

internal fun FragmentActivity.launchOfflineTranslator(text: String): Boolean {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .setPackage(PACKAGE_NAME)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private const val PACKAGE_NAME = "dev.davidv.translator"
