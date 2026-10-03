package jadx.gui.cache.usage

import jadx.api.plugins.input.data.IMethodRef
import jadx.api.usage.IUsageInfoVisitor
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.utils.Utils

/**
 * usage 结果采集器：把 [IUsageInfoVisitor] 的回调写入 [RawUsageData]。
 *
 * **做什么**：jadx-core 完成 usage 分析后，会通过访问者模式逐类 / 逐方法回调；
 * 本类负责把这些结果转换成只含字符串 / 轻量引用的原始数据，便于后续序列化到磁盘。
 *
 * **为什么用普通 class**：持有待填充的 [data]，属于一次性采集器。
 */
internal class CollectUsageData(
	private val data: RawUsageData,
) : IUsageInfoVisitor {

	override fun visitClassDeps(cls: ClassNode, deps: List<ClassNode>) {
		data.getClassData(cls).clsDeps = clsNodesRef(deps)
	}

	override fun visitClassUsage(cls: ClassNode, usage: List<ClassNode>) {
		data.getClassData(cls).clsUsage = clsNodesRef(usage)
	}

	override fun visitClassUseInMethods(cls: ClassNode, methods: List<MethodNode>) {
		data.getClassData(cls).clsUseInMth = mthNodesRef(methods)
	}

	override fun visitFieldsUsage(fld: FieldNode, methods: List<MethodNode>) {
		data.getFieldData(fld).usage = mthNodesRef(methods)
	}

	override fun visitMethodsUsage(mth: MethodNode, methods: List<MethodNode>) {
		data.getMethodData(mth).usage = mthNodesRef(methods)
	}

	override fun visitMethodsUses(mth: MethodNode, methods: List<MethodNode>) {
		data.getMethodData(mth).uses = mthNodesRef(methods)
	}

	override fun visitUnresolvedMethodsUsage(mth: MethodNode, methods: List<MethodInfo>) {
		data.getMethodData(mth).unresolvedUsage = mthInfoRef(methods)
	}

	override fun visitIsSelfCall(mth: MethodNode, isSelfCall: Boolean) {
		data.getMethodData(mth).callsSelf = isSelfCall
	}

	override fun visitComplete() {
		data.collectClassesWithoutData()
	}

	/** 类节点列表 -> 类原始名列表。 */
	private fun clsNodesRef(usage: List<ClassNode>): List<String> = Utils.collectionMap(usage) { it.rawName }

	/** 方法节点列表 -> 方法引用列表。 */
	private fun mthNodesRef(methods: List<MethodNode>): List<MthRef> = Utils.collectionMap(methods) { data.getMethodData(it).mthRef }

	/** 方法签名列表 -> 方法引用列表（不可解析方法用 [CachedMethodRef] 承载签名）。 */
	private fun mthInfoRef(methods: List<MethodInfo>): List<IMethodRef> = Utils.collectionMap(methods) { CachedMethodRef(it) }
}
