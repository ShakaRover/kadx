package jadx.core.dex.nodes.utils

import jadx.core.clsp.ClspClass
import jadx.core.clsp.ClspMethod
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.BaseInvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils

/**
 * 方法相关的通用工具：解析调用目标、判断参数是否可跳过、收集重载方法、
 * 查找覆写链上的基类/桥接方法等。
 *
 * Kotlin 转换说明：本类只有普通业务方法（无 getter/静态方法），因此保持为函数；
 * 原 Java 的 `getMethodDetails()` 等名字不变，Java 调用方零改动。
 */
class MethodUtils(private val root: RootNode) {

	/** 获取调用指令对应的方法详情（优先使用指令上缓存的属性）。 */
	fun getMethodDetails(invokeNode: BaseInvokeNode): IMethodDetails? {
		val methodDetails = invokeNode.get(AType.METHOD_DETAILS)
		if (methodDetails != null) {
			return methodDetails
		}
		return getMethodDetails(invokeNode.callMth)
	}

	/** 解析方法详情：优先返回已加载的 [MethodNode]，否则回退到 classpath 信息。 */
	fun getMethodDetails(callMth: MethodInfo): IMethodDetails? {
		val mthNode = root.resolveMethod(callMth)
		if (mthNode != null) {
			return mthNode
		}
		return checkNotNull(root.getClsp()).getMethodDetails(callMth)
	}

	/** 解析调用目标为 [MethodNode]；不是应用内方法时返回 null。 */
	fun resolveMethod(invokeNode: BaseInvokeNode): MethodNode? {
		val methodDetails = getMethodDetails(invokeNode)
		if (methodDetails is MethodNode) {
			return methodDetails
		}
		return null
	}

	/** 判断调用指令的某个参数是否应被跳过（如 Kotlin 默认参数/合成参数）。 */
	fun isSkipArg(invokeNode: BaseInvokeNode, arg: InsnArg): Boolean {
		val mth = resolveMethod(invokeNode) ?: return false
		val skipArgsAttr = mth.get(AType.SKIP_MTH_ARGS) ?: return false
		val argIndex = invokeNode.getArgIndex(arg)
		return skipArgsAttr.isSkip(argIndex)
	}

	/**
	 * 在类层次结构中查找与 [mthInfo] 同名同参数个数的方法（即重载）。
	 *
	 * 注意 [startCls] 可能与 `mthInfo.getDeclClass()` 不同。
	 */
	fun isMethodArgsOverloaded(startCls: ArgType, mthInfo: MethodInfo): Boolean = processMethodArgsOverloaded(startCls, mthInfo, null)

	/** 收集类层次结构中所有重载方法。 */
	fun collectOverloadedMethods(startCls: ArgType, mthInfo: MethodInfo): List<IMethodDetails> {
		val list = ArrayList<IMethodDetails>()
		processMethodArgsOverloaded(startCls, mthInfo, list)
		return list
	}

	/** 获取调用方法的泛型返回类型；无泛型时返回 null。 */
	fun getMethodGenericReturnType(invokeNode: BaseInvokeNode): ArgType? {
		val methodDetails = getMethodDetails(invokeNode)
		if (methodDetails != null) {
			val returnType = methodDetails.getReturnType()
			if (returnType != null && returnType.containsGeneric()) {
				return returnType
			}
		}
		return null
	}

	/**
	 * 递归搜索类层次结构中的重载方法。
	 *
	 * @param collectedMths 非 null 时收集结果；为 null 时只判断是否存在（找到即返回 true）
	 */
	private fun processMethodArgsOverloaded(
		startCls: ArgType?,
		mthInfo: MethodInfo,
		collectedMths: MutableList<IMethodDetails>?,
	): Boolean {
		if (startCls == null || !startCls.isObject()) {
			return false
		}
		val isMthConstructor = mthInfo.isConstructor() || mthInfo.isClassInit()
		val classNode = root.resolveClass(startCls)
		if (classNode != null) {
			for (mth in classNode.methods) {
				if (mthInfo.isOverloadedBy(mth.getMethodInfo())) {
					if (collectedMths == null) {
						return true
					}
					collectedMths.add(mth)
				}
			}
			if (!isMthConstructor) {
				if (processMethodArgsOverloaded(classNode.superClass, mthInfo, collectedMths)) {
					if (collectedMths == null) {
						return true
					}
				}
				for (parentInterface in classNode.interfaces) {
					if (processMethodArgsOverloaded(parentInterface, mthInfo, collectedMths)) {
						if (collectedMths == null) {
							return true
						}
					}
				}
			}
		} else {
			val clsDetails = checkNotNull(root.getClsp()).getClsDetails(startCls)
			if (clsDetails == null) {
				// classpath 中没有该类的信息
				return false
			}
			for (clspMth in clsDetails.methodsMap.values) {
				if (mthInfo.isOverloadedBy(clspMth.getMethodInfo())) {
					if (collectedMths == null) {
						return true
					}
					collectedMths.add(clspMth)
				}
			}
			if (!isMthConstructor) {
				for (parent in clsDetails.parents.orEmpty()) {
					if (processMethodArgsOverloaded(parent, mthInfo, collectedMths)) {
						if (collectedMths == null) {
							return true
						}
					}
				}
			}
		}
		return false
	}

	/** 获取方法覆写链上的基类方法详情；无覆写信息时返回 null。 */
	fun getOverrideBaseMth(mth: MethodNode): IMethodDetails? {
		val overrideAttr = mth.get(AType.METHOD_OVERRIDE) ?: return null
		return Utils.getOne(overrideAttr.baseMethods)
	}

	/** 返回方法（或桥接方法）最初声明的类。 */
	fun getMethodOriginDeclClass(mth: MethodNode): ClassInfo {
		val baseMth = getOverrideBaseMth(mth)
		if (baseMth != null) {
			return baseMth.getMethodInfo().declClass
		}
		val bridgeAttr = mth.get(AType.BRIDGED_BY)
		if (bridgeAttr != null) {
			return getMethodOriginDeclClass(bridgeAttr.bridgeMth)
		}
		return mth.getMethodInfo().declClass
	}
}
