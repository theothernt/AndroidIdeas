package com.neilturner.exifblur.data

import android.net.Uri
import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.exifblur.BuildConfig
import java.util.EnumSet
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SambaImageProvider : ImageProvider {
    /**
     * One SMBClient/connection/session/share is reused for every image read.
     * Reconnecting per image (per read) turned each load into a full
     * connect + authenticate + share-connect handshake, which dominates the
     * cost of loading an image over the network.
     */
    private val client = SMBClient()
    private var connection: Connection? = null
    private var share: DiskShare? = null
    private var connectedShareName: String? = null
    private val connectionLock = Any()

    override suspend fun getImages(): ImageListing = withContext(Dispatchers.IO) {
        if (BuildConfig.SAMBA_IP.isBlank() || BuildConfig.SAMBA_SHARE.isBlank()) {
            return@withContext ImageListing(error = "Samba share is not configured")
        }

        val images = mutableListOf<Uri>()
        try {
            val (shareName, rootPath) = parseSambaPath(BuildConfig.SAMBA_SHARE)
            val diskShare = ensureShare(shareName)
                ?: return@withContext ImageListing(error = "Samba unreachable: could not connect to ${BuildConfig.SAMBA_IP}")
            listImagesRecursive(diskShare, rootPath, images, shareName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list images from Samba", e)
            closeSession()
            return@withContext ImageListing(error = "Samba unreachable: ${e.message ?: "unknown error"}")
        }
        // Sort deterministically by URI string for consistent ordering across devices
        ImageListing(images.sortedBy { it.toString() })
    }

    override suspend fun openInputStream(uri: Uri): java.io.InputStream? = withContext(Dispatchers.IO) {
        openFileStream(uri) ?: run {
            // The cached session may have gone stale (server sleep, network change):
            // drop it and retry once on a fresh connection before giving up.
            closeSession()
            openFileStream(uri)
        }
    }

    private fun openFileStream(uri: Uri): java.io.InputStream? {
        return try {
            val pathSegments = uri.pathSegments
            if (pathSegments.isEmpty()) return null

            val shareName = pathSegments[0]
            val relativePath = pathSegments.drop(1).joinToString("\\")
            val diskShare = ensureShare(shareName) ?: return null

            val file = diskShare.openFile(
                relativePath,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            // Return a stream that closes the underlying file handle when done.
            // The cached connection/share are kept open for reuse.
            object : java.io.FilterInputStream(file.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        file.close()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for $uri", e)
            closeSession()
            null
        }
    }

    /**
     * Returns a connected share, creating the connection on first use or when
     * the cached session is gone/stale/for a different share.
     */
    private fun ensureShare(shareName: String): DiskShare? = synchronized(connectionLock) {
        val existing = share
        if (existing != null && connectedShareName == shareName && connection?.isConnected == true) {
            return@synchronized existing
        }
        closeSession()
        try {
            val newConnection = client.connect(BuildConfig.SAMBA_IP)
            val auth = AuthenticationContext(BuildConfig.SAMBA_USERNAME, BuildConfig.SAMBA_PASSWORD.toCharArray(), "")
            val session = newConnection.authenticate(auth)
            val newShare = session.connectShare(shareName) as DiskShare
            connection = newConnection
            share = newShare
            connectedShareName = shareName
            newShare
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMB session for share '$shareName'", e)
            closeSession()
            null
        }
    }

    private fun closeSession() = synchronized(connectionLock) {
        try {
            share?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing share", e)
        }
        share = null
        connectedShareName = null
        try {
            connection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing connection", e)
        }
        connection = null
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
            Log.e(TAG, "Failed to list directory '$relativePath'", e)
        }
    }

    private fun isDirectory(f: FileIdBothDirectoryInformation): Boolean {
        return (f.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
    }

    private fun parseSambaPath(fullPath: String): Pair<String, String> {
        val normalizedPath = fullPath.trim('/').replace('\\', '/')
        if (normalizedPath.isEmpty()) return Pair("", "")
        val parts = normalizedPath.split('/')
        val shareName = parts.firstOrNull() ?: ""
        val relativePath = parts.drop(1).joinToString("\\")
        return Pair(shareName, relativePath)
    }

    companion object {
        private const val TAG = "SambaImageProvider"
        private val SUPPORTED_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp", ".gif"
        )
    }
}
