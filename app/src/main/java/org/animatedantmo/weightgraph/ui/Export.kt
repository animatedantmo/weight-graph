package org.animatedantmo.weightgraph.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

// Suggested filename, stamped so repeated exports do not overwrite each other in Downloads.
fun exportFileName(): String = "weights_" + LocalDate.now() + ".csv"

/**
 * Writes [csv] to the cache and hands it to the share sheet, which covers email, Drive, messaging
 * and anything else that accepts a file.
 *
 * The file goes in the cache rather than external storage so no permission is needed, and the URI
 * comes from FileProvider because sending a raw file:// URI throws on modern Android.
 */
fun shareCsv(context: Context, csv: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, exportFileName())
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Weight history")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share weight history"))
}
