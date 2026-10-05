package kadx.api.metadata

/**
 * 代码注解（code annotation）的顶层接口。
 *
 * **做什么**：反编译生成代码时，会在代码的不同字符位置挂上注解，用来记录
 * “这个位置对应哪个类 / 方法 / 字段 / 变量 / 字节码偏移”等信息。UI 点击代码时
 * 就靠这些注解定位到对应的 dex 节点。
 *
 * **Kotlin 转换说明**：`annType` 以 Kotlin 属性声明，JVM 上仍生成 `getAnnType()`，
 * Java 实现方与调用方零改动。
 */
interface ICodeAnnotation {

	/** 注解种类。 */
	enum class AnnType {
		/** 类声明。 */
		CLASS,

		/** 字段声明。 */
		FIELD,

		/** 方法声明。 */
		METHOD,

		/** 包声明。 */
		PKG,

		/** 变量（局部变量）声明。 */
		VAR,

		/** 变量引用（按位置指向 VAR 注解）。 */
		VAR_REF,

		/** 节点声明引用。 */
		DECLARATION,

		/** 字节码偏移。 */
		OFFSET,

		/** 类或方法体结束。 */
		END,
	}

	/** 返回注解种类。 */
	val annType: AnnType
}
