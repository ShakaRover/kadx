package kadx.gui.settings.font

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.fonts.inter.FlatInterFont
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont
import kadx.commons.app.KadxSystemInfo
import org.slf4j.LoggerFactory
import kadx.gui.settings.KadxSettingsData
import kadx.gui.utils.FontUtils
import kadx.gui.utils.UiUtils
import java.awt.Font
import javax.swing.JLabel
import javax.swing.UIManager

/**
 * 字体相关设置的统一入口。
 *
 * **做什么**：在启动时安装内置字体，维护 UI / 代码 / smali 三个 [FontAdapter]，
 * 并把 UI 缩放系数同步到这些字体上。
 *
 * **为什么静态初始化放在 companion object**：原 Java 的 `static {}` 块在类加载时
 * 安装 FlatLaf 字体；Kotlin 的 companion `init` 同样运行在 `<clinit>` 中，语义一致。
 */
class FontSettings {

	private val uiFontAdapter: FontAdapter
	private val codeFontAdapter: FontAdapter
	private val smaliFontAdapter: FontAdapter

	private var uiZoom: Float = 0f
	private var applyUiZoomToFonts: Boolean = false
	private var desktopScale: Float = 1.0f

	init {
		val defUiFont: Font
		val defCodeFont: Font
		if (KadxSystemInfo.IS_MAC) {
			val defaultFont = JLabel().font
			defUiFont = defaultFont
			defCodeFont = defaultFont
		} else {
			val defFontSize = 13
			defUiFont = FontUtils.getCompositeFont(FlatInterFont.FAMILY, Font.PLAIN, defFontSize)
			defCodeFont = FontUtils.getCompositeFont(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, defFontSize)
		}
		uiFontAdapter = FontAdapter(defUiFont)
		codeFontAdapter = FontAdapter(defCodeFont)
		smaliFontAdapter = FontAdapter(defCodeFont)
	}

	/**
	 * 把设置数据中的字体字符串与缩放参数绑定到各字体适配器。
	 */
	fun bindData(data: KadxSettingsData) {
		uiFontAdapter.bindData(data.uiFontStr) { data.uiFontStr = it }
		codeFontAdapter.bindData(data.codeFontStr) { data.codeFontStr = it }
		smaliFontAdapter.bindData(data.smaliFontStr) { data.smaliFontStr = it }
		applyUiZoom(data.uiZoom, data.applyUiZoomToFonts)
	}

	/**
	 * FlatLaf 初始化之后获取并应用默认字体。
	 */
	fun updateDefaultFont() {
		val defaultFont = UIManager.getFont("defaultFont")
		if (defaultFont != null) {
			uiFontAdapter.setDefaultFont(defaultFont)
		}
	}

	/**
	 * 应用 UI 缩放；缩放是否作用到字体由 [newApplyUiZoomToFonts] 控制。
	 *
	 * **线程模型**：保持原 Java 的 `synchronized` 语义，改用 K2 支持的 `@Synchronized` 注解。
	 */
	@Synchronized
	fun applyUiZoom(newUiZoom: Float, newApplyUiZoomToFonts: Boolean) {
		if (UiUtils.nearlyEqual(uiZoom, newUiZoom) && applyUiZoomToFonts == newApplyUiZoomToFonts) {
			return
		}
		uiZoom = newUiZoom
		applyUiZoomToFonts = newApplyUiZoomToFonts

		val effectiveFontZoom = if (newApplyUiZoomToFonts) newUiZoom else 1.0f
		uiFontAdapter.setUiZoom(effectiveFontZoom)
		codeFontAdapter.setUiZoom(effectiveFontZoom)
		smaliFontAdapter.setUiZoom(effectiveFontZoom)
	}

	/**
	 * 应用桌面缩放（Linux HiDPI）：把桌面缩放系数（如 Xft.dpi/96、GDK_SCALE）
	 * 乘进全部字体适配器。与用户 [uiZoom] 正交：最终字号 = 字号 × uiZoom × desktopScale。
	 *
	 * **为什么不走 FlatLaf 的 flatlaf.uiScale 属性**：实测该属性需在 FlatLaf UIScale
	 * 类初始化前设置，真实 GUI 启动时序下不生效；直接缩放字体适配器是确定性路径。
	 */
	private val LOG = LoggerFactory.getLogger(FontSettings::class.java)

	fun applyDesktopScale(scale: Float) {
		if (UiUtils.nearlyEqual(desktopScale, scale)) {
			return
		}
		desktopScale = scale
		uiFontAdapter.setDesktopScale(scale)
		codeFontAdapter.setDesktopScale(scale)
		smaliFontAdapter.setDesktopScale(scale)
		LOG.info("Desktop scale {} applied to fonts", scale)
	}

	fun getUiFontAdapter(): FontAdapter = uiFontAdapter

	fun getCodeFontAdapter(): FontAdapter = codeFontAdapter

	fun getSmaliFontAdapter(): FontAdapter = smaliFontAdapter

	companion object {
		/**
		 * 检测 Linux 桌面缩放系数：GDK_SCALE（×GDK_DPI_SCALE 补充倍率）优先，
		 * 其次 KDE/GNOME 的 Xft.dpi 字体缩放（xrdb）。可用 KADX_FORCE_XFT_DPI 覆盖。
		 * 非 Linux、未设置或 ≤1.05 时返回 1.0。
		 */
		fun detectDesktopScale(): Float {
			if (!KadxSystemInfo.IS_LINUX) {
				return 1.0f
			}
			val gdkScale = System.getenv("GDK_SCALE")?.toDoubleOrNull() ?: 0.0
			val gdkDpiScale = System.getenv("GDK_DPI_SCALE")?.toDoubleOrNull() ?: 0.0
			var scale = when {
				gdkScale >= 1.0 -> gdkScale * if (gdkDpiScale > 1.0) gdkDpiScale else 1.0
				gdkDpiScale > 1.0 -> gdkDpiScale
				else -> 0.0
			}
			if (scale <= 1.05) {
				val xftDpi = System.getenv("KADX_FORCE_XFT_DPI")?.toDoubleOrNull()
					?: runCatching {
						ProcessBuilder("xrdb", "-query").start().inputStream.bufferedReader().readLines()
							.firstOrNull { it.startsWith("Xft.dpi") }
							?.substringAfterLast('\t')?.trim()?.toDoubleOrNull()
					}.getOrNull() ?: 0.0
				if (xftDpi > 96.0) {
					scale = xftDpi / 96.0
				}
			}
			return if (scale > 1.05) scale.toFloat() else 1.0f
		}

		init {
			if (KadxSystemInfo.IS_MAC) {
				// workaround: bundled fonts don't support CJK chars (and composite fonts?) on macOS
			} else {
				FlatInterFont.install()
				FlatJetBrainsMonoFont.install()
				FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY)
			}
		}
	}
}
