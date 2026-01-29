package com.neilturner.persistentlist.data

import android.net.Uri
import androidx.core.net.toUri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.neilturner.persistentlist.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SmbRepository {
    suspend fun listFiles(): List<Uri>
}

class SmbRepositoryImpl : SmbRepository {
	override suspend fun listFiles(): List<Uri> = withContext(Dispatchers.IO) {
		val media = mutableListOf<Uri>()
		val client = SMBClient()
		try {
			val (shareName, rootPath) = parseSambaPath(BuildConfig.SAMBA_SHARE)
			client.connect(BuildConfig.SAMBA_IP).use { connection ->
				val ac = AuthenticationContext(BuildConfig.SAMBA_USERNAME, BuildConfig.SAMBA_PASSWORD.toCharArray(), "")
				val session = connection.authenticate(ac)
				(session.connectShare(shareName) as DiskShare).use { share ->
					listImagesRecursive(share, rootPath, media, shareName)
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		media.shuffled()
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


