package kadx.core.dex.visitors

import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.types.ExceptionsAttr
import kadx.core.Consts
import kadx.core.clsp.ClspClass
import kadx.core.clsp.ClspMethod
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.MethodThrowsAttr
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.trycatch.CatchAttr
import kadx.core.dex.trycatch.ExceptionHandler
import kadx.core.dex.visitors.regions.RegionMakerVisitor
import kadx.core.dex.visitors.typeinference.TypeCompare
import kadx.core.dex.visitors.typeinference.TypeCompareEnum
import kadx.core.utils.exceptions.KadxException
import java.util.ArrayList
import java.util.HashSet

/**
 * 扫描方法体，收集可能抛出的受检异常，用于生成 `throws` 子句。
 *
 * **做什么**：遍历指令，遇到 `throw` 或调用了会抛异常的方法时，把异常类型加入
 * [MethodThrowsAttr]；同时合并父类/接口方法的声明（沿继承链向上查找）。
 * 最后剔除被更宽泛异常覆盖的类型（例如已有 `Exception` 就不需要 `IOException`）。
 *
 * **为什么在 [RegionMakerVisitor] 之后**：需要忽略 synchronized 区域内编译器生成的 throw。
 */
@KadxVisitor(
	name = "MethodThrowsVisitor",
	desc = "Scan methods to collect thrown exceptions",
	runAfter = [
		RegionMakerVisitor::class,
	],
)
class MethodThrowsVisitor : AbstractVisitor() {

	private enum class ExceptionType {
		THROWS_REQUIRED,
		RUNTIME,
		UNKNOWN_TYPE,
		NO_EXCEPTION,
	}

	private lateinit var root: RootNode

	@Throws(KadxException::class)
	override fun init(root: RootNode) {
		this.root = root
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		var attr = mth.get(AType.METHOD_THROWS)
		if (attr == null) {
			attr = MethodThrowsAttr(HashSet())
			mth.addAttr(attr)
		}
		if (!attr.isVisited()) {
			attr.setVisited(true)
			processInstructions(mth)
		}

		val invalid = ArrayList<ArgType>()
		val exceptions = mth.get(KadxAttrType.EXCEPTIONS)
		if (exceptions != null && exceptions.list.isNotEmpty()) {
			for (throwsTypeStr in exceptions.list) {
				val excType = ArgType.`object`(throwsTypeStr)
				if (validateException(excType) == ExceptionType.NO_EXCEPTION) {
					invalid.add(excType)
				} else {
					attr.list.add(excType.getObject())
				}
			}
		}
		if (invalid.isNotEmpty()) {
			mth.addWarnComment("Byte code manipulation detected: skipped illegal throws declarations: $invalid")
		}
		mergeExceptions(attr.list)
	}

	private fun mergeExceptions(excSet: MutableSet<String>) {
		if (excSet.contains(Consts.CLASS_EXCEPTION)) {
			excSet.removeIf { e -> e != Consts.CLASS_EXCEPTION }
			return
		}
		if (excSet.contains(Consts.CLASS_THROWABLE)) {
			excSet.removeIf { e -> e != Consts.CLASS_THROWABLE }
			return
		}
		val toRemove = ArrayList<String>()
		for (ex1 in excSet) {
			for (ex2 in excSet) {
				if (ex1 == ex2) {
					continue
				}
				if (isBaseException(ex1, ex2)) {
					toRemove.add(ex1)
				}
			}
		}
		toRemove.forEach { excSet.remove(it) }
	}

	private fun processInstructions(mth: MethodNode) {
		if (mth.isNoCode() || mth.basicBlocks == null) {
			return
		}
		try {
			blocks@ for (block in checkNotNull(mth.basicBlocks)) {
				// 跳过（例如 synchronized 区域的）throw 指令
				val skipExceptions = block.contains(AFlag.REMOVE) || block.contains(AFlag.DONT_GENERATE)
				val excludedExceptions = HashSet<String>()
				val catchAttr = block.get(AType.EXC_CATCH)
				if (catchAttr != null) {
					for (handler in catchAttr.handlers) {
						if (handler.isCatchAll()) {
							continue@blocks
						}
						excludedExceptions.add(handler.argType.toString())
					}
				}
				for (insn in block.instructions) {
					checkInsn(mth, insn, excludedExceptions, skipExceptions)
				}
			}
		} catch (e: Exception) {
			mth.addWarnComment("Failed to analyze thrown exceptions", e)
		}
	}

