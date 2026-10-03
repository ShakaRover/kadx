package jadx.gui.settings.font

import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.fonts.inter.FlatInterFont
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont
import jadx.commons.app.JadxSystemInfo
import jadx.gui.settings.JadxSettingsData
import jadx.gui.utils.FontUtils
import jadx.gui.utils.UiUtils
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

	init {
		val defUiFont: Font
		val defCodeFont: Font
		if (JadxSystemInfo.IS_MAC) {
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
	fun bindData(data: JadxSettingsData) {
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

	fun getUiFontAdapter(): FontAdapter = uiFontAdapter

	fun getCodeFontAdapter(): FontAdapter = codeFontAdapter

	fun getSmaliFontAdapter(): FontAdapter = smaliFontAdapter

	companion object {
		init {
			if (JadxSystemInfo.IS_MAC) {
				// workaround: bundled fonts don't support CJK chars (and composite fonts?) on macOS
			} else {
				FlatInterFont.install()
				FlatJetBrainsMonoFont.install()
				FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY)
			}
		}
	}
}
