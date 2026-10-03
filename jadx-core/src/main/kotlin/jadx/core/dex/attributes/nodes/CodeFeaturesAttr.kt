package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.nodes.MethodNode
import java.util.EnumSet

/**
 * 方法代码特征属性：记录方法里出现了哪些特殊结构，供后续优化 Pass 快速判断。
 *
 * **为什么要预存特征？** 某些 Pass 需要遍历整段代码才能知道“有没有 switch/new-array”，
 * 提前在解析阶段打标记可以避免重复扫描（例如 [CodeFeature.SWITCH] 只在区域构建时用一次）。
 *
 * **Kotlin 转换说明**：静态方法 [contains] / [add] 放入 companion + [JvmStatic]，
 * Java 调用方仍写 `CodeFeaturesAttr.contains(...)`。
 */
class CodeFeaturesAttr : IJadxAttribute {

	/** 代码特征枚举 */
	enum class CodeFeature {
		/** 代码中包含 switch 指令 */
		SWITCH,

		/** 代码中包含 new-array 指令 */
		NEW_ARRAY,
	}

	companion object {
		/** 查询方法是否具有指定特征 */
		fun contains(mth: MethodNode, feature: CodeFeature): Boolean {
			val codeFeaturesAttr = mth.get(AType.METHOD_CODE_FEATURES) ?: return false
			return codeFeaturesAttr.codeFeatures.contains(feature)
		}

		/** 为方法添加一个代码特征（属性不存在时先创建） */
		fun add(mth: MethodNode, feature: CodeFeature) {
			var codeFeaturesAttr = mth.get(AType.METHOD_CODE_FEATURES)
			if (codeFeaturesAttr == null) {
				codeFeaturesAttr = CodeFeaturesAttr()
				mth.addAttr(codeFeaturesAttr)
			}
			codeFeaturesAttr.codeFeatures.add(feature)
		}
	}

	val codeFeatures: MutableSet<CodeFeature> = EnumSet.noneOf(CodeFeature::class.java)

	override val attrType: AType<CodeFeaturesAttr> get() = AType.METHOD_CODE_FEATURES

	override fun toAttrString(): String = "CodeFeatures{$codeFeatures}"

	override fun toString(): String = toAttrString()
}
