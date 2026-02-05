package com.neilturner.persistentlist.data

import android.util.Log
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.persistentlist.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface SmbRepository {
    fun listFiles(): Flow<String>
}

class SmbRepositoryImpl : SmbRepository {
    override fun listFiles(): Flow<String> = flow {
        val client = SMBClient()
        try {
            val (shareName, rootPath) = parseSambaPath(BuildConfig.SAMBA_SHARE)
            client.connect(BuildConfig.SAMBA_IP).use { connection ->
                val ac = AuthenticationContext(
                    BuildConfig.SAMBA_USERNAME,
                    BuildConfig.SAMBA_PASSWORD.toCharArray(),
                    ""
                )
                val session = connection.authenticate(ac)
                (session.connectShare(shareName) as DiskShare).use { share ->
                    emitRecursive(share, rootPath, shareName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            client.close()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<String>.emitRecursive(
        share: DiskShare,
        relativePath: String,
        shareName: String
    ) {
        try {
            for (f in share.list(relativePath)) {
                val fileName = f.fileName
                if (fileName == "." || fileName == ".." || fileName.startsWith(".")) continue

                val fullPath = if (relativePath.isEmpty()) fileName else "$relativePath\\$fileName"

                if (isDirectory(f)) {
                    emitRecursive(share, fullPath, shareName)
                } else {
                    if (SUPPORTED_EXTENSIONS.any { fileName.lowercase().endsWith(it) }) {
                        val uriPath = "$shareName/${fullPath.replace('\\', '/')}"
	                    Log.i("SmbRepository", "Emit")
                        emit("smb://${BuildConfig.SAMBA_IP}/$uriPath")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

	companion object {
		private val SUPPORTED_EXTENSIONS = setOf(
			".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp", ".gif",
			".mov", ".mp4", ".m4a"
		)
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
}


