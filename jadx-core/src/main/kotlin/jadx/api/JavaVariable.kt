package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.api.metadata.annotations.VarNode
import jadx.api.metadata.annotations.VarRef
import jadx.core.dex.instructions.args.ArgType
import org.jetbrains.annotations.ApiStatus

/**
 * 方法内局部变量的 Java 视图（包装 [VarNode]）。
 *
 * 公共 API；getter 保留显式函数形态。禁止改成 `data class`（引用语义 + 自定义 equals）。
 */
class JavaVariable(
	private val mth: JavaMethod,
	private val varNode: VarNode,
) : JavaNode {

	/** 变量所属方法。 */
	fun getMth(): JavaMethod = mth

	/** 寄存器号。 */
	fun getReg(): Int = varNode.getReg()

	/** SSA 版本号。 */
	fun getSsa(): Int = varNode.getSsa()

	override fun getName(): String? = varNode.getName()

	override fun getCodeNodeRef(): ICodeNodeRef = varNode

	/** 内部 API，非稳定。 */
	@ApiStatus.Internal
	fun getVarNode(): VarNode = varNode

	override fun getFullName(): String = varNode.getType().toString() + " " + varNode.getName() + " (r" + varNode.getReg() + "v" + varNode.getSsa() + ")"

	/** 变量类型（解析类别名之后）。 */
	fun getType(): ArgType = ArgType.tryToResolveClassAlias(mth.getMethodNode().root(), varNode.getType())

	override fun getDeclaringClass(): JavaClass = mth.getDeclaringClass()

	override fun getTopParentClass(): JavaClass = mth.getTopParentClass()

	override fun getDefPos(): Int = varNode.getDefPosition()

	override fun getUseIn(): List<JavaNode> = listOf<JavaNode>(mth)

	override fun removeAlias() {
		varNode.setName(null)
	}

	override fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean {
		if (ann.getAnnType() == ICodeAnnotation.AnnType.VAR_REF) {
			val varRef = ann as VarRef
			return varRef.getRefPos() == getDefPos()
		}
		return false
	}

	override fun hashCode(): Int = varNode.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is JavaVariable) {
			return false
		}
		return varNode == other.varNode
	}
}
