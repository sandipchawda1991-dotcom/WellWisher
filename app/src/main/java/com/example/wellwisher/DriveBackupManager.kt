package com.example.wellwisher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DriveBackupManager(private val context: Context) {

    private val TAG = "DriveBackup"
    private val BACKUP_FILE_NAME = "wellwisher_backup.json"
    private val FOLDER_NAME = "WellWisher"

    private fun getDriveService(): Drive? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccount = account.account
            Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("WellWisher").build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Drive service", e)
            null
        }
    }

    suspend fun backupToDrive(data: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext false
            val existingFileId = findBackupFile(driveService)
            val fileContent = com.google.api.client.http.ByteArrayContent(
                "application/json",
                data.toByteArray()
            )
            if (existingFileId != null) {
                driveService.files().update(existingFileId, null, fileContent).execute()
            } else {
                val metadata = File().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                driveService.files().create(metadata, fileContent).execute()
            }
            Log.d(TAG, "Backup successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            false
        }
    }

    suspend fun restoreFromDrive(): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService() ?: return@withContext null
            val fileId = findBackupFile(driveService) ?: return@withContext null
            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val data = outputStream.toString("UTF-8")
            Log.d(TAG, "Restore successful")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            null
        }
    }

    private fun findBackupFile(driveService: Drive): String? {
        return try {
            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME'")
                .setFields("files(id, name)")
                .execute()
            result.files?.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e(TAG, "Find file failed", e)
            null
        }
    }
}
