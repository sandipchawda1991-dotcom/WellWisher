package com.example.wellwisher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DriveBackupManager(private val context: Context) {

    private val TAG = "DriveBackup"
    private val BACKUP_FILE_NAME = "wellwisher_backup.json"

    suspend fun backupToDrive(data: String, accessToken: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Check if file exists first
                val fileId = findBackupFile(accessToken)

                if (fileId != null) {
                    // Update existing file
                    updateFile(fileId, data, accessToken)
                } else {
                    // Create new file
                    createFile(data, accessToken)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                false
            }
        }

    suspend fun restoreFromDrive(accessToken: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val fileId = findBackupFile(accessToken) ?: return@withContext null
                downloadFile(fileId, accessToken)
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                null
            }
        }

    private fun findBackupFile(accessToken: String): String? {
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$BACKUP_FILE_NAME'&fields=files(id,name)")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()

            val json = JSONObject(response)
            val files = json.getJSONArray("files")
            if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        } catch (e: Exception) {
            Log.e(TAG, "Find file failed", e)
            null
        }
    }

    private fun createFile(data: String, accessToken: String): Boolean {
        return try {
            // Step 1: Create metadata
            val metadataUrl = URL("https://www.googleapis.com/drive/v3/files")
            val metaConn = metadataUrl.openConnection() as HttpURLConnection
            metaConn.requestMethod = "POST"
            metaConn.setRequestProperty("Authorization", "Bearer $accessToken")
            metaConn.setRequestProperty("Content-Type", "application/json")
            metaConn.doOutput = true

            val metadata = JSONObject().apply {
                put("name", BACKUP_FILE_NAME)
                put("parents", org.json.JSONArray().apply { put("appDataFolder") })
            }

            val writer = OutputStreamWriter(metaConn.outputStream)
            writer.write(metadata.toString())
            writer.flush()
            writer.close()

            val reader = BufferedReader(InputStreamReader(metaConn.inputStream))
            val response = reader.readText()
            reader.close()

            val fileId = JSONObject(response).getString("id")

            // Step 2: Upload content
            uploadContent(fileId, data, accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Create file failed", e)
            false
        }
    }

    private fun updateFile(fileId: String, data: String, accessToken: String): Boolean {
        return uploadContent(fileId, data, accessToken)
    }

    private fun uploadContent(fileId: String, data: String, accessToken: String): Boolean {
        return try {
            val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(data)
            writer.flush()
            writer.close()

            conn.responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            false
        }
    }

    private fun downloadFile(fileId: String, accessToken: String): String? {
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val content = reader.readText()
            reader.close()
            content
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }
}
