package com.neilturner.perfview.data.adb

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
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
        private const val KEY_ALIAS = "perfview"
        private const val CERT_FILE_NAME = "perfview-cert.pem"
        private const val KEY_FILE_NAME = "perfview-private.key"
        private const val CERT_VALIDITY_DAYS = 365 * 20

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
            val privateKey = readPrivateKeyFromFile(context)
            val publicKey = readPublicKeyFromFile(context)

            return if (privateKey != null && publicKey != null) {
                Log.d(TAG, "Loaded existing RSA key pair from files")
                KeyPairData(publicKey, privateKey)
            } else {
                Log.i(TAG, "Generating new RSA key pair...")
                val keyPairGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
                keyPairGenerator.initialize(2048, SecureRandom())
                val keyPair = keyPairGenerator.generateKeyPair()

                writePrivateKeyToFile(context, keyPair.private)
                writePublicKeyToFile(context, keyPair.public)

                KeyPairData(keyPair.public, keyPair.private)
            }
        }

        private fun loadOrGenerateCertificate(
            context: Context,
            publicKey: PublicKey,
            privateKey: PrivateKey
        ): Certificate {
            val storedCert = readCertificateFromFile(context)

            return storedCert ?: run {
                Log.i(TAG, "Generating new certificate...")
                val certificate = generateCertificate(publicKey, privateKey)
                writeCertificateToFile(context, certificate)
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
            
            val certBuilder = X509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.currentTimeMillis()),
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

        @Throws(IOException::class, java.security.cert.CertificateException::class)
        private fun readCertificateFromFile(context: Context): Certificate? {
            val certFile = File(context.filesDir, CERT_FILE_NAME)
            if (!certFile.exists()) return null

            FileInputStream(certFile).use { cert ->
                return CertificateFactory.getInstance("X.509").generateCertificate(cert)
            }
        }

        @Throws(IOException::class)
        private fun writeCertificateToFile(context: Context, certificate: Certificate) {
            val certFile = File(context.filesDir, CERT_FILE_NAME)
            Log.d(TAG, "Writing certificate to ${certFile.absolutePath}")

            FileOutputStream(certFile).use { os ->
                os.write("-----BEGIN CERTIFICATE-----\n".toByteArray(StandardCharsets.UTF_8))
                val encoded = Base64.encodeToString(certificate.encoded, Base64.DEFAULT)
                os.write(encoded.toByteArray(StandardCharsets.UTF_8))
                os.write("-----END CERTIFICATE-----\n".toByteArray(StandardCharsets.UTF_8))
            }
        }

        @Throws(IOException::class)
        private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
            val privateKeyFile = File(context.filesDir, KEY_FILE_NAME)
            if (!privateKeyFile.exists()) return null

            val privateKeyBytes = ByteArray(privateKeyFile.length().toInt())
            FileInputStream(privateKeyFile).use { `is` ->
                `is`.read(privateKeyBytes)
            }

            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            return keyFactory.generatePrivate(privateKeySpec)
        }

        @Throws(IOException::class)
        private fun readPublicKeyFromFile(context: Context): PublicKey? {
            val publicKeyFile = File(context.filesDir, "$KEY_FILE_NAME.pub")
            if (!publicKeyFile.exists()) return null

            val publicKeyBytes = ByteArray(publicKeyFile.length().toInt())
            FileInputStream(publicKeyFile).use { `is` ->
                `is`.read(publicKeyBytes)
            }

            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
            return keyFactory.generatePublic(publicKeySpec)
        }

        @Throws(IOException::class)
        private fun writePrivateKeyToFile(context: Context, privateKey: PrivateKey) {
            val privateKeyFile = File(context.filesDir, KEY_FILE_NAME)
            FileOutputStream(privateKeyFile).use { os ->
                os.write(privateKey.encoded)
            }
        }

        @Throws(IOException::class)
        private fun writePublicKeyToFile(context: Context, publicKey: PublicKey) {
            val publicKeyFile = File(context.filesDir, "$KEY_FILE_NAME.pub")
            FileOutputStream(publicKeyFile).use { os ->
                os.write(publicKey.encoded)
            }
        }

        private data class KeyPairData(
            val public: PublicKey,
            val private: PrivateKey
        )
    }
}
