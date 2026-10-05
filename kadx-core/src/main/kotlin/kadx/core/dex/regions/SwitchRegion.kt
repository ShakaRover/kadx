package kadx.core.dex.regions

import kadx.api.ICodeWriter
import kadx.core.codegen.RegionGen
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.IBranchRegion
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.CodegenException
import java.util.Collections

/**
 * `switch` 语句对应的区域。
 *
 * **结构**：一个 switch 由“头部块” [header]（执行 switch 指令的块）和若干
 * `case` 分支组成，每个分支由 [CaseInfo] 描述（一组 case 常量 + 对应的代码容器）。
 * 默认分支用特殊哨兵常量 [DEFAULT_CASE_KEY] 标记。
 *
 * 同样属于区域树节点，保持普通 class（身份语义）。
 */
class SwitchRegion(parent: IRegion?, val header: BlockNode) :
	AbstractRegion(parent),
	IBranchRegion {

	/** 所有 case 分支，按出现顺序保存 */
	val cases: MutableList<CaseInfo> = ArrayList()

	fun addCase(keysList: List<Any>, c: IContainer) {
		cases.add(CaseInfo(keysList, c))
	}

	/** 只取各 case 的代码容器（去掉 key） */
	val caseContainers: List<IContainer> get() = Utils.collectionMap(cases) { it.container }

	override val subBlocks: List<IContainer> get() {
		val all = ArrayList<IContainer>(cases.size + 1)
		all.add(header)
		for (caseInfo in cases) {
			all.add(caseInfo.container)
		}
		return Collections.unmodifiableList(all)
	}

	override val branches: List<IContainer?> get() = Collections.unmodifiableList(caseContainers)

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeSwitch(this, code)
	}

	override fun baseString(): String = "SW:" + header.baseString()

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("Switch: ").append(header.baseString())
		for (caseInfo in cases) {
			val keyStrings = Utils.collectionMap(caseInfo.keys) { k ->
				if (k === DEFAULT_CASE_KEY) "default" else k.toString()
			}
			sb.append("\n case ")
				.append(Utils.listToString(keyStrings))
				.append(" -> ").append(caseInfo.container)
		}
		return sb.toString()
	}

	/**
	 * 单个 case 分支：一组 case 常量 [keys] 和对应的代码容器 [container]。
	 *
	 * 注意：[keys] 是可变的（[kadx.core.dex.visitors.regions.SwitchOverStringVisitor]
	 * 会向其中追加常量），所以类型用 MutableList。
	 */
	class CaseInfo(val keys: List<Any>, val container: IContainer) {

		/** 判断是否为 default 分支：只有一个 key 且等于哨兵常量 */
		val isDefaultCase: Boolean get() = keys.size == 1 && keys[0] === DEFAULT_CASE_KEY
	}

	companion object {
		/**
		 * 默认分支的哨兵 key。用匿名对象而不是 null/字符串，保证与普通常量不会冲突，
		 * 且比较时用引用相等（[CaseInfo.isDefaultCase]）。
		 */
		val DEFAULT_CASE_KEY: Any = object : Any() {
			override fun toString(): String = "default"
		}
	}
}
