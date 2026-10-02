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

	private var useIn: List<MethodNode> = emptyList()
	var javaNode: JavaField? = null

	companion object {
		fun build(cls: ClassNode, fieldData: IFieldData): FieldNode {
			val fieldInfo = FieldInfo.fromRef(cls.root(), fieldData)
			val fieldNode = FieldNode(cls, fieldInfo, fieldData.getAccessFlags())
			fieldNode.addAttrs(fieldData.getAttributes())
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

	fun getName(): String = fieldInfo.name

	fun getAlias(): String = fieldInfo.alias

	override fun rename(alias: String) {
		fieldInfo.setAlias(alias)
	}

	override fun getDeclaringClass(): ClassNode? = parentClass

	fun getTopParentClass(): ClassNode = parentClass.getTopParentClass()

	// 协变返回类型：保留 Java 原 API 的 List<MethodNode>
	override fun getUseIn(): List<MethodNode> = useIn

	fun setUseIn(useIn: List<MethodNode>) {
		this.useIn = useIn
	}

	@Synchronized
	fun addUseIn(mth: MethodNode) {
		useIn = safeAdd(useIn, mth)
	}

	override fun typeName(): String = "field"

	override fun getInputFileName(): String? = parentClass.inputFileName

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
