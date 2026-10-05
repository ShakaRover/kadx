package kadx.api

import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeNodeRef
import kadx.core.dex.attributes.AType
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.MethodNode
import kadx.core.utils.Utils
import org.jetbrains.annotations.ApiStatus

/**
 * 方法的 Java 视图：把内部 [MethodNode] 包装成对插件/GUI 友好的对象。
 *
 * 公共 API，接口/类成员以 Kotlin 属性形式声明时 JVM 方法名与原来一致。
 * 该类是 final 且使用自定义 equals/hashCode（基于 [MethodNode]），禁止改成 `data class`。
 */
class JavaMethod internal constructor(
	private val mth: MethodNode,
	private val parent: JavaClass,
) : JavaNode {

	override fun getName(): String = mth.alias

	override fun getFullName(): String = mth.methodInfo.fullName

	override val declaringClass: JavaClass get() = parent

	override fun getTopParentClass(): JavaClass = parent.getTopParentClass()

	/** 访问修饰符信息。 */
	fun getAccessFlags(): AccessInfo = mth.accessFlags

	/** 方法参数类型列表（解析类别名之后）。 */
	fun getArguments(): List<ArgType> {
		val infoArgTypes = mth.methodInfo.argumentsTypes
		if (infoArgTypes.isEmpty()) {
			return emptyList()
		}
		val arguments = mth.argTypes
		return Utils.collectionMap(arguments) { type -> ArgType.tryToResolveClassAlias(mth.root(), type) }
	}

	/** 返回类型（解析类别名之后）。 */
	val returnType: ArgType
		get() {
			val retType = mth.returnType
			return ArgType.tryToResolveClassAlias(mth.root(), retType)
		}

	override val useIn: List<JavaNode> get() = declaringClass.getRootDecompiler().convertNodes(mth.useIn)

	/** 本方法调用了哪些方法。 */
	fun getUsed(): List<JavaNode> = declaringClass.getRootDecompiler().convertNodes(mth.used)

	/** 未能解析的目标方法列表。 */
	fun getUnresolvedUsed(): List<MethodInfo> = mth.getUnresolvedUsed()

	/** 是否递归调用自身。 */
	fun callsSelf(): Boolean = mth.callsSelf()

	/** 与本方法存在覆写关系的所有方法。 */
	fun getOverrideRelatedMethods(): List<JavaMethod> {
		val ovrdAttr = mth.get(AType.METHOD_OVERRIDE) ?: return emptyList()
		val decompiler = declaringClass.getRootDecompiler()
		return ovrdAttr.relatedMthNodes.map { decompiler.convertMethodNode(it) }
	}

	/** 是否为构造方法（`<init>`）。 */
	fun isConstructor(): Boolean = mth.methodInfo.isConstructor()

	/** 是否为静态初始化方法（`<clinit>`）。 */
	fun isClassInit(): Boolean = mth.methodInfo.isClassInit()

	override fun getDefPos(): Int = mth.defPosition

	/** 本方法的反编译代码字符串。 */
	val codeStr: String get() = mth.codeStr

	override fun removeAlias() {
		mth.methodInfo.removeAlias()
	}

	override fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean {
		if (ann.annType == ICodeAnnotation.AnnType.METHOD) {
			return ann == mth
		}
		return false
	}

	override fun getCodeNodeRef(): ICodeNodeRef = mth

	/**
	 * 内部 API，非稳定。
	 */
	@ApiStatus.Internal
	fun getMethodNode(): MethodNode = mth

	override fun hashCode(): Int = mth.hashCode()

	override fun equals(other: Any?): Boolean = this === other || (other is JavaMethod && mth == other.mth)

	override fun toString(): String = mth.toString()
}
