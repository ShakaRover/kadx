package jadx.core.clsp

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.dex.instructions.args.ArgType
import org.intellij.lang.annotations.MagicConstant

/**
 * classpath 图中的类节点。
 *
 * **用途**：保存某个类的类型、父类/接口、泛型参数以及方法表，供反编译时的
 * 继承关系判断、泛型恢复与覆写检测使用。
 *
 * **相等性**：按类的类型 [clsType] 判等（两个不同类路径下的同名类视为同一个类），
 * 保留手写的 `equals/hashCode`，因此这里用普通 class 而非 data class。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 字段 `clsType/id/accFlags/source` 直接声明为 Kotlin 属性，JVM 上仍生成
 *   同名 getter（`getClsType()` 等），Java 调用方零改动；
 * - `parents` 数组允许为 null，且元素类型也可能为 null（classpath 文件可能缺失信息），
 *   因此声明为 `Array<ArgType?>?`，如实保留可空语义；
 * - `methodsMap` / `parents` 被其他 Kotlin 文件以属性语法（`.methodsMap`、`.parents`）访问，
 *   所以必须声明为真实属性。
 */
class ClspClass(
	val clsType: ArgType,
	val id: Int,
	val accFlags: Int,
	val source: ClspClassSource,
) {
	/** 父类与接口类型数组；类信息尚未设置时为 null，元素也可能为 null */
	var parents: Array<ArgType?>? = null

	/** 方法短签名 -> 方法节点；默认空表，由 [setMethods] 填充 */
	var methodsMap: Map<String, ClspMethod> = emptyMap()

	/** 泛型类型参数；默认空表 */
	var typeParameters: List<ArgType> = emptyList()

	/** 类的全限定名（DEX 内部名，如 `java.lang.String`） */
	val name: String get() = clsType.getObject()

	/** 是否为接口（检查访问标志中的 INTERFACE 位） */
	fun isInterface(): Boolean = AccessFlags.hasFlag(accFlags, AccessFlags.INTERFACE)

	/** 检查是否包含给定的访问标志位组合 */
	fun hasAccFlag(@MagicConstant(flagsFromClass = AccessFlags::class) flags: Int): Boolean = AccessFlags.hasFlag(accFlags, flags)

	/** 返回按方法签名排序后的方法列表（用于稳定输出，如保存 .jcst 文件） */
	val sortedMethodsList: List<ClspMethod> get() {
		val list = ArrayList<ClspMethod>(methodsMap.size)
		list.addAll(methodsMap.values)
		list.sort()
		return list
	}

	/** 由方法列表构建“短签名 -> 方法”的映射并写入方法表 */
	fun setMethods(methods: List<ClspMethod>) {
		val map = HashMap<String, ClspMethod>(methods.size)
		for (mth in methods) {
			map[mth.methodInfo.shortId] = mth
		}
		methodsMap = map
	}

	override fun hashCode(): Int = clsType.hashCode()

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		// 与原 Java 一致：必须类型完全相同（不是子类）才继续比较
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		val nClass = other as ClspClass
		return clsType == nClass.clsType
	}

	override fun toString(): String = clsType.toString()
}
