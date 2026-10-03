package jadx.core.dex.nodes

import jadx.api.JavaField
import jadx.api.metadata.ICodeAnnotation
import jadx.api.plugins.input.data.IFieldData
import jadx.core.dex.attributes.nodes.NotificationAttrNode
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.ListUtils.safeAdd

class FieldNode(
	val parentClass: ClassNode,
	@get:JvmName("fieldInfoValue")
	val fieldInfo: FieldInfo,
	accessFlags: Int,
) : NotificationAttrNode(),
	ICodeNode,
	IFieldInfoRef {
	var type: ArgType = fieldInfo.type
	var accFlags: AccessInfo = AccessInfo(accessFlags, AccessInfo.AFType.FIELD)

	private var useInValue: List<MethodNode> = emptyList()
	var javaNode: JavaField? = null

	companion object {
		fun build(cls: ClassNode, fieldData: IFieldData): FieldNode {
			val fieldInfo = FieldInfo.fromRef(cls.root(), fieldData)
			val fieldNode = FieldNode(cls, fieldInfo, fieldData.accessFlags)
			fieldNode.addAttrs(fieldData.attributes)
			return fieldNode
		}
	}

	fun unload() {
		unloadAttributes()
	}

	fun updateType(type: ArgType) {
		this.type = type
	}

	override fun getFieldInfo(): FieldInfo = fieldInfo

	override var accessFlags: AccessInfo
		get() = accFlags
		set(value) {
			accFlags = value
		}

	fun isStatic(): Boolean = accFlags.isStatic()

	fun isInstance(): Boolean = !accFlags.isStatic()

	val name: String get() = fieldInfo.name

	val alias: String get() = fieldInfo.alias

	override fun rename(alias: String) {
		fieldInfo.alias = alias
	}

	override val declaringClass: ClassNode? get() = parentClass

	val topParentClass: ClassNode get() = parentClass.topParentClass

	// 协变返回类型：保留 Java 原 API 的 List<MethodNode>
	override val useIn: List<MethodNode> get() = useInValue

	fun setUseIn(useIn: List<MethodNode>) {
		this.useInValue = useIn
	}

	@Synchronized
	fun addUseIn(mth: MethodNode) {
		useInValue = safeAdd(useInValue, mth)
	}

	override fun typeName(): String = "field"

	override val inputFileName: String? get() = parentClass.inputFileName

	override fun root(): RootNode = parentClass.root()

	override fun getAnnType() = ICodeAnnotation.AnnType.FIELD

	override fun hashCode(): Int = fieldInfo.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is FieldNode) return false
		return fieldInfo == other.fieldInfo
	}

	override fun toString(): String = "${fieldInfo.declClass}.${fieldInfo.name}:$type"
}
