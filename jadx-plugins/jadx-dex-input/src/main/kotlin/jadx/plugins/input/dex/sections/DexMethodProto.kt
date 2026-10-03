package jadx.plugins.input.dex.sections

import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.utils.Utils

/**
 * DEX 方法原型（proto_id）：描述一个方法的"签名形状"——参数类型列表 + 返回类型。
 *
 * **背景**：DEX 的 proto_ids section 中每个条目对应本类的一个实例；
 * [SectionReader.getMethodProto] 按索引读取并构造。
 *
 * **可空语义**（与原 Java 一致）：[returnType] 由 `getType(idx)` 填充，
 * 损坏 DEX 的 NO_INDEX 场景下可能为 null——此时调用 [getReturnType] 抛 NPE，
 * 与原 Java 返回 null 后由调用方解引用崩溃的行为等价。
 *
 * **等值语义**（与原 Java 一致）：equals/hashCode 基于"参数列表 + 返回类型"的值比较，
 * 且 equals 接受任意 [IMethodProto] 实现（而非仅限本类），便于跨输入格式比较签名。
 */
public class DexMethodProto(
	private val argTypesValue: List<String>,
	private val returnTypeValue: String?,
) : IMethodProto {

	override val argTypes: List<String> get() = argTypesValue

	override val returnType: String get() = checkNotNull(returnTypeValue) { "return type not set" }

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is IMethodProto) {
			return false
		}
		return argTypesValue == other.argTypes && returnTypeValue == other.returnType
	}

	override fun hashCode(): Int = 31 * argTypesValue.hashCode() + (returnTypeValue?.hashCode() ?: 0)

	override fun toString(): String = "(${Utils.listToStr(argTypes)})$returnTypeValue"
}
