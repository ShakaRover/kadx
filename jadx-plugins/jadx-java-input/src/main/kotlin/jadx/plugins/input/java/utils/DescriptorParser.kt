package jadx.plugins.input.java.utils

import jadx.plugins.input.java.data.JavaMethodProto

/**
 * JVM 方法描述符（descriptor）解析器。
 *
 * **做什么**：把形如 `(ILjava/lang/String;)V` 的方法签名串解析为参数类型列表 + 返回类型，
 * 填充到 [JavaMethodProto]。描述符是 .class 文件常量池中方法签名的紧凑编码形式：
 * `L...;` 表示对象类型、`[...]` 表示数组、单个字符（I/J/Z/...）表示基本类型。
 *
 * **为什么用私有构造器 + companion 静态入口**：原 Java 通过两个 static 工厂方法对外暴露，
 * 实例只是解析过程的临时载体；Kotlin 侧保持同样的使用方式（@JvmStatic 保证 Java 调用方零改动）。
 */
class DescriptorParser private constructor(private val desc: String) {

	private var pos = 0

	/**
	 * 解析完整方法描述符并写入 [mthProto]。
	 *
	 * @throws JavaClassParseException 描述符结构非法（缺少括号、未知类型字符）时抛出
	 */
	private fun parseMethodDescriptor(mthProto: JavaMethodProto) {
		// 注意：JavaMethodProto 的 getArgTypes/getReturnType 来自 Kotlin 接口 IMethodProto，
		// 不构成合成属性，必须用显式 setter 调用
		validate('(')
		if (check(')')) {
			mthProto.setArgTypes(emptyList())
		} else {
			mthProto.setArgTypes(readArgsList())
		}
		validate(')')
		mthProto.setReturnType(readType())
	}

	/** 逐个读取参数类型，直到遇到 ')'（readType 理论上可返回 null，保持原 Java 的可空元素语义） */
	private fun readArgsList(): MutableList<String?> {
		val list = ArrayList<String?>(5)
		do {
			list.add(readType())
		} while (!check(')'))
		return list
	}

	/**
	 * 读取单个类型描述符并推进 [pos]。
	 * @return 类型字符串；若描述符在此处意外结束则返回 null（与原 Java 行为一致）
	 */
	private fun readType(): String? {
		val cur = pos
		if (cur >= desc.length) {
			return null
		}
		when (val ch = desc[cur]) {
			'L' -> {
				val end = desc.indexOf(';', cur)
				if (end == -1) {
					throw JavaClassParseException("Unexpected object type descriptor: " + desc)
				}
				val lastChar = end + 1
				val type = desc.substring(cur, lastChar)
				pos = lastChar
				return type
			}

			'[' -> {
				// 数组类型：递归读取元素类型后加前缀（支持多维数组如 [[I）
				pos++
				return "[" + readType()
			}

			else -> {
				val primitiveType = parsePrimitiveType(ch)
				pos = cur + 1
				return primitiveType
			}
		}
	}

	/** 基本类型字符 → 描述符单字符（Z/B/C/S/I/J/F/D/V） */
	fun parsePrimitiveType(f: Char): String = when (f) {
		'Z', 'B', 'C', 'S', 'I', 'J', 'F', 'D', 'V' -> f.toString()
		else -> throw JavaClassParseException("Unexpected char '" + f + "' in descriptor " + desc)
	}

	private fun check(exp: Char): Boolean = desc[pos] == exp

	/** 断言当前位置是 [exp] 并前进一位，否则抛异常（附带出错位置便于定位损坏的 class） */
	private fun validate(exp: Char) {
		if (!check(exp)) {
			throw JavaClassParseException("Unexpected char in descriptor: " + desc + " at pos " + pos + ", expected: " + exp)
		}
		pos++
	}

	companion object {
		/** 解析描述符并填充到已有的 [mthProto]（供调用方复用 proto 实例的场景） */
		@JvmStatic
		fun fillMethodProto(mthDesc: String, mthProto: JavaMethodProto) {
			DescriptorParser(mthDesc).parseMethodDescriptor(mthProto)
		}

		/** 解析描述符并返回新的 [JavaMethodProto] */
		@JvmStatic
		fun parseToMethodProto(mthDesc: String): JavaMethodProto {
			val mthProto = JavaMethodProto()
			DescriptorParser(mthDesc).parseMethodDescriptor(mthProto)
			return mthProto
		}
	}
}
