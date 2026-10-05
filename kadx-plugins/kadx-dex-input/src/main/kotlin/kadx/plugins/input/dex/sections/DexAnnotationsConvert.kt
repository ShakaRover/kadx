package kadx.plugins.input.dex.sections

import kadx.api.plugins.input.data.annotations.AnnotationVisibility
import kadx.api.plugins.input.data.annotations.EncodedType
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.api.plugins.input.data.attributes.types.AnnotationDefaultClassAttr
import kadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import kadx.api.plugins.input.data.attributes.types.ExceptionsAttr
import kadx.api.plugins.input.data.attributes.types.InnerClassesAttr
import kadx.api.plugins.input.data.attributes.types.InnerClsInfo
import kadx.api.plugins.input.data.attributes.types.MethodParametersAttr
import kadx.api.plugins.input.data.attributes.types.SignatureAttr
import kadx.api.plugins.utils.Utils
import kadx.plugins.input.dex.sections.annotations.AnnotationsUtils
import org.slf4j.LoggerFactory
import java.util.Collections

/**
 * DEX 注解转换器：把 dalvik 系统注解（SYSTEM 可见性）翻译成 kadx 的 [IKadxAttribute]。
 *
 * **背景**：DEX 中 Signature/InnerClass/Throws 等"编译期元信息"以普通注解形式存储，
 * 本类负责识别并还原为结构化属性；其余注解统一打包进 [AnnotationsAttr]。
 * 各 forXxx 入口对应 class/method/field 三个层级（method/field 无所属类名，cls 传 null）。
 */
public class DexAnnotationsConvert {

	public companion object {
		private val LOG = LoggerFactory.getLogger(DexAnnotationsConvert::class.java)

		public fun forClass(cls: String, list: MutableList<IKadxAttribute>, annotationList: List<IAnnotation>) {
			appendAnnotations(cls, list, annotationList)
		}

		public fun forMethod(list: MutableList<IKadxAttribute>, annotationList: List<IAnnotation>) {
			appendAnnotations(null, list, annotationList)
		}

		public fun forField(list: MutableList<IKadxAttribute>, annotationList: List<IAnnotation>) {
			appendAnnotations(null, list, annotationList)
		}

		private fun appendAnnotations(cls: String?, attributes: MutableList<IKadxAttribute>, annotations: List<IAnnotation>) {
			if (annotations.isEmpty()) {
				return
			}
			for (annotation in annotations) {
				if (annotation.visibility == AnnotationVisibility.SYSTEM) {
					convertSystemAnnotations(cls, attributes, annotation)
				}
			}
			Utils.addToList(attributes, AnnotationsAttr.pack(annotations))
		}

		private fun convertSystemAnnotations(cls: String?, attributes: MutableList<IKadxAttribute>, annotation: IAnnotation) {
			when (annotation.annotationClass) {
				"Ldalvik/annotation/Signature;" -> {
					attributes.add(SignatureAttr(extractSignature(annotation)))
				}

				"Ldalvik/annotation/InnerClass;" -> {
					try {
						val name: String? = AnnotationsUtils.getValue(annotation, "name", EncodedType.ENCODED_STRING, null)
						val accFlags: Int = AnnotationsUtils.getValue(annotation, "accessFlags", EncodedType.ENCODED_INT, 0)
						if (name != null || accFlags != 0) {
							val innerClsInfo = InnerClsInfo(cls, null, name, accFlags)

							// cls 可能为 null（method/field 层级），与原 Java 的 singletonMap(null, ...) 行为一致
							@Suppress("UNCHECKED_CAST")
							val map = Collections.singletonMap(cls, innerClsInfo) as Map<String, InnerClsInfo>
							attributes.add(InnerClassesAttr(map))
						}
					} catch (e: Exception) {
						LOG.warn("Failed to parse annotation: {}", annotation, e)
					}
				}

				"Ldalvik/annotation/AnnotationDefault;" -> {
					val annValue = annotation.defaultValue
					if (annValue != null && annValue.type == EncodedType.ENCODED_ANNOTATION) {
						val defAnnotation = annValue.value as IAnnotation
						attributes.add(AnnotationDefaultClassAttr(defAnnotation.values))
					}
				}

				"Ldalvik/annotation/Throws;" -> {
					try {
						val defaultValue = annotation.defaultValue
						if (defaultValue != null) {
							val values = defaultValue.value as List<*>
							val excs = ArrayList<String>(values.size)
							for (ev in values) {
								excs.add((ev as EncodedValue).value as String)
							}
							attributes.add(ExceptionsAttr(excs))
						}
					} catch (e: Exception) {
						LOG.warn("Failed to convert dalvik throws annotation", e)
					}
				}

				"Ldalvik/annotation/MethodParameters;" -> {
					try {
						val names = AnnotationsUtils.getArray(annotation, "names")
						val accFlags = AnnotationsUtils.getArray(annotation, "accessFlags")
						if (!names.isEmpty() && names.size == accFlags.size) {
							val size = names.size
							val list = ArrayList<MethodParametersAttr.Info>(size)
							for (i in 0 until size) {
								val name = names[i].value as String
								val accFlag = accFlags[i].value as Int
								list.add(MethodParametersAttr.Info(accFlag, name))
							}
							attributes.add(MethodParametersAttr(list))
						}
					} catch (e: Exception) {
						LOG.warn("Failed to parse annotation: {}", annotation, e)
					}
				}
			}
		}

		private fun extractSignature(annotation: IAnnotation): String {
			val default = checkNotNull(annotation.defaultValue) { "signature annotation has no value" }
			val values = default.value as List<*>
			if (values.size == 1) {
				return (values[0] as EncodedValue).value as String
			}
			val sb = StringBuilder()
			for (part in values) {
				sb.append((part as EncodedValue).value as String)
			}
			return sb.toString()
		}
	}
}
