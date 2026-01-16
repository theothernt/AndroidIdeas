package com.neilturner.exifblur.data

import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.exifblur.BuildConfig
import java.util.EnumSet
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SambaImageProvider : ImageProvider {
    override suspend fun getImages(): List<Uri> = withContext(Dispatchers.IO) {
        val images = mutableListOf<Uri>()
        val client = SMBClient()
        try {
            val (shareName, rootPath) = parseSambaPath(BuildConfig.SAMBA_SHARE)
            client.connect(BuildConfig.SAMBA_IP).use { connection ->
                val ac = AuthenticationContext(BuildConfig.SAMBA_USERNAME, BuildConfig.SAMBA_PASSWORD.toCharArray(), "")
                val session = connection.authenticate(ac)
                (session.connectShare(shareName) as DiskShare).use { share ->
                    listImagesRecursive(share, rootPath, images, shareName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        images.shuffled()
    }

    private fun listImagesRecursive(share: DiskShare, relativePath: String, images: MutableList<Uri>, shareName: String) {
        try {
            for (f in share.list(relativePath)) {
                val fileName = f.fileName
                if (fileName == "." || fileName == ".." || fileName.startsWith(".")) continue

                val fullPath = if (relativePath.isEmpty()) fileName else "$relativePath\\$fileName"
                
                if (isDirectory(f)) {
                    listImagesRecursive(share, fullPath, images, shareName)
                } else {
                    if (SUPPORTED_EXTENSIONS.any { fileName.lowercase().endsWith(it) }) {
                        val uriPath = "$shareName/${fullPath.replace('\\', '/')}"
                        images.add("smb://${BuildConfig.SAMBA_IP}/$uriPath".toUri())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp", ".gif"
        )
    }

    private fun isDirectory(f: FileIdBothDirectoryInformation): Boolean {
        return (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
    }

    override suspend fun getExifMetadata(uri: Uri): ExifMetadata = withContext(Dispatchers.IO) {
        try {
            openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                val date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
                val latLng = exifInterface.latLong

                ExifMetadata(
                    date = date,
                    latitude = latLng?.get(0),
                    longitude = latLng?.get(1)
                )
            } ?: ExifMetadata()
        } catch (e: Exception) {
            e.printStackTrace()
            ExifMetadata()
        }
    }

    override suspend fun openInputStream(uri: Uri): java.io.InputStream? = withContext(Dispatchers.IO) {
        val totalStartTime = System.currentTimeMillis()
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            val connectStartTime = System.currentTimeMillis()
            val client = SMBClient()
            connection = client.connect(BuildConfig.SAMBA_IP)
            val ac = AuthenticationContext(BuildConfig.SAMBA_USERNAME, BuildConfig.SAMBA_PASSWORD.toCharArray(), "")
            session = connection.authenticate(ac)
            Log.d("SambaImageProvider", "SMB Connect & Auth took ${System.currentTimeMillis() - connectStartTime}ms")
            
            val openStartTime = System.currentTimeMillis()
            val pathSegments = uri.pathSegments
            if (pathSegments.isEmpty()) return@withContext null
            
            val shareName = pathSegments[0]
            val relativePath = pathSegments.drop(1).joinToString("\\")
            
            share = session.connectShare(shareName) as DiskShare
            val file = share.openFile(
                relativePath,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            Log.d("SambaImageProvider", "Share connect & File open took ${System.currentTimeMillis() - openStartTime}ms")
            
            val finalConnection = connection
            val finalSession = session
            val finalShare = share
            object : java.io.FilterInputStream(file.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        file.close()
                        finalShare.close()
                        finalSession.close()
                        finalConnection.close()
                        Log.d("SambaImageProvider", "Stream closed. Total open time: ${System.currentTimeMillis() - totalStartTime}ms")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SambaImageProvider", "Failed to open input stream for $uri after ${System.currentTimeMillis() - totalStartTime}ms", e)
            share?.close()
            session?.close()
            connection?.close()
            null
        }
    }

    private fun parseSambaPath(fullPath: String): Pair<String, String> {
        val normalizedPath = fullPath.trim('/').replace('\\', '/')
        if (normalizedPath.isEmpty()) return Pair("", "")
        val parts = normalizedPath.split('/')
        val shareName = parts.firstOrNull() ?: ""
        val relativePath = parts.drop(1).joinToString("\\")
        return Pair(shareName, relativePath)
    }
}