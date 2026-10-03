package jadx.gui.treemodel

import com.android.apksig.ApkVerifier
import jadx.api.ICodeInfo
import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.impl.SimpleCodeInfo
import jadx.gui.JadxWrapper
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.panel.HtmlPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.CertificateManager
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.zip.IZipEntry
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.security.cert.Certificate
import java.util.concurrent.ExecutionException
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

/**
 * APK 签名校验节点。
 *
 * **做什么**：使用 `apksig` 库在后台校验 APK 签名（v1/v2/v3/v3.1），
 * 并把证书信息、错误与警告渲染成 HTML 展示在内容面板中。
 *
 * **线程模型**：保持原 Swing 模型 —— [getCodeInfo] 在 UI 线程触发 [SwingWorker]，
 * 校验完成后在 [SwingWorker.done] 里刷新面板。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class ApkSignatureNode(private val openFile: File) : JNode() {

	private var content: ICodeInfo? = null

	@Volatile
	private var loadingStarted: Boolean = false

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = CERTIFICATE_ICON

	override fun makeString(): String = "APK signature"

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel {
		Companion.tabbedPane = tabbedPane
		return HtmlPanel(tabbedPane, this)
	}

	override fun getCodeInfo(): ICodeInfo {
		content?.let { return it }

		// 若尚未开始加载，则立即启动后台校验
		if (!loadingStarted) {
			loadingStarted = true
			SwingUtilities.invokeLater { ApkSignatureWorker(this).execute() }
		}

		return SimpleCodeInfo(StringEscapeUtils.escapeHtml4(NLS.str("apkSignature.loading")))
	}

	companion object {
		private const val serialVersionUID = -9121321926113143407L

		private val LOG: Logger = LoggerFactory.getLogger(ApkSignatureNode::class.java)

		private val CERTIFICATE_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/styleKeyPack")

		private var tabbedPane: TabbedPane? = null

		/**
		 * 从已加载资源中找出 APK 文件并构造签名节点；无 AndroidManifest.xml 时返回 `null`。
		 */
		@JvmStatic
		fun getApkSignature(wrapper: JadxWrapper): ApkSignatureNode? {
			// 只有存在 AndroidManifest.xml 时才显示 ApkSignature 节点；
			// 没有 manifest 时 Google 的 ApkVerifier 会拒绝工作。
			var apkFile: File? = null
			for (resFile in wrapper.getResources()) {
				if (resFile.getType() == ResourceType.MANIFEST) {
					val zipEntry: IZipEntry? = resFile.getZipEntry()
					if (zipEntry != null) {
						apkFile = zipEntry.getZipFile()
						break
					}
				}
			}
			if (apkFile == null) {
				return null
			}
			return ApkSignatureNode(apkFile)
		}

		/** 写入证书信息（头部/公钥/签名/指纹）。 */
		private fun writeCertificate(builder: StringEscapeUtils.Builder, cert: Certificate) {
			val certMgr = CertificateManager(cert)
			builder.append("<blockquote><pre>")
			builder.escape(certMgr.generateHeader())
			builder.append("</pre><pre>")
			builder.escape(certMgr.generatePublicKey())
			builder.append("</pre><pre>")
			builder.escape(certMgr.generateSignature())
			builder.append("</pre><pre>")
			builder.append(certMgr.generateFingerprint())
			builder.append("</pre></blockquote>")
		}

		private fun writeIssues(
			builder: StringEscapeUtils.Builder,
			issueType: String,
			issueList: List<ApkVerifier.IssueWithParams>,
		) {
			if (issueList.isEmpty()) {
				return
			}
			builder.append("<h3>")
			builder.escape(issueType)
			builder.append("</h3>")
			builder.append("<blockquote>")
			// 未受保护的 Zip 条目问题非常常见，单独处理
			val unprotIssues = issueList.filter { it.getIssue() == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
			if (unprotIssues.isNotEmpty()) {
				builder.append("<h4>")
				builder.escape(NLS.str("apkSignature.unprotectedEntry"))
				builder.append("</h4><blockquote>")
				for (issue in unprotIssues) {
					builder.escape(issue.getParams()[0] as String)
					builder.append("<br>")
				}
				builder.append("</blockquote>")
			}
			val remainingIssues = issueList.filter { it.getIssue() != ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
			if (remainingIssues.isNotEmpty()) {
				builder.append("<pre>\n")
				for (issue in remainingIssues) {
					builder.escape(issue.toString())
					builder.append("\n")
				}
				builder.append("</pre>\n")
			}
			builder.append("</blockquote>")
		}
	}

	private class ApkSignatureWorker(private val node: ApkSignatureNode) : SwingWorker<ICodeInfo, Void>() {

		override fun doInBackground(): ICodeInfo {
			LOG.debug("Starting APK signature verification for {}", node.openFile)
			val verifier = ApkVerifier.Builder(node.openFile).build()
			try {
				val result = verifier.verify()

				// 构造 HTML 内容
				val builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4)
				builder.append("<h1>APK signature verification result:</h1>")

				builder.append("<p><b>")
				if (result.isVerified()) {
					builder.escape(NLS.str("apkSignature.verificationSuccess"))
				} else {
					builder.escape(NLS.str("apkSignature.verificationFailed"))
				}
				builder.append("</b></p>")

				val err = NLS.str("apkSignature.errors")
				val warn = NLS.str("apkSignature.warnings")
				writeIssues(builder, err, result.getErrors())

				if (result.getV1SchemeSigners().isNotEmpty()) {
					addVerifyResult(builder, result.isVerifiedUsingV1Scheme(), 1)
					builder.append("<blockquote>")
					for (signer in result.getV1SchemeSigners()) {
						builder.append("<h3>")
						builder.escape(NLS.str("apkSignature.signer"))
						builder.append(" ")
						builder.escape(signer.getName())
						builder.append(" (")
						builder.escape(signer.getSignatureFileName())
						builder.append(")")
						builder.append("</h3>")
						writeCertificate(builder, signer.getCertificate())
						writeIssues(builder, err, signer.getErrors())
						writeIssues(builder, warn, signer.getWarnings())
					}
					builder.append("</blockquote>")
				}
				if (result.getV2SchemeSigners().isNotEmpty()) {
					addVerifyResult(builder, result.isVerifiedUsingV2Scheme(), 2)
					builder.append("<blockquote>")
					for (signer in result.getV2SchemeSigners()) {
						builder.append("<h3>")
						builder.escape(NLS.str("apkSignature.signer"))
						builder.append(" ")
						builder.append(Integer.toString(signer.getIndex() + 1))
						builder.append("</h3>")
						writeCertificate(builder, signer.getCertificate())
						writeIssues(builder, err, signer.getErrors())
						writeIssues(builder, warn, signer.getWarnings())
					}
					builder.append("</blockquote>")
				}
				if (result.getV3SchemeSigners().isNotEmpty()) {
					addVerifyResult(builder, result.isVerifiedUsingV3Scheme(), 3)
					builder.append("<blockquote>")
					for (signer in result.getV3SchemeSigners()) {
						builder.append("<h3>")
						builder.escape(NLS.str("apkSignature.signer"))
						builder.append(" ")
						builder.append(Integer.toString(signer.getIndex() + 1))
						builder.append("</h3>")
						writeCertificate(builder, signer.getCertificate())
						writeIssues(builder, err, signer.getErrors())
						writeIssues(builder, warn, signer.getWarnings())
					}
					builder.append("</blockquote>")
				}
				if (result.getV31SchemeSigners().isNotEmpty()) {
					addVerifyResult(builder, result.isVerifiedUsingV31Scheme(), 31)
					builder.append("<blockquote>")
					for (signer in result.getV31SchemeSigners()) {
						builder.append("<h3>")
						builder.escape(NLS.str("apkSignature.signer"))
						builder.append(" ")
						builder.append(Integer.toString(signer.getIndex() + 1))
						builder.append("</h3>")
						writeCertificate(builder, signer.getCertificate())
						writeIssues(builder, err, signer.getErrors())
						writeIssues(builder, warn, signer.getWarnings())
					}
					builder.append("</blockquote>")
				}
				writeIssues(builder, warn, result.getWarnings())

				return SimpleCodeInfo(builder.toString())
			} catch (e: Exception) {
				LOG.error("Failed to verify APK signature for {}", node.openFile, e)
				val builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4)
				builder.append("<h1>")
				builder.escape(NLS.str("apkSignature.exception"))
				builder.append("</h1><pre>")
				builder.escape(ExceptionUtils.getStackTrace(e))
				builder.append("</pre>")
				return SimpleCodeInfo(builder.toString())
			}
		}

		override fun done() {
			try {
				node.content = get()
				val tabbedPane = ApkSignatureNode.Companion.tabbedPane
				if (tabbedPane != null) {
					val panel = tabbedPane.getTabByNode(node)
					if (panel is HtmlPanel) {
						panel.loadContent(node)
					}
				} else {
					LOG.warn("Could not find TabbedPane to refresh ApkSignatureNode panel.")
				}
			} catch (e: Exception) {
				LOG.error("Error during APK signature verification SwingWorker", e)
				val builder = StringEscapeUtils.builder(StringEscapeUtils.ESCAPE_HTML4)
				builder.append("<h1>")
				builder.escape(NLS.str("apkSignature.exception"))
				builder.append("</h1><pre>")
				builder.escape(ExceptionUtils.getStackTrace(if (e is ExecutionException) e.cause else e))
				builder.append("</pre>")
				node.content = SimpleCodeInfo(builder.toString())

				val tabbedPane = ApkSignatureNode.Companion.tabbedPane
				if (tabbedPane != null) {
					val panel = tabbedPane.getTabByNode(node)
					if (panel is HtmlPanel) {
						panel.loadContent(node)
					}
				}
			} finally {
				node.loadingStarted = false
			}
		}

		companion object {
			private fun addVerifyResult(builder: StringEscapeUtils.Builder, verifyResult: Boolean, verNum: Int) {
				builder.append("<h2>")
				if (verifyResult) {
					builder.escape(NLS.str("apkSignature.signatureSuccess", verNum))
				} else {
					builder.escape(NLS.str("apkSignature.signatureFailed", verNum))
				}
				builder.append("</h2>\n")
			}
		}
	}
}
