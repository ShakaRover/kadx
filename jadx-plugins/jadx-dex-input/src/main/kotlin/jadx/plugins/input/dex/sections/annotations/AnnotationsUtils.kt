package jadx.plugins.input.dex.sections.annotations

import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import org.jetbrains.annotations.Nullable
import java.util.Collections

/**
 * 注解属性读取工具：按名称 + 期望类型从 [IAnnotation] 中安全取值。
 *
 * **背景**：[jadx.plugins.input.dex.sections.DexAnnotationsConvert] 把 DEX 注解转换为
 * Jadx 内部表示时，用本类读取 name/accessFlags/names 等约定属性；类型不匹配或注解缺失
 * 时返回默认值而非抛异常（注解数据可能不完整）。
 */
public class AnnotationsUtils {

	companion object {
		/**
		 * 取指定名称、指定类型的属性值，缺失或类型不符时返回 [defValue]。
		 */
		@JvmStatic
		public fun <T> getValue(ann: IAnnotation?, name: String, type: EncodedType, defValue: T): T {
			if (ann == null || ann.values.isEmpty()) {
				return defValue
			}
			val encodedValue = ann.values[name]
			if (encodedValue == null || encodedValue.type != type) {
				return defValue
			}
			@Suppress("UNCHECKED_CAST")
			return encodedValue.value as T
		}

		/**
		 * 取指定名称、指定类型的属性值，缺失或类型不符时返回 null。
		 */
		@Nullable
		@JvmStatic
		public fun getValue(ann: IAnnotation?, name: String, type: EncodedType): Any? {
			if (ann == null || ann.values.isEmpty()) {
				return null
			}
			val encodedValue = ann.values[name]
			if (encodedValue == null || encodedValue.type != type) {
				return null
			}
			return encodedValue.value
		}

		/**
		 * 取指定名称的 ENCODED_ARRAY 属性，缺失或类型不符时返回空列表。
		 */
		@JvmStatic
		public fun getArray(ann: IAnnotation?, name: String): List<EncodedValue> {
			if (ann == null || ann.values.isEmpty()) {
				return Collections.emptyList()
			}
			val encodedValue = ann.values[name]
			if (encodedValue == null || encodedValue.type != EncodedType.ENCODED_ARRAY) {
				return Collections.emptyList()
			}
			@Suppress("UNCHECKED_CAST")
			return encodedValue.value as List<EncodedValue>
		}
	}
}
