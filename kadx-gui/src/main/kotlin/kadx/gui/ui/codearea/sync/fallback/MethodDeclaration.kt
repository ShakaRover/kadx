package kadx.gui.ui.codearea.sync.fallback

import kadx.core.utils.Utils
import java.util.Objects

/**
 * 方法 / 构造器声明：记录返回类型、参数类型、是否 static 以及方法名。
 *
 * **做什么**：Java 与 Smali 两种文本各有一个工厂方法，把声明行解析成可比较的
 * 结构（[Type] 内部类负责 Java 名与 Smali 名之间的互换）。
 *
 * **为什么不能用 `data class`**：原实现有自定义 `equals/hashCode`（类型比较时允许
 * 包名前缀缺失，只要一方以另一方结尾即可），`data class` 会破坏该语义。
 */
class MethodDeclaration private constructor(
	private val line: AbstractCodeAreaLine,
	private val returnType: Type,
	private val argTypes: List<Type>,
	private val isStatic: Boolean,
	private val name: String,
) : IDeclaration {

	override fun getIdentifyingName(): String = name

	override fun getLine(): AbstractCodeAreaLine = line

	override fun equals(other: Any?): Boolean {
		if (other is MethodDeclaration) {
			if (other.name != this.name) {
				return false
			}
			if (other.isStatic != this.isStatic) {
				return false
			}
			if (other.returnType != this.returnType) {
				return false
			}
			if (other.argTypes.size != this.argTypes.size) {
				return false
			}
			for (i in other.argTypes.indices) {
				if (other.argTypes[i] != this.argTypes[i]) {
					return false
				}
			}
			return true
		}
		return false
	}

	override fun hashCode(): Int = Objects.hash(name, isStatic, returnType, argTypes)

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append("NAME=").append(name).append("+++")
			.append("RETURN=").append(returnType).append("+++")
			.append("ARGS=")
		for (a in argTypes) {
			sb.append(a).append(",")
		}
		return sb.toString()
	}

	companion object {
		/** 从 Java 声明行创建方法声明。 */
		fun create(line: JavaCodeAreaLine): MethodDeclaration {
			val methodName = line.extractDeclaredMethodName()
				?: throw FallbackSyncException("no method name found in java declaration")

			// 取返回类型字符串
			val trimmed = line.trimmedStr
			val methodNameStartPos = trimmed.indexOf(methodName)
			// -2 跳到返回类型最后一个字符，+1 到返回类型第一个字符
			var returnTypeStartPos = trimmed.lastIndexOf(' ', methodNameStartPos - 2) + 1
			returnTypeStartPos = if (returnTypeStartPos > -1) returnTypeStartPos else 0
			val returnStr = trimmed.substring(returnTypeStartPos, methodNameStartPos - 1)

			// 取参数类型
			val argString = trimmed.substring(trimmed.indexOf('(') + 1, trimmed.indexOf(')'))
			val argStringParts = argString.split(", ")
			val argTypeStrings = ArrayList<String>()
			for (part in argStringParts) {
				if (part.isEmpty()) {
					break
				}
				argTypeStrings.add(part.substring(0, part.indexOf(" ")))
			}

			val isStatic = trimmed.contains("static ")

			val argTypes = argTypeStrings.map { Type.fromJavaName(it) }
			return MethodDeclaration(line, Type.fromJavaName(returnStr), argTypes, isStatic, methodName)
		}

		/** 从 Smali 声明行创建方法声明。 */
		fun create(line: SmaliAreaLine): MethodDeclaration {
			val methodName = line.extractDeclaredMethodName()
				?: throw FallbackSyncException("no method name found in smali declaration")

			// 取返回类型字符串
			val trimmed = line.trimmedStr
			var returnStr = trimmed.substring(trimmed.indexOf(')') + 1)
			returnStr = if (returnStr.endsWith(";")) returnStr.substring(0, returnStr.length - 1) else returnStr

			val isStatic = trimmed.contains("static ")

			return MethodDeclaration(line, Type.fromSmaliName(returnStr), parseSmaliArgs(trimmed), isStatic, methodName)
		}

		/** 解析 smali 参数描述符，如 `ILjava/lang/String;[I`。 */
		private fun parseSmaliArgs(lineStr: String): List<Type> {
			val argTypeStrings = ArrayList<String>()
			val argString = lineStr.substring(lineStr.indexOf('(') + 1, lineStr.indexOf(')'))
			var i = 0
			while (i < argString.length) {
				val c = argString[i]
				if (c == 'L') {
					var j = i
					while (j < argString.length) {
						if (argString[j] == ';') {
							argTypeStrings.add(argString.substring(i, j + 1))
							break
						}
						j++
					}
					i = j + 1
				} else if (c == '[') {
					argTypeStrings.add(argString.substring(i, i + 2))
					i += 2
				} else if (c != ' ') {
					argTypeStrings.add(argString.substring(i, i + 1))
					i++
				} else {
					i++
				}
			}
			return argTypeStrings.map { Type.fromSmaliName(it) }
		}
	}

	private class Type private constructor(
		private val smaliName: String,
		private val javaName: String,
	) {
		private val isNonPrimitive: Boolean get() = smaliName.startsWith("L")

		override fun equals(other: Any?): Boolean {
			if (other is Type) {
				if (other.isNonPrimitive || this.isNonPrimitive) {
					// 可能有一方缺少包名前缀
					return other.javaName.endsWith(this.javaName) || this.javaName.endsWith(other.javaName)
				}
				return other.javaName == this.javaName || other.smaliName == this.smaliName
			}
			return false
		}

		override fun hashCode(): Int = Objects.hash(this, javaName, smaliName)

		override fun toString(): String = "@" + smaliName + "-OR-" + javaName + "@"

		companion object {
			fun fromJavaName(name: String): Type = Type(Utils.javaNameToSmaliName(name), name)

			fun fromSmaliName(name: String): Type = Type(name, Utils.smaliNameToJavaName(name))
		}
	}
}
