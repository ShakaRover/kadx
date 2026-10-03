package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.FieldNode
import org.jetbrains.annotations.ApiStatus

/**
 * 字段的 Java 视图：把内部 [FieldNode] 包装成对插件/GUI 友好的对象。
 *
 * 公共 API，getter 全部保留显式函数形态（JVM 方法名与原来一致）。
 * 该类是 final 且不覆写 equals 的“引用语义”对象，禁止改成 `data class`。
 */
class JavaField internal constructor(
	private val field: FieldNode,
	private val parent: JavaClass,
) : JavaNode {

	override fun getName(): String = field.alias

	override fun getFullName(): String = parent.getFullName() + '.' + getName()

	/** 原始（未去混淆）字段名。 */
	fun getRawName(): String = field.name

	override val declaringClass: JavaClass get() = parent

	override fun getTopParentClass(): JavaClass = parent.getTopParentClass()

	/** 访问修饰符信息。 */
	fun getAccessFlags(): AccessInfo = field.accessFlags

	/** 字段类型（解析类别名之后）。 */
	fun getType(): ArgType = ArgType.tryToResolveClassAlias(field.root(), field.type)

	override fun getDefPos(): Int = field.defPosition

	override val useIn: List<JavaNode> get() = declaringClass.getRootDecompiler().convertNodes(this.field.useIn)

	override fun removeAlias() {
		field.getFieldInfo().removeAlias()
	}

	override fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean {
		if (ann.annType == ICodeAnnotation.AnnType.FIELD) {
			return ann == field
		}
		return false
	}

	override fun getCodeNodeRef(): ICodeNodeRef = field

	/**
	 * 内部 API，非稳定。
	 */
	@ApiStatus.Internal
	fun getFieldNode(): FieldNode = field

	override fun hashCode(): Int = field.hashCode()

	override fun equals(other: Any?): Boolean = this === other || (other is JavaField && field == other.field)

	override fun toString(): String = field.toString()
}
