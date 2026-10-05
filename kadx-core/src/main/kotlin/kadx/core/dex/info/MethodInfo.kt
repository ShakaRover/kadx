package kadx.core.dex.info

import kadx.api.plugins.input.data.IMethodProto
import kadx.api.plugins.input.data.IMethodRef
import kadx.core.codegen.TypeGen
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.Utils

/**
 * 方法元信息（MethodInfo）。
 *
 * 描述“哪个类的哪个方法、参数与返回类型是什么”，并缓存 [shortId]/[rawFullId]/hash
 * 以避免重复计算。原 Java 重写了 `equals`/`hashCode`（按 签名+声明类 判等），
 * Kotlin 侧原样保留，不使用 `data class`。
 *
 * Kotlin 转换说明：
 * - 简单 getter 改为属性（`name`/`declClass`/`returnType`/`argumentsTypes`/`shortId`/
 *   `rawFullId`/`fullId`/`fullName`/`aliasFullName`/`argsCount`/`alias`），
 *   JVM 访问器名与原 Java 一致；
 * - `isConstructor()`/`isClassInit()`/`hasAlias()` 等保持显式函数；
 * - 静态工厂与 [makeShortId] 放入 companion + `@JvmStatic`。
 */
class MethodInfo private constructor(
	val declClass: ClassInfo,
	val name: String,
	val argumentsTypes: List<ArgType>,
	val returnType: ArgType,
) : Comparable<MethodInfo> {

	/** 方法别名（重命名后），默认与 [name] 相同 */
	var alias: String = name

	/** 方法名 + 参数/返回类型签名，唯一标识一个方法 */
	val shortId: String = makeShortId(name, argumentsTypes, returnType)

	/** 声明类原始名 + [shortId] */
	val rawFullId: String = declClass.makeRawFullName() + '.' + shortId

	private val hash: Int = calcHashCode()

	companion object {
		/** 从输入层方法引用构造（并按 uniq id / 签名缓存）。 */
		fun fromRef(root: RootNode, methodRef: IMethodRef): MethodInfo {
			val infoStorage = root.infoStorage
			val uniqId = methodRef.uniqId
			if (uniqId != 0) {
				val prevMth = infoStorage.getByUniqId(uniqId)
				if (prevMth != null) {
					return prevMth
				}
			}
			methodRef.load()
			val parentClsType = ArgType.parse(methodRef.parentClassType)
			val parentClass = ClassInfo.fromType(root, parentClsType)
			val returnType = ArgType.parse(methodRef.returnType)
			val args = Utils.collectionMap(methodRef.argTypes) { ArgType.parse(it) }
			val newMth = MethodInfo(parentClass, methodRef.name, args, returnType)
			val uniqMth = infoStorage.putMethod(newMth)
			if (uniqId != 0) {
				infoStorage.putByUniqId(uniqId, uniqMth)
			}
			return uniqMth
		}

		/** 用已知的声明类、名字、参数、返回类型构造并唯一化。 */
		fun fromDetails(root: RootNode, declClass: ClassInfo, name: String, args: List<ArgType>, retType: ArgType): MethodInfo {
			val newMth = MethodInfo(declClass, name, args, retType)
			return root.infoStorage.putMethod(newMth)
		}

		/** 从输入层方法原型 [IMethodProto] 构造。 */
		fun fromMethodProto(root: RootNode, declClass: ClassInfo, name: String, proto: IMethodProto): MethodInfo {
			val args = Utils.collectionMap(proto.argTypes) { ArgType.parse(it) }
			val returnType = ArgType.parse(proto.returnType)
			return fromDetails(root, declClass, name, args, returnType)
		}

		/** 拼接方法短 id：`名字(参数签名)返回签名`。 */
		fun makeShortId(name: String, argTypes: List<ArgType>, retType: ArgType?): String {
			val sb = StringBuilder()
			sb.append(name)
			sb.append('(')
			for (arg in argTypes) {
				sb.append(TypeGen.signature(arg))
			}
			sb.append(')')
			if (retType != null) {
				sb.append(TypeGen.signature(retType))
			}
			return sb.toString()
		}
	}

	/** 生成方法签名；[includeRetType] 决定是否带返回类型。 */
	fun makeSignature(includeRetType: Boolean): String = makeSignature(false, includeRetType)

	/** 生成方法签名；[useAlias] 决定用别名还是原名。 */
	fun makeSignature(useAlias: Boolean, includeRetType: Boolean): String = makeShortId(if (useAlias) alias else name, argumentsTypes, if (includeRetType) returnType else null)

	/**
	 * 判断是否被 [otherMthInfo] 重载：名字与参数个数相同，但签名不同。
	 */
	fun isOverloadedBy(otherMthInfo: MethodInfo): Boolean = argumentsTypes.size == otherMthInfo.argumentsTypes.size &&
		name == otherMthInfo.name &&
		shortId != otherMthInfo.shortId

	/** `声明类.方法名`。 */
	val fullName: String get() = declClass.fullName + '.' + name

	/** `别名声明类.别名`。 */
	val aliasFullName: String get() = declClass.aliasFullName + '.' + alias

	/** `声明类.方法签名`。 */
	val fullId: String get() = declClass.fullName + '.' + shortId

	/** 参数个数。 */
	val argsCount: Int get() = argumentsTypes.size

	fun isConstructor(): Boolean = name == "<init>"

	fun isClassInit(): Boolean = name == "<clinit>"

	/** 是否被重命名。 */
	fun hasAlias(): Boolean = name != alias

	/** 清除别名，恢复原名。 */
	fun removeAlias() {
		alias = name
	}

	fun calcHashCode(): Int = shortId.hashCode() + 31 * declClass.hashCode()

	override fun hashCode(): Int = hash

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is MethodInfo) {
			return false
		}
		return shortId == other.shortId &&
			declClass == other.declClass
	}

	override fun compareTo(other: MethodInfo): Int {
		val clsCmp = declClass.compareTo(other.declClass)
		if (clsCmp != 0) {
			return clsCmp
		}
		return shortId.compareTo(other.shortId)
	}

	override fun toString(): String = declClass.fullName + '.' + name + '(' + Utils.listToString(argumentsTypes) + "):" + returnType
}
