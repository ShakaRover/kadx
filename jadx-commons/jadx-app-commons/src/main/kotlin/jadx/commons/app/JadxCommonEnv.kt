package jadx.commons.app

/**
 * 环境变量读取工具。
 *
 * 提供 String / Boolean / Int 三种类型的取值方法：
 * 环境变量不存在或为空时返回给定的默认值，避免调用方重复写判空逻辑。
 * 所有方法加了 @JvmStatic，Java 代码仍可按 JadxCommonEnv.getBool(...) 直接静态调用。
 */
class JadxCommonEnv {
	companion object {
		/**
		 * 读取字符串类型的环境变量。
		 *
		 * @param varName 环境变量名
		 * @param defValue 取不到值时的默认值（可为 null）
		 */
		@JvmStatic
		fun get(varName: String, defValue: String?): String? {
			val strValue = System.getenv(varName)
			return if (isNullOrEmpty(strValue)) defValue else strValue
		}

		/** 读取布尔类型的环境变量，值为 "true"（忽略大小写）时为 true */
		@JvmStatic
		fun getBool(varName: String, defValue: Boolean): Boolean {
			val strValue = System.getenv(varName)
			if (isNullOrEmpty(strValue)) return defValue
			return strValue.equals("true", ignoreCase = true) // 对应 Java 的 equalsIgnoreCase("true")
		}

		/** 读取整型环境变量，值为非法数字时 toInt() 会抛 NumberFormatException（与原 Java 行为一致） */
		@JvmStatic
		fun getInt(varName: String, defValue: Int): Int {
			val strValue = System.getenv(varName)
			if (isNullOrEmpty(strValue)) return defValue
			return strValue.toInt() // 对应 Java 的 Integer.parseInt，非法数字抛 NumberFormatException
		}

		private fun isNullOrEmpty(value: String?) = value.isNullOrEmpty()
	}
}
