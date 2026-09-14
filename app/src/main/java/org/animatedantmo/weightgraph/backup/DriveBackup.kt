package org.animatedantmo.weightgraph.backup

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Drive v3 REST client: find or create the backup folder, upload a CSV into it, and
 * delete all but the newest backups.
 *
 * Plain HttpURLConnection rather than the Google API client library, which would add several
 * megabytes to the app for four HTTP calls.
 */
class DriveBackup(private val accessToken: String) {

    fun upload(fileName: String, csv: String, keep: Int) {
        val folderId = findFolder() ?: createFolder()
        uploadFile(folderId, fileName, csv)
        prune(folderId, keep)
    }

    private fun findFolder(): String? {
        val query = "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val url = "$API/files?spaces=drive&fields=files(id)&q=" + encode(query)
        val files = JSONObject(request("GET", url)).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createFolder(): String {
        val body = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()
        return JSONObject(request("POST", "$API/files?fields=id", body, "application/json"))
            .getString("id")
    }

    private fun uploadFile(folderId: String, fileName: String, csv: String) {
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", "text/csv")
            .put("parents", org.json.JSONArray().put(folderId))
            .toString()
        val body = buildString {
            append("--").append(BOUNDARY).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--").append(BOUNDARY).append("\r\n")
            append("Content-Type: text/csv; charset=UTF-8\r\n\r\n")
            append(csv).append("\r\n")
            append("--").append(BOUNDARY).append("--\r\n")
        }
        request(
            method = "POST",
            url = "$UPLOAD/files?uploadType=multipart&fields=id",
            body = body,
            contentType = "multipart/related; boundary=$BOUNDARY",
        )
    }

    // Newest first, so everything past the first `keep` is an older backup to remove.
    private fun prune(folderId: String, keep: Int) {
        val query = "'$folderId' in parents and trashed = false"
        val url = "$API/files?spaces=drive&orderBy=createdTime%20desc&pageSize=100" +
            "&fields=files(id)&q=" + encode(query)
        val files = JSONObject(request("GET", url)).getJSONArray("files")
        for (i in keep until files.length()) {
            request("DELETE", "$API/files/" + files.getJSONObject(i).getString("id"))
        }
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw DriveHttpException(code, detail)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val FOLDER_NAME = "Weight Graph Backups"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val BOUNDARY = "weightgraph-backup-boundary"
    }
}

class DriveHttpException(val code: Int, detail: String) :
    IOException("Google Drive returned HTTP $code" + if (detail.isBlank()) "" else ": $detail")