	@Throws(KadxException::class)
	private fun checkInsn(mth: MethodNode, insn: InsnNode, excludedExceptions: Set<String>, skipExceptions: Boolean) {
		if (!skipExceptions && insn.type == InsnType.THROW && !insn.contains(AFlag.DONT_GENERATE)) {
			val throwArg = insn.getArg(0)
			if (throwArg is RegisterArg) {
				val exceptionType = throwArg.getType()
				if (exceptionType == ArgType.THROWABLE) {
					val assignInsn = throwArg.assignInsn
					if (assignInsn != null &&
						assignInsn.type == InsnType.MOVE_EXCEPTION &&
						checkNotNull(assignInsn.result).contains(AFlag.CUSTOM_DECLARE)
					) {
						// 该变量来自 catch 语句，忽略对 Throwable 的 rethrow
						return
					}
				}
				visitThrows(mth, exceptionType, excludedExceptions)
			} else {
				if (throwArg is InsnWrapArg) {
					val exceptionType = throwArg.getType()
					visitThrows(mth, exceptionType, excludedExceptions)
				}
			}
			return
		}

		if (insn.type == InsnType.INVOKE) {
			val invokeNode = insn as InvokeNode
			val callMth = invokeNode.callMth
			val signature = callMth.makeSignature(true)
			val classInfo = callMth.declClass

			val classNode = root.resolveClass(classInfo)
			if (classNode != null) {
				val cMth = searchOverriddenMethod(classNode, callMth, signature)
				if (cMth == null) {
					return
				}
				visit(cMth)
				val cAttr = cMth.get(AType.METHOD_THROWS)
				val attr = mth.get(AType.METHOD_THROWS)
				if (attr != null && cAttr != null && cAttr.list.isNotEmpty()) {
					for (argTypeStr in cAttr.list) {
						visitThrows(mth, ArgType.`object`(argTypeStr), excludedExceptions)
					}
				}
			} else {
				val clsDetails = checkNotNull(root.getClsp()).getClsDetails(classInfo.type)
				if (clsDetails != null) {
					val cMth = searchOverriddenMethod(clsDetails, signature)
					if (cMth != null && cMth.throws.isNotEmpty()) {
						val attr = mth.get(AType.METHOD_THROWS)
						if (attr != null) {
							for (argType in cMth.throws) {
								visitThrows(mth, argType, excludedExceptions)
							}
						}
					}
				}
			}
		}
	}

	private fun visitThrows(mth: MethodNode, excType: ArgType, excludedExceptions: Set<String>) {
		if (excType.isTypeKnown() && isThrowsRequired(mth, excType)) {
			for (excludedException in excludedExceptions) {
				if (isBaseException(excType.getObject(), excludedException)) {
					return
				}
			}

			checkNotNull(mth.get(AType.METHOD_THROWS)).list.add(excType.getObject())
		}
	}

	private fun isThrowsRequired(mth: MethodNode, type: ArgType): Boolean {
		val result = validateException(type)
		if (result == ExceptionType.UNKNOWN_TYPE) {
			mth.addInfoComment("Thrown type has an unknown type hierarchy: $type")
			return true // 假定它会抛异常
		}
		return result == ExceptionType.THROWS_REQUIRED
	}

	private fun validateException(clsType: ArgType?): ExceptionType {
		if (clsType == null || clsType == ArgType.OBJECT) {
			return ExceptionType.NO_EXCEPTION
		}
		if (!clsType.isTypeKnown() || !checkNotNull(root.getClsp()).isClsKnown(clsType.getObject())) {
			return ExceptionType.UNKNOWN_TYPE
		}
		if (isImplements(clsType, ArgType.RUNTIME_EXCEPTION) || isImplements(clsType, ArgType.ERROR)) {
			return ExceptionType.RUNTIME
		}
		if (isImplements(clsType, ArgType.THROWABLE) || isImplements(clsType, ArgType.EXCEPTION)) {
			return ExceptionType.THROWS_REQUIRED
		}
		return ExceptionType.NO_EXCEPTION
	}

	/**
	 * @return `possibleParent` 是否是 `exception` 的异常父类
	 */
	private fun isBaseException(exception: String, possibleParent: String): Boolean {
		if (exception == possibleParent) {
			return true
		}
		return checkNotNull(root.getClsp()).isImplements(exception, possibleParent)
	}

	private fun isImplements(type: ArgType, baseType: ArgType): Boolean {
		if (type == baseType) {
			return true
		}
		return checkNotNull(root.getClsp()).isImplements(type.getObject(), baseType.getObject())
	}

	private fun searchOverriddenMethod(cls: ClassNode, mth: MethodInfo, signature: String): MethodNode? {
		// 用包含返回值的完整签名精确匹配，抵抗混淆（见测试 'TestOverrideWithSameName'）
		val shortId = mth.shortId
		for (supMth in cls.methods) {
			if (supMth.methodInfo.shortId == shortId) {
				return supMth
			}
		}
		// 用不含返回值、但返回值更宽泛的签名匹配
		for (supMth in cls.methods) {
			if (supMth.methodInfo.shortId.startsWith(signature) && !supMth.accessFlags.isStatic()) {
				val typeCompare: TypeCompare = cls.root().typeCompare
				val supRetType = supMth.methodInfo.returnType
				val mthRetType = mth.returnType
				val res = typeCompare.compareTypes(supRetType, mthRetType)
				if (res.isWider()) {
					return supMth
				}
			}
		}
		return null
	}

	private fun searchOverriddenMethod(clsDetails: ClspClass, signature: String): ClspMethod? {
		val methodsMap = clsDetails.methodsMap
		for ((mthShortId, value) in methodsMap) {
			// 不校验完整签名，classpath 方法可信（同一类中不会出现同签名方法）
			if (mthShortId.startsWith(signature)) {
				return value
			}
		}
		return null
	}
}
