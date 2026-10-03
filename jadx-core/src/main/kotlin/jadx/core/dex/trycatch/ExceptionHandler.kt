package jadx.core.dex.trycatch

import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IRegion
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.InsnUtils
import jadx.core.utils.Utils
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Objects

/**
 * 单个异常处理器（catch 子句）的完整描述。
 *
 * **职责**：记录该处理器能捕获的异常类型、处理器入口块、覆盖的 try 体块集合、
 * 关联的 try 块（[TryCatchBlockAttr]）以及是否为 finally。
 *
 * **相等性**：按“处理器偏移量 + catch 类型列表 + 所属 try 块”判等（原 Java 手写实现），
 * 因此这里保持普通 class，不使用 data class。
 *
 * **Kotlin 转换说明**：所有 getter/setter 保持显式函数形式（大量 Kotlin 调用方以
 * `handler.getHandlerBlock()` 等形式调用），JVM 表面与 Java 完全一致。
 */
class ExceptionHandler private constructor(val handlerOffset: Int) {

	val catchTypes: MutableList<ClassInfo> = ArrayList(1)

	private var handlerBlock: BlockNode? = null
	val blocks: MutableList<BlockNode> = ArrayList()
	private var handlerRegion: IRegion? = null
	private var arg: InsnArg? = null

	private var tryBlock: TryCatchBlockAttr? = null
	private var isFinally: Boolean = false

	private var removed: Boolean = false

	/**
	 * 向处理器添加一个可捕获的异常类型。
	 *
	 * @param type null 表示“捕获全部/Throwable”处理器；此时会清空已有的具体类型
	 * @return 类型列表是否发生了变化
	 */
	fun addCatchType(mth: MethodNode, type: ClassInfo?): Boolean {
		if (type != null) {
			if (catchTypes.contains(type)) {
				return false
			}
			return catchTypes.add(type)
		}
		if (catchTypes.isNotEmpty()) {
			mth.addDebugComment("Throwable added to exception handler: '" + catchTypeStr() + "', keep only Throwable")
			catchTypes.clear()
			return true
		}
		return false
	}

	/** 批量添加可捕获的异常类型 */
	fun addCatchTypes(mth: MethodNode, types: Collection<ClassInfo>) {
		for (type in types) {
			addCatchType(mth, type)
		}
	}

	/**
	 * 返回该处理器用于接收异常对象的类型。
	 *
	 * - 捕获全部或捕获多种类型时统一用 `Throwable`；
	 * - 只捕获一种类型时用该类型。
	 */
	val argType: ArgType get() {
		if (isCatchAll()) {
			return ArgType.THROWABLE
		}
		val types = catchTypes
		return if (types.size == 1) {
			types.iterator().next().type
		} else {
			ArgType.THROWABLE
		}
	}

	/** 是否为“捕获全部”（catch 类型为空，或显式捕获 Throwable） */
	fun isCatchAll(): Boolean {
		if (catchTypes.isEmpty()) {
			return true
		}
		for (classInfo in catchTypes) {
			if (classInfo.fullName == Consts.CLASS_THROWABLE) {
				return true
			}
		}
		return false
	}

	fun getHandlerBlock(): BlockNode? = handlerBlock

	fun setHandlerBlock(handlerBlock: BlockNode?) {
		this.handlerBlock = handlerBlock
	}

	fun addBlock(node: BlockNode) {
		blocks.add(node)
	}

	fun getHandlerRegion(): IRegion? = handlerRegion

	fun setHandlerRegion(handlerRegion: IRegion?) {
		this.handlerRegion = handlerRegion
	}

	fun getArg(): InsnArg? = arg

	fun setArg(arg: InsnArg?) {
		this.arg = arg
	}

	fun setTryBlock(tryBlock: TryCatchBlockAttr) {
		this.tryBlock = tryBlock
	}

	fun getTryBlock(): TryCatchBlockAttr? = tryBlock

	fun isFinally(): Boolean = isFinally

	fun setFinally(isFinally: Boolean) {
		this.isFinally = isFinally
	}

	fun isRemoved(): Boolean = removed

	/**
	 * 查找 try 体底部与 catch 处理器交汇的“底部拆分块”。
	 *
	 * 目前不支持带有多个内层 try 的 catch（此时打印警告并返回 null）。
	 */
	@get:Nullable
	val bottomSplitter: BlockNode? get() {
		val handlerTryBlock = checkNotNull(getTryBlock())
		// TODO: Implement support for finding bottom splitter of catch with inner tries
		if (handlerTryBlock.getInnerTryBlocks().size > 1) {
			LOG.warn("No support yet for finding bottom block of try body with multipe inner trys")
			return null
		}
		val searchForTryBody: TryCatchBlockAttr? = if (handlerTryBlock.getInnerTryBlocks().isEmpty()) {
			handlerTryBlock
		} else {
			Utils.getOne(handlerTryBlock.getInnerTryBlocks())
		}

		var splitter: BlockNode? = null
		for (handlerPredecessor in checkNotNull(getHandlerBlock()).predecessors) {
			if (!handlerPredecessor.contains(AFlag.EXC_BOTTOM_SPLITTER)) {
				continue
			}

			for (splitterPredecessor in handlerPredecessor.predecessors) {
				val tryBody = splitterPredecessor.get(AType.TRY_BLOCK)
				if (tryBody === searchForTryBody) {
					splitter = handlerPredecessor
					break
				}
			}

			if (splitter != null) {
				break
			}
		}
		return splitter
	}

	/** 标记该处理器待删除，并给其所有块打上 REMOVE 标志 */
	fun markForRemove() {
		removed = true
		blocks.forEach { it.add(AFlag.REMOVE) }
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val that = other as ExceptionHandler
		return handlerOffset == that.handlerOffset &&
			catchTypes == that.catchTypes &&
			Objects.equals(tryBlock, that.tryBlock)
	}

	override fun hashCode(): Int = Objects.hash(catchTypes, handlerOffset)

	/** 类型的可读字符串：`all` 或 `A | B` 形式的短名 */
	fun catchTypeStr(): String = if (catchTypes.isEmpty()) "all" else Utils.listToString(catchTypes, " | ") { it.shortName }

	override fun toString(): String = catchTypeStr() + " -> " + InsnUtils.formatOffset(handlerOffset)

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ExceptionHandler::class.java)

		/**
		 * 构建异常处理器。
		 *
		 * @param mth  所属方法（用于在类型冲突时追加调试注释）
		 * @param addr 处理器入口地址
		 * @param type 捕获的异常类型；null 表示捕获全部（Throwable）
		 */
		fun build(mth: MethodNode, addr: Int, type: ClassInfo?): ExceptionHandler {
			val eh = ExceptionHandler(addr)
			eh.addCatchType(mth, type)
			return eh
		}
	}
}
