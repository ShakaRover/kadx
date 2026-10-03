package jadx.core.dex.visitors

import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.DotGraphUtils
import java.io.File
import java.util.regex.Matcher

/**
 * 控制流图（CFG）导出访问者：把方法的基本块/区域画成 Graphviz DOT 文件。
 *
 * **做什么**：提供若干静态工厂方法，分别导出普通 CFG、原始指令 CFG、按区域组织的 CFG；
 * 通过 [save] 可指定输出目录，[visit] 则输出到默认目录。
 *
 * **为什么**：这是纯调试工具，`Jadx.java` 在开启 `--cfg-vis` 等选项时会把它加入 Pass 列表。
 *
 * **Kotlin 转换说明**：私有构造器 + 静态工厂在 Kotlin 中用「私有主构造器 + 伴生对象工厂」
 * 实现；`@JvmStatic` 保证 Java 侧 `DotGraphVisitor.dump()` 等调用不变。
 */
class DotGraphVisitor private constructor(
	private val useRegions: Boolean,
	private val rawInsn: Boolean,
	private val highlightRegion: IRegion? = null,
) : AbstractVisitor() {

	override fun getName(): String = "DotGraphVisitor"

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		DotGraphUtils(useRegions, rawInsn, highlightRegion).dumpToFile(mth)
	}

	fun save(dir: File, mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		DotGraphUtils(useRegions, rawInsn, highlightRegion).dumpToFile(mth, dir)
	}

	companion object {
		private const val NL = "\\l"
		private val NLQR: String = Matcher.quoteReplacement(NL)
		private const val PRINT_DOMINATORS = false
		private const val PRINT_DOMINATORS_INFO = false

		@JvmStatic
		fun dump(): DotGraphVisitor = DotGraphVisitor(false, false)

		@JvmStatic
		fun dumpRaw(): DotGraphVisitor = DotGraphVisitor(false, true)

		@JvmStatic
		fun dumpRegions(): DotGraphVisitor = DotGraphVisitor(true, false)

		@JvmStatic
		fun dumpRawRegions(): DotGraphVisitor = DotGraphVisitor(true, true)

		/**
		 * 调试辅助：只绘制指定区域及其子区域的 CFG。
		 * 用法：`DotGraphVisitor.debugDumpWithRegionHighlight(region).visit(mth);`
		 */
		@JvmStatic
		fun debugDumpWithRegionHighlight(region: IRegion): DotGraphVisitor = DotGraphVisitor(false, false, region)
	}
}
