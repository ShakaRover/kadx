package jadx.gui.ui.dialog

import jadx.api.JadxDecompiler
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * 「关于」对话框：展示 jadx 版本、Java 虚拟机与 Java 版本信息。
 *
 * **做什么**：构造时立即构建界面（[initUI]），点击关闭按钮即销毁窗口。
 *
 * **为什么保持 Swing 线程模型**：全部工作在 EDT 上完成。
 */
class AboutDialog : JDialog() {

	init {
		initUI()
	}

	/**
	 * 构建并布局对话框内容。
	 *
	 * 保留 `public` 可见性以兼容原 Java 调用方。
	 */
	fun initUI() {
		val logoURL = javaClass.getResource("/logos/jadx-logo-48px.png")
		val logo: Icon = ImageIcon(logoURL, "JADX logo")

		val name = JLabel("JADX", logo, SwingConstants.CENTER)
		name.setAlignmentX(0.5f)

		val desc = JLabel("Dex to Java decompiler")
		desc.setAlignmentX(0.5f)

		val version = JLabel("JADX version: " + JadxDecompiler.getVersion())
		version.setAlignmentX(0.5f)

		// 系统属性可能缺失，缺失时退化为空字符串（与原 Java 行为一致）
		val javaVm = System.getProperty("java.vm.name") ?: ""
		val javaVer = System.getProperty("java.version") ?: ""

		val javaVmLabel = JLabel("Java VM: $javaVm")
		javaVmLabel.setAlignmentX(0.5f)

		val javaVerLabel = JLabel("Java version: $javaVer")
		javaVerLabel.setAlignmentX(0.5f)

		val textPane = JPanel()
		textPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15))
		textPane.setLayout(BoxLayout(textPane, BoxLayout.PAGE_AXIS))
		textPane.add(Box.createRigidArea(Dimension(0, 10)))
		textPane.add(name)
		textPane.add(Box.createRigidArea(Dimension(0, 10)))
		textPane.add(desc)
		textPane.add(Box.createRigidArea(Dimension(0, 10)))
		textPane.add(version)
		textPane.add(Box.createRigidArea(Dimension(0, 20)))
		textPane.add(javaVmLabel)
		textPane.add(javaVerLabel)
		textPane.add(Box.createRigidArea(Dimension(0, 20)))

		val close = JButton(NLS.str("tabs.close"))
		close.addActionListener { dispose() }
		close.setAlignmentX(0.5f)

		val contentPane: Container = getContentPane()
		contentPane.add(textPane, BorderLayout.CENTER)
		contentPane.add(close, BorderLayout.PAGE_END)

		UiUtils.setWindowIcons(this)

		setModalityType(ModalityType.APPLICATION_MODAL)

		title = NLS.str("about_dialog.title")
		pack()
		setDefaultCloseOperation(DISPOSE_ON_CLOSE)
		setLocationRelativeTo(null)
	}

	companion object {
		private const val serialVersionUID = 5763493590584039096L
	}
}
