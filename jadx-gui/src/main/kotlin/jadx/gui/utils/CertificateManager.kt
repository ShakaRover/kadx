package jadx.gui.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Collections

/**
 * X.509 证书信息解析与展示。
 *
 * **做什么**：把签名文件中的证书解析成人类可读文本（类型、序列号、指纹、公钥等），
 * 用于 APK 签名信息面板。
 *
 * **为什么保持普通类**：Java 调用方用 `new CertificateManager(cert)` 构造。
 */
class CertificateManager(private val cert: Certificate) {

	/** 仅当证书类型为 X.509 时才非空。 */
	private var x509cert: X509Certificate? = null

	init {
		val type = cert.getType()
		if (type == CERTIFICATE_TYPE_NAME && cert is X509Certificate) {
			x509cert = cert
		}
	}

	fun generateHeader(): String {
		val x509 = checkNotNull(x509cert)
		val builder = StringBuilder()
		append(builder, NLS.str("certificate.cert_type"), x509.getType())
		append(builder, NLS.str("certificate.serialSigVer"), x509.getVersion().toString())
		// 序列号用十六进制展示
		append(builder, NLS.str("certificate.serialNumber"), "0x" + x509.getSerialNumber().toString(16))

		// 主体（Subject）
		val subjectDN = x509.getSubjectDN()
		append(builder, NLS.str("certificate.cert_subject"), subjectDN.getName())

		append(builder, NLS.str("certificate.serialValidFrom"), x509.getNotBefore().toString())
		append(builder, NLS.str("certificate.serialValidUntil"), x509.getNotAfter().toString())
		return builder.toString()
	}

	fun generateSignature(): String {
		val x509 = checkNotNull(x509cert)
		val builder = StringBuilder()
		append(builder, NLS.str("certificate.serialSigType"), x509.getSigAlgName())
		append(builder, NLS.str("certificate.serialSigOID"), x509.getSigAlgOID())
		return builder.toString()
	}

	fun generateFingerprint(): String {
		val x509 = checkNotNull(x509cert)
		val builder = StringBuilder()
		try {
			append(builder, NLS.str("certificate.serialMD5"), getThumbPrint(x509, "MD5"))
			append(builder, NLS.str("certificate.serialSHA1"), getThumbPrint(x509, "SHA-1"))
			append(builder, NLS.str("certificate.serialSHA256"), getThumbPrint(x509, "SHA-256"))
		} catch (e: Exception) {
			LOG.error("Failed to parse fingerprint", e)
		}
		return builder.toString()
	}

	/** 按公钥算法分派生成公钥描述。 */
	fun generatePublicKey(): String {
		val publicKey: PublicKey = checkNotNull(x509cert).getPublicKey()
		if (publicKey is RSAPublicKey) {
			return generateRSAPublicKey()
		}
		if (publicKey is DSAPublicKey) {
			return generateDSAPublicKey()
		}
		return ""
	}

	fun generateRSAPublicKey(): String {
		val pub = cert.getPublicKey() as RSAPublicKey
		val builder = StringBuilder()

		append(builder, NLS.str("certificate.serialPubKeyType"), pub.getAlgorithm())
		append(builder, NLS.str("certificate.serialPubKeyExponent"), pub.getPublicExponent().toString(10))
		append(builder, NLS.str("certificate.serialPubKeyModulusSize"), pub.getModulus().toString(2).length.toString())
		append(builder, NLS.str("certificate.serialPubKeyModulus"), pub.getModulus().toString(10))

		return builder.toString()
	}

	fun generateDSAPublicKey(): String {
		val pub = cert.getPublicKey() as DSAPublicKey
		val builder = StringBuilder()
		append(builder, NLS.str("certificate.serialPubKeyType"), pub.getAlgorithm())
		append(builder, NLS.str("certificate.serialPubKeyY"), pub.getY().toString(10))

		return builder.toString()
	}

	fun generateTextForX509(): String {
		val builder = StringBuilder()
		if (x509cert != null) {
			builder.append(generateHeader())
			builder.append('\n')

			builder.append(generatePublicKey())
			builder.append('\n')

			builder.append(generateSignature())
			builder.append('\n')
			builder.append(generateFingerprint())
		}
		return builder.toString()
	}

	fun generateText(): String {
		val str = StringBuilder()
		val type = cert.getType()
		if (type == CERTIFICATE_TYPE_NAME) {
			str.append(generateTextForX509())
		} else {
			str.append(cert.toString())
		}
		return str.toString()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CertificateManager::class.java)
		private const val CERTIFICATE_TYPE_NAME = "X.509"

		/** 解析输入流中的全部证书并生成展示文本。 */
		@JvmStatic
		fun decode(input: InputStream): String {
			val strBuild = StringBuilder()
			val certificates = readCertificates(input)
			for (cert in certificates) {
				val certificateManager = CertificateManager(cert)
				strBuild.append(certificateManager.generateText())
			}
			return strBuild.toString()
		}

		/** 读取输入流中的证书集合；失败时返回空集合。 */
		@JvmStatic
		fun readCertificates(input: InputStream): Collection<Certificate> = try {
			val cf = CertificateFactory.getInstance(CERTIFICATE_TYPE_NAME)
			cf.generateCertificates(input)
		} catch (e: Exception) {
			LOG.error("Certificate read error", e)
			Collections.emptyList<Certificate>()
		}

		@JvmStatic
		fun append(str: StringBuilder, name: String, value: String) {
			str.append(name).append(": ").append(value).append('\n')
		}

		/** 计算证书指纹（十六进制、空格分隔）。 */
		@JvmStatic
		@Throws(NoSuchAlgorithmException::class, CertificateEncodingException::class)
		fun getThumbPrint(cert: X509Certificate, type: String): String {
			val md = MessageDigest.getInstance(type)
			val der = cert.getEncoded()
			md.update(der)
			val digest = md.digest()
			return hexify(digest)
		}

		/** 把字节数组转成大写十六进制串（每个字节后跟一个空格）。 */
		@JvmStatic
		fun hexify(bytes: ByteArray): String {
			val hexDigits = charArrayOf(
				'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
			)
			val buf = StringBuilder(bytes.size * 3)
			for (aByte in bytes) {
				// Kotlin 的 Byte 不支持位运算，先转 Int
				val b = aByte.toInt()
				buf.append(hexDigits[(b and 0xf0) shr 4])
				buf.append(hexDigits[b and 0x0f])
				buf.append(' ')
			}
			return buf.toString()
		}
	}
}
