package kadx.api.plugins.input.data.attributes

import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.attributes.types.AnnotationDefaultAttr
import kadx.api.plugins.input.data.attributes.types.AnnotationDefaultClassAttr
import kadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import kadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import kadx.api.plugins.input.data.attributes.types.ExceptionsAttr
import kadx.api.plugins.input.data.attributes.types.InnerClassesAttr
import kadx.api.plugins.input.data.attributes.types.MethodParametersAttr
import kadx.api.plugins.input.data.attributes.types.SignatureAttr
import kadx.api.plugins.input.data.attributes.types.SourceFileAttr

/**
 * KADX 内置属性类型注册表。
 *
 * **设计目的**：集中定义所有内置属性的类型标记，类似枚举但使用对象实例。
 *
 * **为什么不用枚举？**
 * - 需要泛型类型安全：`ANNOTATION_LIST` 的类型是 `KadxAttrType<AnnotationsAttr>`
 * - 枚举无法表达这种精细的泛型关系
 *
 * **属性分类**：
 *
 * **类/方法/字段通用**：
 * - ANNOTATION_LIST：注解列表
 * - SIGNATURE：泛型签名
 *
 * **仅类**：
 * - SOURCE_FILE：源文件名
 * - INNER_CLASSES：内部类关系
 * - ANNOTATION_DEFAULT_CLASS：Dex 特有，注解默认值（类级别）
 *
 * **仅字段**：
 * - CONSTANT_VALUE：编译时常量值
 *
 * **仅方法**：
 * - ANNOTATION_MTH_PARAMETERS：参数注解
 * - ANNOTATION_DEFAULT：注解接口 default 方法
 * - EXCEPTIONS：throws 声明
 * - METHOD_PARAMETERS：参数名（DebugInfo）
 */
public class KadxAttrType<T : IKadxAttribute> : IKadxAttrType<T> {
	companion object {
		// ==================== 类/方法/字段通用 ====================
		/** 注解列表属性（@interface 上的所有注解）*/
		@JvmField
		public val ANNOTATION_LIST: KadxAttrType<AnnotationsAttr> = bind()

		/** 泛型签名属性（GenericSignature）*/
		@JvmField
		public val SIGNATURE: KadxAttrType<SignatureAttr> = bind()

		// ==================== 仅类 ====================

		/** 源文件名属性 */
		@JvmField
		public val SOURCE_FILE: KadxAttrType<SourceFileAttr> = bind()

		/** 内部类关系属性 */
		@JvmField
		public val INNER_CLASSES: KadxAttrType<InnerClassesAttr> = bind()

		/** 注解默认值（Dex 特有，类级别）*/
		@JvmField
		public val ANNOTATION_DEFAULT_CLASS: KadxAttrType<AnnotationDefaultClassAttr> = bind()

		// ==================== 仅字段 ====================

		/** 常量值属性（static final 字段的值）*/
		@JvmField
		public val CONSTANT_VALUE: KadxAttrType<EncodedValue> = bind()

		// ==================== 仅方法 ====================

		/** 参数注解属性 */
		@JvmField
		public val ANNOTATION_MTH_PARAMETERS: KadxAttrType<AnnotationMethodParamsAttr> = bind()

		/** 注解默认值（注解接口 default 方法）*/
		@JvmField
		public val ANNOTATION_DEFAULT: KadxAttrType<AnnotationDefaultAttr> = bind()

		/** 异常声明属性（throws）*/
		@JvmField
		public val EXCEPTIONS: KadxAttrType<ExceptionsAttr> = bind()

		/** 方法参数名属性 */
		@JvmField
		public val METHOD_PARAMETERS: KadxAttrType<MethodParametersAttr> = bind()

		/**
		 * 绑定（创建）新的属性类型实例。
		 *
		 * @param T 属性类类型
		 * @return 新的类型标记实例
		 */
		@JvmStatic
		private fun <A : IKadxAttribute> bind(): KadxAttrType<A> = KadxAttrType()
	}

	/** 私有的构造器，防止外部实例化 */
	private constructor()
}
