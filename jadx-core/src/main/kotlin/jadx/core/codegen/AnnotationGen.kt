package jadx.core.codegen

import jadx.api.ICodeWriter
import jadx.api.plugins.input.data.IFieldRef
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.core.Consts
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.StringUtils
import jadx.core.utils.exceptions.JadxRuntimeException

/**
 * 注解代码生成器：把类 / 方法 / 字段 / 参数上挂载的注解渲染成源码。
 *
 * **职责**：
 * - 渲染注解声明（`@Override`、`@Deprecated(...)` 等）；
 * - 把注解值（[EncodedValue]）按类型转成源码字面量（含数组、嵌套注解、枚举/字段引用）；
 * - 渲染方法 `throws` 列表。
 *
 * **Kotlin 转换说明**：注解值是 `Any?`，按 [EncodedType] 分派时使用 `as` 强转（与原 Java 的强转一致）。
 */
class AnnotationGen(private val cls: ClassNode, private val classGen: ClassGen) {

	fun addForClass(code: ICodeWriter) {
		add(cls, code)
	}

	fun addForMethod(code: ICodeWriter, mth: MethodNode) {
		add(mth, code)
	}

	fun addForField(code: ICodeWriter, field: FieldNode) {
		add(field, code)
	}

	fun addForParameter(code: ICodeWriter, paramsAnnotations: AnnotationMethodParamsAttr, n: Int) {
		val paramList = paramsAnnotations.paramList
		if (n >= paramList.size) {
			return
		}
		val aList = paramList[n]
		if (aList == null || aList.isEmpty) {
			return
		}
		for (a in aList.all) {
			formatAnnotation(code, a)
			code.add(' ')
		}
	}

	private fun add(node: IAttributeNode, code: ICodeWriter) {
		val aList = node.get(JadxAttrType.ANNOTATION_LIST)
		if (aList == null || aList.isEmpty) {
			return
		}
		for (a in aList.all) {
			val aCls = a.annotationClass
			if (aCls != Consts.OVERRIDE_ANNOTATION) {
				code.startLine()
				formatAnnotation(code, a)
			}
		}
	}

	private fun formatAnnotation(code: ICodeWriter, a: IAnnotation) {
		code.add('@')
		val annCls = cls.root().resolveClass(a.annotationClass)
		if (annCls != null) {
			classGen.useClass(code, annCls)
		} else {
			classGen.useClass(code, a.annotationClass)
		}

		val vl = a.values
		if (!vl.isEmpty()) {
			code.add('(')
			val it = vl.entries.iterator()
			while (it.hasNext()) {
				val e = it.next()
				val paramName = getParamName(annCls, e.key)
				if (paramName == "value" && vl.size == 1) {
					// don't add "value = " if no other parameters
				} else {
					code.add(paramName)
					code.add(" = ")
				}
				encodeValue(cls.root(), code, e.value)
				if (it.hasNext()) {
					code.add(", ")
				}
			}
			code.add(')')
		}
	}

	private fun getParamName(annCls: ClassNode?, paramName: String): String {
		if (annCls != null) {
			// TODO: save value type and search using signature
			val mth = annCls.searchMethodByShortName(paramName)
			if (mth != null) {
				return mth.getAlias()
			}
		}
		return paramName
	}

	fun addThrows(mth: MethodNode, code: ICodeWriter) {
		val throwList = mth.getThrows()
		if (!throwList.isEmpty()) {
			code.add(" throws ")
			val it = throwList.iterator()
			while (it.hasNext()) {
				val ex = it.next()
				classGen.useType(code, ex)
				if (it.hasNext()) {
					code.add(", ")
				}
			}
		}
	}

	fun getAnnotationDefaultValue(mth: MethodNode): EncodedValue? {
		val defaultAttr = mth.get(JadxAttrType.ANNOTATION_DEFAULT)
		if (defaultAttr == null) {
			return null
		}
		return defaultAttr.value
	}

	// TODO: refactor this boilerplate code
	fun encodeValue(root: RootNode, code: ICodeWriter, encodedValue: EncodedValue?) {
		if (encodedValue == null) {
			code.add("null")
			return
		}

		val stringUtils = getStringUtils()
		val value = encodedValue.value
		when (encodedValue.type) {
			EncodedType.ENCODED_NULL -> code.add("null")

			EncodedType.ENCODED_BOOLEAN -> code.add(if (java.lang.Boolean.TRUE == value) "true" else "false")

			EncodedType.ENCODED_BYTE -> code.add(stringUtils.formatByte((value as Byte).toLong(), false))

			EncodedType.ENCODED_SHORT -> code.add(stringUtils.formatShort((value as Short).toLong(), false))

			EncodedType.ENCODED_CHAR -> code.add(stringUtils.unescapeChar(value as Char))

			EncodedType.ENCODED_INT -> code.add(stringUtils.formatInteger((value as Int).toLong(), false))

			EncodedType.ENCODED_LONG -> code.add(stringUtils.formatLong(value as Long, false))

			EncodedType.ENCODED_FLOAT -> code.add(StringUtils.formatFloat(value as Float))

			EncodedType.ENCODED_DOUBLE -> code.add(StringUtils.formatDouble(value as Double))

			EncodedType.ENCODED_STRING -> code.add(stringUtils.unescapeString(value as String))

			EncodedType.ENCODED_TYPE -> {
				classGen.useType(code, ArgType.parse(value as String))
				code.add(".class")
			}

			EncodedType.ENCODED_ENUM, EncodedType.ENCODED_FIELD -> {
				// must be a static field
				if (value is IFieldRef) {
					val fieldInfo = FieldInfo.fromRef(root, value)
					InsnGen.makeStaticFieldAccess(code, fieldInfo, classGen)
				} else if (value is FieldInfo) {
					InsnGen.makeStaticFieldAccess(code, value, classGen)
				} else {
					throw JadxRuntimeException("Unexpected field type class: " + checkNotNull(value).javaClass)
				}
			}

			EncodedType.ENCODED_METHOD -> {
				// TODO
			}

			EncodedType.ENCODED_ARRAY -> {
				code.add('{')
				val it = (value as Iterable<*>).iterator()
				while (it.hasNext()) {
					val v = it.next() as EncodedValue
					encodeValue(cls.root(), code, v)
					if (it.hasNext()) {
						code.add(", ")
					}
				}
				code.add('}')
			}

			EncodedType.ENCODED_ANNOTATION -> formatAnnotation(code, value as IAnnotation)

			else -> throw JadxRuntimeException("Can't decode value: " + encodedValue.type + " (" + encodedValue + ')')
		}
	}

	private fun getStringUtils(): StringUtils = cls.root().getStringUtils()
}
