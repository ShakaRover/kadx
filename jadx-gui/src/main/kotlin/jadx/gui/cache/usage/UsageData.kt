package jadx.gui.cache.usage

import jadx.api.plugins.input.data.IMethodRef
import jadx.api.usage.IUsageInfoData
import jadx.api.usage.IUsageInfoVisitor
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * usage 数据的内存视图：把磁盘 / 内存中的 [RawUsageData] 写回 AST 节点。
 *
 * **做什么**：实现 [IUsageInfoData]，在反编译流程中把“类依赖、类被使用、方法 / 字段
 * 被哪些方法使用”等结果设置到 [ClassNode] / [MethodNode] / [FieldNode] 上。
 *
 * **为什么用普通 class**：持有 [RootNode] 与原始数据，是有状态的数据应用器。
 */
internal class UsageData(
	private val root: RootNode,
	private val rawUsageData: RawUsageData,
) : IUsageInfoData {

	override fun apply() {
		val clsMap = rawUsageData.clsMap
		for (cls in root.getClasses()) {
			val clsRawName = cls.rawName
			val clsUsageData = clsMap[clsRawName]
			if (clsUsageData != null) {
				applyForClass(clsUsageData, cls)
			}
		}
	}

	override fun applyForClass(cls: ClassNode) {
		val clsRawName = cls.rawName
		val clsUsageData = rawUsageData.clsMap[clsRawName]
		if (clsUsageData == null) {
			LOG.debug("No usage data for class: {}", clsRawName)
			return
		}
		applyForClass(clsUsageData, cls)
	}

	private fun applyForClass(clsUsageData: ClsUsageData, cls: ClassNode) {
		cls.dependencies = resolveClsList(clsUsageData.clsDeps)
		cls.useIn = resolveClsList(clsUsageData.clsUsage)
		cls.useInMth = resolveMthList(clsUsageData.clsUseInMth)

		val mthUsage = clsUsageData.mthUsage
		for (mth in cls.methods) {
			val mthUsageData = mthUsage[mth.getMethodInfo().shortId]
			if (mthUsageData != null) {
				mth.setUseIn(resolveMthList(mthUsageData.usage))
				mth.setUsed(resolveMthList(mthUsageData.uses))
				mth.setUnresolvedUsed(resolveMthInfoList(mthUsageData.unresolvedUsage))
				mth.setCallsSelf(mthUsageData.callsSelf)
			}
		}
		val fldUsage = clsUsageData.fldUsage
		for (fld in cls.fields) {
			val fldUsageData = fldUsage[fld.getFieldInfo().shortId]
			if (fldUsageData != null) {
				fld.setUseIn(resolveMthList(fldUsageData.usage))
			}
		}
	}

	override fun visitUsageData(visitor: IUsageInfoVisitor): Unit = throw JadxRuntimeException("Not implemented")

	/**
	 * 类名列表 -> 类节点列表。
	 *
	 * 原 Java 允许列表元素为 `null`（解析不到的类），这里用未检查强转保留该语义。
	 */
	@Suppress("UNCHECKED_CAST")
	private fun resolveClsList(clsList: List<String>?): List<ClassNode> = Utils.collectionMap(clsList) { root.resolveRawClass(it) } as List<ClassNode>

	/** 方法引用列表 -> 方法节点列表（解析失败时 `resolveDirectMethod` 会抛异常）。 */
	private fun resolveMthList(mthRefList: List<MthRef>?): List<MethodNode> = Utils.collectionMap(mthRefList) { root.resolveDirectMethod(it.cls, it.shortId) }

	/** 未解析方法引用列表 -> 方法签名列表。 */
	private fun resolveMthInfoList(mthRefList: List<IMethodRef>?): List<MethodInfo> = Utils.collectionMap(mthRefList) { MethodInfo.fromRef(root, it) }

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UsageData::class.java)
	}
}
