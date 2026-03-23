package com.neilturner.perfview.data.adb

import android.content.Context
import android.os.Build
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.X509Factory
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.spec.EncodedKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import javax.net.ssl.SSLContext

class PerfViewAdbConnectionManager private constructor(context: Context) :
	AbsAdbConnectionManager() {
	private val privateKey: PrivateKey?
	private val certificate: Certificate?
	private val sslContext: SSLContext

	init {
		setApi(Build.VERSION.SDK_INT)
		var storedPrivateKey: PrivateKey? = readPrivateKeyFromFile(context)
		var storedCertificate: Certificate? = readCertificateFromFile(context)

		if (storedPrivateKey == null || storedCertificate == null) {
			val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
			keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
			val generatedKeyPair = keyPairGenerator.generateKeyPair()

			storedPrivateKey = generatedKeyPair.getPrivate()
			storedCertificate = generateCertificate(generatedKeyPair.getPublic(), storedPrivateKey)

			writePrivateKeyToFile(context, storedPrivateKey)
			writeCertificateToFile(context, storedCertificate)
		}

		privateKey = storedPrivateKey
		certificate = storedCertificate

		sslContext = SSLContext.getInstance("TLS")
		sslContext.init(null, null, SecureRandom())
	}

	override fun getPrivateKey(): PrivateKey {
		return privateKey!!
	}

	override fun getCertificate(): Certificate {
		return certificate!!
	}

	override fun getDeviceName(): String {
		return "PerfView"
	}

	fun getSslContext(): SSLContext {
		return sslContext
	}

	companion object {
		private var INSTANCE: PerfViewAdbConnectionManager? = null

		@Synchronized
		@Throws(Exception::class)
		fun getInstance(context: Context): PerfViewAdbConnectionManager {
			if (INSTANCE == null) {
				INSTANCE = PerfViewAdbConnectionManager(context.getApplicationContext())
			}
			return INSTANCE!!
		}

		@Throws(Exception::class)
		private fun generateCertificate(publicKey: PublicKey, privateKey: PrivateKey): Certificate {
			val subject = "CN=PerfView"
			val algorithmName = "SHA512withRSA"
			val now = System.currentTimeMillis()
			val expiryDate = now + 630720000000L
			val certificateExtensions = CertificateExtensions()
			certificateExtensions.set(
				"SubjectKeyIdentifier", SubjectKeyIdentifierExtension(
					KeyIdentifier(publicKey).getIdentifier()
				)
			)
			val x500Name = X500Name(subject)
			val notBefore = Date(now - 86400000L)
			val notAfter = Date(expiryDate)
			certificateExtensions.set(
				"PrivateKeyUsage",
				PrivateKeyUsageExtension(notBefore, notAfter)
			)
			val certificateValidity = CertificateValidity(notBefore, notAfter)
			val x509CertInfo = X509CertInfo()
			x509CertInfo.set("version", CertificateVersion(2))
			x509CertInfo.set(
				"serialNumber",
				CertificateSerialNumber(Random().nextInt() and Int.Companion.MAX_VALUE)
			)
			x509CertInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
			x509CertInfo.set("subject", CertificateSubjectName(x500Name))
			x509CertInfo.set("key", CertificateX509Key(publicKey))
			x509CertInfo.set("validity", certificateValidity)
			x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
			x509CertInfo.set("extensions", certificateExtensions)
			val x509CertImpl = X509CertImpl(x509CertInfo)
			x509CertImpl.sign(privateKey, algorithmName)
			return x509CertImpl
		}

		@Throws(IOException::class, CertificateException::class)
		private fun readCertificateFromFile(context: Context): Certificate? {
			val certFile = File(context.getFilesDir(), "perfview-cert.pem")
			if (!certFile.exists()) return null
			FileInputStream(certFile).use { cert ->
				return CertificateFactory.getInstance("X.509").generateCertificate(cert)
			}
		}

		@Throws(CertificateEncodingException::class, IOException::class)
		private fun writeCertificateToFile(context: Context, certificate: Certificate) {
			val certFile = File(context.getFilesDir(), "perfview-cert.pem")
			val encoder = BASE64Encoder()
			FileOutputStream(certFile).use { os ->
				os.write(X509Factory.BEGIN_CERT.toByteArray(StandardCharsets.UTF_8))
				os.write('\n'.code)
				encoder.encode(certificate.getEncoded(), os)
				os.write('\n'.code)
				os.write(X509Factory.END_CERT.toByteArray(StandardCharsets.UTF_8))
			}
		}

		@Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
		private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
			val privateKeyFile = File(context.getFilesDir(), "perfview-private.key")
			if (!privateKeyFile.exists()) return null
			val privateKeyBytes = ByteArray(privateKeyFile.length().toInt())
			FileInputStream(privateKeyFile).use { `is` ->
				`is`.read(privateKeyBytes)
			}
			val keyFactory = KeyFactory.getInstance("RSA")
			val privateKeySpec: EncodedKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
			return keyFactory.generatePrivate(privateKeySpec)
		}

		@Throws(IOException::class)
		private fun writePrivateKeyToFile(context: Context, privateKey: PrivateKey) {
			val privateKeyFile = File(context.getFilesDir(), "perfview-private.key")
			FileOutputStream(privateKeyFile).use { os ->
				os.write(privateKey.getEncoded())
			}
		}
	}
}
