package jadx.core.dex.regions

import jadx.api.ICodeWriter
import jadx.core.codegen.RegionGen
import jadx.core.dex.nodes.IBranchRegion
import jadx.core.dex.nodes.IContainer
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.dex.trycatch.TryCatchBlockAttr
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.CodegenException
import java.util.Collections

/**
 * `try { ... } catch (...) { ... } finally { ... }` 对应的区域。
 *
 * **结构**：
 * - [tryRegion]：try 代码体；
 * - [catchRegions]：每个异常处理器（[ExceptionHandler]）到其 catch 代码体的映射；
 * - [finallyRegion]：finally 代码体（可空）；
 * - [tryCatchBlock]：原始 try/catch 属性（包含处理器列表等元信息）。
 *
 * 构建时先调用 [setTryCatchBlock]，它把处理器拆分成 catch 映射与 finally 区域。
 *
 * 同样属于区域树节点，保持普通 class（身份语义）。
 */
class TryCatchRegion(parent: IRegion?, val tryRegion: IContainer) :
	AbstractRegion(parent),
	IBranchRegion {

	private var catchRegionsValue: MutableMap<ExceptionHandler, IContainer> = Collections.emptyMap()
	private var finallyRegionValue: IContainer? = null
	private var tryCatchBlockValue: TryCatchBlockAttr? = null

	/**
	 * 根据 try/catch 属性填充 catch 映射与 finally 区域。
	 *
	 * 遍历所有异常处理器：标为 finally 的存入 [finallyRegion]，
	 * 其余按顺序放入 [catchRegions]（用 LinkedHashMap 保持声明顺序）。
	 */
	fun setTryCatchBlock(tryCatchBlock: TryCatchBlockAttr) {
		this.tryCatchBlockValue = tryCatchBlock
		val count = tryCatchBlock.handlersCount
		val regions = LinkedHashMap<ExceptionHandler, IContainer>(count)
		for (handler in tryCatchBlock.handlers) {
			val handlerRegion = handler.getHandlerRegion()
			if (handlerRegion != null) {
				if (handler.isFinally()) {
					finallyRegionValue = handlerRegion
				} else {
					regions[handler] = handlerRegion
				}
			}
		}
		this.catchRegionsValue = regions
	}

	val catchRegions: MutableMap<ExceptionHandler, IContainer> get() = catchRegionsValue

	val tryCatchBlock: TryCatchBlockAttr? get() = tryCatchBlockValue

	val finallyRegion: IContainer? get() = finallyRegionValue

	fun setFinallyRegion(finallyRegion: IContainer?) {
		this.finallyRegionValue = finallyRegion
	}

	override val subBlocks: List<IContainer> get() {
		val all = ArrayList<IContainer>(2 + catchRegionsValue.size)
		all.add(tryRegion)
		all.addAll(catchRegionsValue.values)
		val finallyRegion = this.finallyRegionValue
		if (finallyRegion != null) {
			all.add(finallyRegion)
		}
		return Collections.unmodifiableList(all)
	}

	override val branches: List<IContainer?> get() = subBlocks

	@Throws(CodegenException::class)
	override fun generate(regionGen: RegionGen, code: ICodeWriter) {
		regionGen.makeTryCatch(this, code)
	}

	override fun baseString(): String = tryRegion.baseString()

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("Try: ").append(tryRegion)
		if (catchRegionsValue.isNotEmpty()) {
			sb.append(" catches: ").append(Utils.listToString(catchRegionsValue.values))
		}
		val finallyRegion = this.finallyRegionValue
		if (finallyRegion != null) {
			sb.append(" finally: ").append(finallyRegion)
		}
		return sb.toString()
	}
}
