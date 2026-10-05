package kadx.api.plugins.input.data

/**
 * 访问标志工具类。
 *
 * **背景**：Java class file 和 Dex 文件使用位掩码（bitmask）存储访问修饰符和其他属性。
 * 这个类定义了所有标准标志常量，并提供检查/格式化方法。
 *
 * **标志分类**：
 *
 * **可见性修饰符**（互斥，最多一个）：
 * - PUBLIC (0x1)、PRIVATE (0x2)、PROTECTED (0x4)
 *
 * **类级别**：
 * - INTERFACE、ABSTRACT、ENUM、ANNOTATION、MODULE、STRICT、SUPER
 *
 * **方法级别**：
 * - STATIC、FINAL、NATIVE、ABSTRACT、SYNCHRONIZED、BRIDGE、VARARGS、CONSTRUCTOR
 *
 * **字段级别**：
 * - STATIC、FINAL、VOLATILE、TRANSIENT
 *
 * **通用**：
 * - SYNTHETIC（编译器生成）、DATA（Dex 特有）
 *
 * **示例**：`public static final int MAX_VALUE`
 * - flags = PUBLIC | STATIC | FINAL = 0x1 | 0x8 | 0x10 = 0x19
 */
public class AccessFlags {
	companion object {
		// ==================== 可见性修饰符 ====================
		/** public 修饰符 */
		public const val PUBLIC: Int = 0x1

		/** private 修饰符 */
		public const val PRIVATE: Int = 0x2

		/** protected 修饰符 */
		public const val PROTECTED: Int = 0x4

		// ==================== 通用标志 ====================

		/** static 修饰符（静态成员）*/
		public const val STATIC: Int = 0x8

		/** final 修饰符（不可变/不可覆盖）*/
		public const val FINAL: Int = 0x10

		/** synchronized 方法（方法级别）或 super 类（类级别）*/
		public const val SYNCHRONIZED: Int = 0x20
		public const val SUPER: Int = 0x20 // 与 SYNCHRONIZED 同值，不同作用域含义不同

		/** volatile 字段（字段级别）或 bridge 方法（方法级别）*/
		public const val VOLATILE: Int = 0x40
		public const val BRIDGE: Int = 0x40 // 泛型擦除产生的桥接方法

		/** transient 字段（字段级别）或 varargs 方法（方法级别）*/
		public const val TRANSIENT: Int = 0x80
		public const val VARARGS: Int = 0x80 // 可变参数方法

		/** native 方法（本地实现）*/
		public const val NATIVE: Int = 0x100

		/** interface 类型 */
		public const val INTERFACE: Int = 0x200

		/** abstract（抽象类/方法）*/
		public const val ABSTRACT: Int = 0x400

		/** strictfp（严格浮点运算）*/
		public const val STRICT: Int = 0x800

		/** synthetic（编译器生成，非源码直接编写）*/
		public const val SYNTHETIC: Int = 0x1000

		/** annotation 类型 */
		public const val ANNOTATION: Int = 0x2000

		/** enum 类型 */
		public const val ENUM: Int = 0x4000

		/** module 类型（Java 9+）*/
		public const val MODULE: Int = 0x8000

		// ==================== 方法级别 ====================

		/** constructor（构造函数标记）*/
		public const val CONSTRUCTOR: Int = 0x10000

		/** declared-synchronized（显式 synchronized 块）*/
		public const val DECLARED_SYNCHRONIZED: Int = 0x20000

		// ==================== Dex 特有 ====================

		/** data（Dex 文件特有标志）*/
		public const val DATA: Int = 0x40000

		/**
		 * 检查指定标志是否被设置。
		 *
		 * @param flags 访问标志位掩码
		 * @param flagValue 要检查的标志值
		 * @return true 如果该标志被设置
		 *
		 * **示例**：
		 * ```kotlin
		 * val flags = AccessFlags.PUBLIC or AccessFlags.STATIC
		 * AccessFlags.hasFlag(flags, AccessFlags.PUBLIC)  // true
		 * AccessFlags.hasFlag(flags, AccessFlags.PRIVATE) // false
		 * ```
		 */
		@JvmStatic
		public fun hasFlag(flags: Int, flagValue: Int): Boolean = (flags and flagValue) != 0

		/**
		 * 将访问标志格式化为人类可读的修饰符字符串。
		 *
		 * @param flags 访问标志位掩码
		 * @param scope 作用域（决定某些标志的含义）
		 * @return 修饰符字符串，如 "public static final "
		 *
		 * **输出示例**：
		 * - flags=0x19 (PUBLIC|STATIC|FINAL), scope=FIELD → "public static final "
		 * - flags=0x401 (PUBLIC|ABSTRACT), scope=CLASS → "public abstract "
		 * - flags=0x101 (PUBLIC|NATIVE), scope=METHOD → "public native "
		 *
		 * **注意**：返回字符串末尾有空格，方便直接拼接方法签名。
		 */
		@JvmStatic
		public fun format(flags: Int, scope: AccessFlagsScope): String {
			val code = StringBuilder()

			// 可见性修饰符（最多一个）
			if (hasFlag(flags, PUBLIC)) code.append("public ")
			if (hasFlag(flags, PRIVATE)) code.append("private ")
			if (hasFlag(flags, PROTECTED)) code.append("protected ")

			// 通用修饰符
			if (hasFlag(flags, STATIC)) code.append("static ")
			if (hasFlag(flags, FINAL)) code.append("final ")
			if (hasFlag(flags, ABSTRACT)) code.append("abstract ")
			if (hasFlag(flags, NATIVE)) code.append("native ")

			// 作用域特定修饰符
			when (scope) {
				AccessFlagsScope.METHOD -> {
					if (hasFlag(flags, SYNCHRONIZED)) code.append("synchronized ")
					if (hasFlag(flags, BRIDGE)) code.append("bridge ")
					if (hasFlag(flags, VARARGS)) code.append("varargs ")
				}

				AccessFlagsScope.FIELD -> {
					if (hasFlag(flags, VOLATILE)) code.append("volatile ")
					if (hasFlag(flags, TRANSIENT)) code.append("transient ")
				}

				AccessFlagsScope.CLASS -> {
					if (hasFlag(flags, MODULE)) code.append("module ")
					if (hasFlag(flags, STRICT)) code.append("strict ")
					if (hasFlag(flags, SUPER)) code.append("super ")
					if (hasFlag(flags, ENUM)) code.append("enum ")
					if (hasFlag(flags, DATA)) code.append("data ")
				}
			}

			// synthetic 总是放在最后（除了返回前）
			if (hasFlag(flags, SYNTHETIC)) code.append("synthetic ")

			return code.toString()
		}
	}
}
