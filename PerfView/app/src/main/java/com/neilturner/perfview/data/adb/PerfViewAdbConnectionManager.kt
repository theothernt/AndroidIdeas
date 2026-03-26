package com.neilturner.perfview.data.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date
import javax.net.ssl.SSLContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class PerfViewAdbConnectionManager private constructor(context: Context) :
    AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate
    private val sslContext: SSLContext

    init {
        Log.d(TAG, "Initializing ADB connection manager (API ${Build.VERSION.SDK_INT})")
        api = Build.VERSION.SDK_INT

        val keyPair = loadOrGenerateKeyPair(context)
        privateKey = keyPair.private
        certificate = loadOrGenerateCertificate(context, keyPair.public, keyPair.private)

        sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, SecureRandom())
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = "PerfView"

    companion object {
        private const val TAG = "PerfViewAdbConn"
        private const val CERT_FILE_NAME = "perfview-cert.der"
        private const val KEY_FILE_NAME = "perfview-private.key"
        private const val CERT_VALIDITY_DAYS = 365 * 20

        @Volatile
        private var INSTANCE: PerfViewAdbConnectionManager? = null

        @Synchronized
        @Throws(Exception::class)
        fun getInstance(context: Context): PerfViewAdbConnectionManager {
            if (INSTANCE == null) {
                INSTANCE = PerfViewAdbConnectionManager(context.applicationContext)
            }
            return INSTANCE!!
        }

        private fun loadOrGenerateKeyPair(context: Context): KeyPairData {
            val keyFile = File(context.filesDir, KEY_FILE_NAME)
            val pubKeyFile = File(context.filesDir, "$KEY_FILE_NAME.pub")

            // Diagnostic logging: file state at load time
            Log.d(TAG, "Key file: exists=${keyFile.exists()}, size=${keyFile.length()}, modified=${Date(keyFile.lastModified())}")
            Log.d(TAG, "PubKey file: exists=${pubKeyFile.exists()}, size=${pubKeyFile.length()}, modified=${Date(pubKeyFile.lastModified())}")

            val privateKey = readPrivateKeyFromFile(context)
            val publicKey = readPublicKeyFromFile(context)

            return if (privateKey != null && publicKey != null) {
                Log.d(TAG, "Loaded existing RSA key pair from files")
                Log.d(TAG, "Public key fingerprint: ${publicKey.getFingerprint()}")
                KeyPairData(publicKey, privateKey)
            } else {
                Log.i(TAG, "Generating new RSA key pair (previous files missing or corrupted)...")
                val keyPairGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
                keyPairGenerator.initialize(2048, SecureRandom())
                val keyPair = keyPairGenerator.generateKeyPair()

                writeAtomically(keyFile, keyPair.private.encoded)
                writeAtomically(pubKeyFile, keyPair.public.encoded)

                Log.d(TAG, "New public key fingerprint: ${keyPair.public.getFingerprint()}")
                KeyPairData(keyPair.public, keyPair.private)
            }
        }

        private fun PublicKey.getFingerprint(): String {
            return try {
                val md = MessageDigest.getInstance("SHA256")
                val digest = md.digest(this.encoded)
                digest.joinToString(":") { "%02x".format(it) }
            } catch (e: Exception) {
                "unknown"
            }
        }

        private fun loadOrGenerateCertificate(
            context: Context,
            publicKey: PublicKey,
            privateKey: PrivateKey
        ): Certificate {
            val certFile = File(context.filesDir, CERT_FILE_NAME)
            Log.d(TAG, "Cert file: exists=${certFile.exists()}, size=${certFile.length()}, modified=${Date(certFile.lastModified())}")

            val storedCert = readCertificateFromFile(context)

            return storedCert?.also {
                Log.d(TAG, "Loaded existing certificate from file")
                Log.d(TAG, "Cert public key fingerprint: ${it.publicKey.getFingerprint()}")
            } ?: run {
                Log.i(TAG, "Generating new certificate...")
                val certificate = generateCertificate(publicKey, privateKey)
                writeAtomically(certFile, certificate.encoded)
                Log.d(TAG, "Certificate written (${certificate.encoded.size} bytes)")
                certificate
            }
        }

        @Throws(Exception::class)
        private fun generateCertificate(
            publicKey: PublicKey,
            privateKey: PrivateKey
        ): Certificate {
            val subject = X500Name("CN=PerfView")
            val now = Date()
            val expiryDate = Date(now.time + CERT_VALIDITY_DAYS * 24 * 60 * 60 * 1000L)

            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.encoded)

            // Use a fixed serial number based on a hash of the public key
            // This ensures the same key always produces the same certificate identity
            val serialNumber = BigInteger.valueOf(publicKey.hashCode().toLong().and(Long.MAX_VALUE))

            val certBuilder = X509v3CertificateBuilder(
                subject,
                serialNumber,
                now,
                expiryDate,
                subject,
                publicKeyInfo
            )

            val signer: ContentSigner = JcaContentSignerBuilder("SHA512withRSA")
                .setProvider(BouncyCastleProvider())
                .build(privateKey)

            val certHolder: X509CertificateHolder = certBuilder.build(signer)
            return JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider())
                .getCertificate(certHolder)
        }

        private fun readCertificateFromFile(context: Context): Certificate? {
            val certFile = File(context.filesDir, CERT_FILE_NAME)
            if (!certFile.exists()) return null

            return try {
                val derBytes = certFile.readBytes()
                if (derBytes.isEmpty()) return null
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(derBytes.inputStream())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load certificate, deleting corrupted file", e)
                safeDelete(certFile)
                null
            }
        }

        private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
            val privateKeyFile = File(context.filesDir, KEY_FILE_NAME)
            if (!privateKeyFile.exists()) return null

            return try {
                val privateKeyBytes = privateKeyFile.readBytes()
                val keyFactory = KeyFactory.getInstance("RSA")
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load private key, deleting corrupted file", e)
                safeDelete(privateKeyFile)
                null
            }
        }

        private fun readPublicKeyFromFile(context: Context): PublicKey? {
            val publicKeyFile = File(context.filesDir, "$KEY_FILE_NAME.pub")
            if (!publicKeyFile.exists()) return null

            return try {
                val publicKeyBytes = publicKeyFile.readBytes()
                val keyFactory = KeyFactory.getInstance("RSA")
                keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load public key, deleting corrupted file", e)
                safeDelete(publicKeyFile)
                null
            }
        }

        /**
         * Writes data atomically using a temp file + rename to prevent
         * corruption if the process is killed mid-write.
         */
        @Throws(IOException::class)
        private fun writeAtomically(targetFile: File, data: ByteArray) {
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            try {
                tempFile.writeBytes(data)
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename ${tempFile.name} to ${targetFile.name}")
                }
            } catch (e: Exception) {
                safeDelete(tempFile)
                throw e
            }
        }

        private fun safeDelete(file: File) {
            try {
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "Failed to delete file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception while deleting file: ${file.name}", e)
            }
        }

        private data class KeyPairData(
            val public: PublicKey,
            val private: PrivateKey
        )
    }
}
