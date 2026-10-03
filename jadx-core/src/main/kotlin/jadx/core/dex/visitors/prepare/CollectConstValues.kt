package jadx.core.dex.visitors.prepare

import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.usage.UsageInfoVisitor
import jadx.core.utils.exceptions.JadxException

/**
 * 收集 `static final` 字段的常量值，写入全局常量存储。
 *
 * **做什么**：遍历每个类的字段，取出带 `ConstantValue` 属性的静态 final 字段值，
 * 存进 [jadx.core.dex.info.ConstStorage]，供后续常量替换/内联使用。
 *
 * **为什么要先看使用信息**：如果字段仍被代码使用（编译器没有内联掉），
 * 说明它必须保留为字段，不能当成常量内联，因此本 Pass 在 [UsageInfoVisitor] 之后运行。
 *
 * **Kotlin 转换说明**：静态方法 [getFieldConstValue] 放 companion + `@JvmStatic`
 * （被 GUI 的 UsageDialog 等 Java 代码调用）；`EncodedValue.NULL` 用引用比较。
 */
@JadxVisitor(
	name = "CollectConstValues",
	desc = "Collect and store values from static final fields",
	runAfter = [
		UsageInfoVisitor::class, // 检查字段使用情况（被使用则不还原为常量）
	],
)
class CollectConstValues : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		val root = cls.root()
		if (!root.getArgs().isReplaceConsts) {
			return true
		}
		if (cls.fields.isEmpty()) {
			return true
		}
		val constStorage = root.getConstValues()
		for (fld in cls.fields) {
			try {
				val value = getFieldConstValue(fld)
				if (value != null) {
					constStorage.addConstField(fld, value, fld.accessFlags.isPublic())
				}
			} catch (e: Exception) {
				cls.addWarnComment("Failed to process value of field: $fld", e)
			}
		}
		return true
	}

	companion object {
		/** 提取静态 final 字段的常量值；不满足条件返回 null。 */
		fun getFieldConstValue(fld: FieldNode): Any? {
			val accFlags = fld.accessFlags
			if (!accFlags.isStatic() || !accFlags.isFinal()) {
				return null
			}
			val constVal = fld.get(JadxAttrType.CONSTANT_VALUE) ?: return null
			if (constVal === EncodedValue.NULL) {
				return null
			}
			if (fld.useIn.isNotEmpty()) {
				// 字段仍被使用且未被编译器内联，不需要还原为常量
				return null
			}
			return constVal.value
		}
	}
}
