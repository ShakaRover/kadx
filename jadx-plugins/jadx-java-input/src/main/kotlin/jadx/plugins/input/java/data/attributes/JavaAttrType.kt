@file:Suppress("ktlint:standard:property-naming") // 常量属性沿用原 Java 大写命名（CONFIG_DIR_ 风格），保持 Java 调用方零改动

package jadx.plugins.input.java.data.attributes

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.plugins.input.java.data.attributes.debuginfo.LineNumberTableAttr
import jadx.plugins.input.java.data.attributes.debuginfo.LocalVarTypesAttr
import jadx.plugins.input.java.data.attributes.debuginfo.LocalVarsAttr
import jadx.plugins.input.java.data.attributes.stack.StackMapTableReader
import jadx.plugins.input.java.data.attributes.types.CodeAttr
import jadx.plugins.input.java.data.attributes.types.ConstValueAttr
import jadx.plugins.input.java.data.attributes.types.IgnoredAttr
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationDefaultAttr
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr
import jadx.plugins.input.java.data.attributes.types.JavaBootstrapMethodsAttr
import jadx.plugins.input.java.data.attributes.types.JavaExceptionsAttr
import jadx.plugins.input.java.data.attributes.types.JavaInnerClsAttr
import jadx.plugins.input.java.data.attributes.types.JavaMethodParametersAttr
import jadx.plugins.input.java.data.attributes.types.JavaParamAnnsAttr
import jadx.plugins.input.java.data.attributes.types.JavaSignatureAttr
import jadx.plugins.input.java.data.attributes.types.JavaSourceFileAttr
import jadx.plugins.input.java.data.attributes.types.StackMapTableAttr
import org.jetbrains.annotations.Nullable
import java.util.HashMap

/**
 * .class 属性类型注册表：每种属性一个 [JavaAttrType] 实例，绑定"属性名 → 读取器"。
 *
 * **做什么**：解析 attribute 区时按名字查到对应的 [IJavaAttributeReader]；
 * 同时用自增 [id] 作为 [JavaAttrStorage] 数组的下标实现 O(1) 存取。
 *
 * **为什么 id 必须按注册顺序分配**：[JavaAttrStorage] 的数组大小和槽位都依赖这里的注册次序，
 * 因此 companion 中各常量的声明顺序与原 Java static 块的赋值顺序严格一致，勿随意调换。
 */
