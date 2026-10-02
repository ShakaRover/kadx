package jadx.api.metadata.annotations

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.CodeVar
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.nodes.MethodNode

/**
 * 局部变量信息节点：一个寄存器在某 SSA 版本下的变量描述。
 *
 * **做什么**：保存变量所属方法、寄存器号、SSA 版本、类型、名字与定义位置，
 * 并通过 [varRef] 提供一个“按位置引用”的轻量视图。
 *
 * **缓存机制**：同一 [CodeVar] 只会创建一个 [VarNode]，缓存在 `CodeVar.cachedVarNode`
 * 上，保证同一变量在代码里始终对应同一个注解对象。
 *
 * **为什么不能改成 `data class`**：本类有自定义 `equals/hashCode`（按寄存器 + SSA 版本 +
 * 方法判等），且被大量用作身份标识；`data class` 会破坏这些语义。
 *
 * **Kotlin 转换说明**：静态工厂方法放入 `companion object` 并加 `@JvmStatic`；
 * getter 保持显式函数形态。原类非 final（有 protected 构造器供子类使用），
 * 故声明为 `open class`。
 */
open class VarNode(
	private val mth: MethodNode,
	private val reg: Int,
	private val ssa: Int,
	private val type: ArgType,
	private var name: String?,
) : ICodeNodeRef {

	companion object {
		/** 由寄存器参数构造；该寄存器没有 SSA 变量时返回 null。 */
		@JvmStatic
		fun get(mth: MethodNode, reg: RegisterArg): VarNode? {
			val ssaVar = reg.sVar ?: return null
			return get(mth, ssaVar)
		}

		/** 由代码变量构造。 */
		@JvmStatic
		fun get(mth: MethodNode, codeVar: CodeVar): VarNode? = get(mth, codeVar.getAnySsaVar())

		/** 由 SSA 变量构造（`this` 变量不生成节点；命中缓存则直接复用）。 */
		@JvmStatic
		fun get(mth: MethodNode, ssaVar: SSAVar): VarNode? {
			val codeVar = ssaVar.codeVar
			if (codeVar.isThis) {
				return null
			}
			val cachedVarNode = codeVar.cachedVarNode
			if (cachedVarNode != null) {
				return cachedVarNode
			}
			val newVarNode = VarNode(mth, ssaVar)
			codeVar.cachedVarNode = newVarNode
			return newVarNode
		}

		/** 取寄存器参数的“变量引用”注解；无变量时返回 null。 */
		@JvmStatic
		fun getRef(mth: MethodNode, reg: RegisterArg): ICodeAnnotation? {
			val varNode = get(mth, reg) ?: return null
			return varNode.getVarRef()
		}
	}

	/** 定义位置（反编译代码中的字符偏移）。 */
	private var defPos: Int = 0

	/** 变量引用视图，创建时即绑定本节点。 */
	private val varRef: VarRef = VarRef.fromVarNode(this)

	/**
	 * 由 SSA 变量构造。
	 *
	 * `codeVar.type` 理论上在代码生成阶段（本构造器唯一调用时机）已完成类型推导，
	 * 这里用 `checkNotNull` 显式校验，避免静默存入 null。
	 */
	protected constructor(mth: MethodNode, ssaVar: SSAVar) : this(
		mth,
		ssaVar.regNum,
		ssaVar.version,
		checkNotNull(ssaVar.codeVar.type) { "Var type is not resolved: " + ssaVar },
		ssaVar.codeVar.name,
	)

	/** 变量所属方法。 */
	fun getMth(): MethodNode = mth

	/** 寄存器号。 */
	fun getReg(): Int = reg

	/** SSA 版本号。 */
	fun getSsa(): Int = ssa

	/** 变量类型。 */
	fun getType(): ArgType = type

	/** 变量名（可能为 null）。 */
	fun getName(): String? = name

	/** 修改变量名。 */
	fun setName(name: String?) {
		this.name = name
	}

	/** 变量引用视图。 */
	fun getVarRef(): VarRef = varRef

	override fun getDefPosition(): Int = defPos

	override fun setDefPosition(pos: Int) {
		this.defPos = pos
	}

	override fun getAnnType(): ICodeAnnotation.AnnType = ICodeAnnotation.AnnType.VAR

	override fun hashCode(): Int {
		var h = 31 * getReg() + getSsa()
		return 31 * h + mth.hashCode()
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is VarNode) {
			return false
		}
		return getReg() == other.getReg() &&
			getSsa() == other.getSsa() &&
			getMth() == other.getMth()
	}

	override fun toString(): String = "VarNode{r" + reg + 'v' + ssa + '}'
}
