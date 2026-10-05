package kadx.api.data.impl

import kadx.api.JavaClass
import kadx.api.JavaField
import kadx.api.JavaMethod
import kadx.api.JavaNode
import kadx.api.data.IJavaNodeRef
import kadx.api.data.IJavaNodeRef.RefType
import java.util.Objects

/**
 * [IJavaNodeRef] 的默认实现，同时是工程文件 JSON 的读写载体。
 *
 * **做什么**：用“引用类型 + 声明类（或包名） + 短签名”唯一标识一个类/字段/方法/包，
 * 供注释与重命名记录定位目标。
 *
 * **为什么用普通 class**：有自定义 `equals/hashCode`（按三字段判等），
 * 且需要无参构造器供 Gson 使用；绝不能改成 `data class`。
 *
 * **Kotlin 转换说明**：静态工厂方法放入 `companion object` + `@JvmStatic`，
 * Java 侧 `KadxNodeRef.forCls(...)` 等调用保持不变。
 */
class KadxNodeRef(
	refType: RefType? = null,
	declClass: String? = null,
	shortId: String? = null,
) : IJavaNodeRef {

	companion object {
		/**
		 * 根据 Java 视图节点构造引用；不认识的节点类型返回 null。
		 *
		 * 参数保留可空：原 Java 未对参数加 `@Nullable`，但调用方可能传入 null，
		 * Java 的 `instanceof null` 为 false，这里用 `when` 的 `else` 还原同样结果。
		 */
		@JvmStatic
		fun forJavaNode(javaNode: JavaNode?): KadxNodeRef? = when (javaNode) {
			is JavaClass -> forCls(javaNode)
			is JavaMethod -> forMth(javaNode)
			is JavaField -> forFld(javaNode)
			else -> null
		}

		/** 类引用（从 Java 类视图）。 */
		@JvmStatic
		fun forCls(cls: JavaClass): KadxNodeRef = KadxNodeRef(RefType.CLASS, getClassRefStr(cls), null)

		/** 类引用（直接给全名）。 */
		@JvmStatic
		fun forCls(clsFullName: String): KadxNodeRef = KadxNodeRef(RefType.CLASS, clsFullName, null)

		/** 方法引用（用方法短签名作为 shortId）。 */
		@JvmStatic
		fun forMth(mth: JavaMethod): KadxNodeRef = KadxNodeRef(
			RefType.METHOD,
			getClassRefStr(mth.declaringClass),
			mth.getMethodNode().methodInfo.shortId,
		)

		/** 字段引用（用字段短签名作为 shortId）。 */
		@JvmStatic
		fun forFld(fld: JavaField): KadxNodeRef = KadxNodeRef(
			RefType.FIELD,
			getClassRefStr(fld.declaringClass),
			fld.getFieldNode().getFieldInfo().shortId,
		)

		/** 包引用（shortId 为空字符串）。 */
		@JvmStatic
		fun forPkg(pkgFullName: String): KadxNodeRef = KadxNodeRef(RefType.PKG, pkgFullName, "")

		/** 取类的原始全名（未混淆/未别名化）。 */
		private fun getClassRefStr(cls: JavaClass): String = cls.getClassNode().classInfo.rawName
	}

	// 私有属性，Gson 反射读写
	private var refType: RefType? = refType
	private var declClass: String? = declClass
	private var shortId: String? = shortId

	override fun getType(): RefType = checkNotNull(refType) { "refType is not set" }

	fun setRefType(refType: RefType) {
		this.refType = refType
	}

	override val declaringClass: String get() = checkNotNull(declClass) { "declClass is not set" }

	fun setDeclClass(declClass: String) {
		this.declClass = declClass
	}

	override fun getShortId(): String? = shortId

	fun setShortId(shortId: String?) {
		this.shortId = shortId
	}

	/**
	 * 排序：依次比较 引用类型 -> 声明类 -> 短签名。
	 *
	 * 原 Java 用 `Comparator.comparing(...).thenComparing(...).thenComparing(...)`，
	 * 语义完全等价；这里手写比较以避免 Kotlin 对可空 `shortId` 的类型推断问题，
	 * 并保留“shortId 为 null 时抛 NPE”的原行为。
	 */
	override fun compareTo(other: IJavaNodeRef): Int {
		val cmpType = getType().compareTo(other.getType())
		if (cmpType != 0) {
			return cmpType
		}
		val cmpClass = declaringClass.compareTo(other.declaringClass)
		if (cmpClass != 0) {
			return cmpClass
		}
		val thisShortId = getShortId() ?: throw NullPointerException("shortId is null")
		val otherShortId = other.getShortId() ?: throw NullPointerException("shortId is null")
		return thisShortId.compareTo(otherShortId)
	}

	override fun hashCode(): Int = Objects.hash(refType, declClass, shortId)

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is KadxNodeRef) {
			return false
		}
		return refType === other.refType &&
			declClass == other.declClass &&
			shortId == other.shortId
	}

	override fun toString(): String = when (getType()) {
		RefType.CLASS, RefType.PKG -> declaringClass
		RefType.FIELD, RefType.METHOD -> declaringClass + "->" + shortId
		else -> "unknown node ref type"
	}
}