class JavaAttrType<T : IJavaAttribute> private constructor(
	/** 注册序号（同时是 storage 数组下标） */
	val id: Int,
	/** .class 文件中的属性名（如 "Code"、"InnerClasses"） */
	val name: String,
	/** 该属性的读取器；被忽略/未支持的属性为 null */
	val reader: IJavaAttributeReader?,
) {

	override fun hashCode(): Int = id

	// 与原 Java 完全一致：不做 instanceof 检查，非 JavaAttrType 参数直接抛异常
	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		return id == (o as JavaAttrType<*>).id
	}

	override fun toString(): String = name

	companion object {
		private val NAME_TO_TYPE_MAP: HashMap<String, JavaAttrType<*>> = HashMap()

		// ===== 以下常量的声明顺序 = 原 Java static 块赋值顺序（决定 id 值），勿调换 =====

		@JvmField
		val CONST_VALUE: JavaAttrType<ConstValueAttr> = bind("ConstantValue", ConstValueAttr.reader())

		@JvmField
		val CODE: JavaAttrType<CodeAttr> = bind("Code", CodeAttr.reader())

		@JvmField
		val LINE_NUMBER_TABLE: JavaAttrType<LineNumberTableAttr> = bind("LineNumberTable", LineNumberTableAttr.reader())

		@JvmField
		val LOCAL_VAR_TABLE: JavaAttrType<LocalVarsAttr> = bind("LocalVariableTable", LocalVarsAttr.reader())

		@JvmField
		val LOCAL_VAR_TYPE_TABLE: JavaAttrType<LocalVarTypesAttr> = bind("LocalVariableTypeTable", LocalVarTypesAttr.reader())

		@JvmField
		val INNER_CLASSES: JavaAttrType<JavaInnerClsAttr> = bind("InnerClasses", JavaInnerClsAttr.reader())

		@JvmField
		val BOOTSTRAP_METHODS: JavaAttrType<JavaBootstrapMethodsAttr> = bind("BootstrapMethods", JavaBootstrapMethodsAttr.reader())

		@JvmField
		val RUNTIME_ANNOTATIONS: JavaAttrType<JavaAnnotationsAttr> = bind("RuntimeVisibleAnnotations", JavaAnnotationsAttr.reader(AnnotationVisibility.RUNTIME))

		@JvmField
		val BUILD_ANNOTATIONS: JavaAttrType<JavaAnnotationsAttr> = bind("RuntimeInvisibleAnnotations", JavaAnnotationsAttr.reader(AnnotationVisibility.BUILD))

		@JvmField
		val RUNTIME_PARAMETER_ANNOTATIONS: JavaAttrType<JavaParamAnnsAttr> = bind("RuntimeVisibleParameterAnnotations", JavaParamAnnsAttr.reader(AnnotationVisibility.RUNTIME))

		@JvmField
		val BUILD_PARAMETER_ANNOTATIONS: JavaAttrType<JavaParamAnnsAttr> = bind("RuntimeInvisibleParameterAnnotations", JavaParamAnnsAttr.reader(AnnotationVisibility.BUILD))

		@JvmField
		val ANNOTATION_DEFAULT: JavaAttrType<JavaAnnotationDefaultAttr> = bind("AnnotationDefault", JavaAnnotationDefaultAttr.reader())

		@JvmField
		val SOURCE_FILE: JavaAttrType<JavaSourceFileAttr> = bind("SourceFile", JavaSourceFileAttr.reader())

		@JvmField
		val SIGNATURE: JavaAttrType<JavaSignatureAttr> = bind("Signature", JavaSignatureAttr.reader())

		@JvmField
		val EXCEPTIONS: JavaAttrType<JavaExceptionsAttr> = bind("Exceptions", JavaExceptionsAttr.reader())

		@JvmField
		val METHOD_PARAMETERS: JavaAttrType<JavaMethodParametersAttr> = bind("MethodParameters", JavaMethodParametersAttr.reader())

		@JvmField
		val STACK_MAP_TABLE: JavaAttrType<StackMapTableAttr> = bind("StackMapTable", StackMapTableReader())

		// ignored（信息已由注解/access flag 表达，无需单独解析）
		@JvmField
		val DEPRECATED: JavaAttrType<IgnoredAttr> = bind("Deprecated", null)

		// duplicated by annotation
		@JvmField
		val SYNTHETIC: JavaAttrType<IgnoredAttr> = bind("Synthetic", null)

		// duplicated by access flag
		@JvmField
		val ENCLOSING_METHOD: JavaAttrType<IgnoredAttr> = bind("EnclosingMethod", null)

		// TODO: not supported yet
		@JvmField
		val RUNTIME_TYPE_ANNOTATIONS: JavaAttrType<IgnoredAttr> = bind("RuntimeVisibleTypeAnnotations", null)

		@JvmField
		val BUILD_TYPE_ANNOTATIONS: JavaAttrType<IgnoredAttr> = bind("RuntimeInvisibleTypeAnnotations", null)

		@JvmField
		val MODULE: JavaAttrType<IgnoredAttr> = bind("Module", null)

		@JvmField
		val NEST_HOST: JavaAttrType<IgnoredAttr> = bind("NestHost", null)

		@JvmField
		val NEST_MEMBERS: JavaAttrType<IgnoredAttr> = bind("NestMembers", null)

		@JvmField
		val SOURCE_DEBUG_EXTENSION: JavaAttrType<IgnoredAttr> = bind("SourceDebugExtension", null)

		/** 注册一个新属性类型：分配自增 id、写入名字索引表 */
		private fun <A : IJavaAttribute> bind(name: String, reader: IJavaAttributeReader?): JavaAttrType<A> {
			val attrType = JavaAttrType<A>(NAME_TO_TYPE_MAP.size, name, reader)
			NAME_TO_TYPE_MAP[name] = attrType
			return attrType
		}

		/** 按 .class 中的属性名查类型；未知名字返回 null */
		@Nullable
		@JvmStatic
		fun byName(name: String): JavaAttrType<*>? = NAME_TO_TYPE_MAP[name]

		/** @return 已注册属性类型的总数（storage 数组大小） */
		@JvmStatic
		fun size(): Int = NAME_TO_TYPE_MAP.size
	}
}
