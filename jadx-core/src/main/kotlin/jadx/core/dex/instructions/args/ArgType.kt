@file:Suppress("ktlint:standard:property-naming")

package jadx.core.dex.instructions.args

import jadx.core.Consts.CLASS_CLASS
import jadx.core.Consts.CLASS_ENUM
import jadx.core.Consts.CLASS_ERROR
import jadx.core.Consts.CLASS_EXCEPTION
import jadx.core.Consts.CLASS_OBJECT
import jadx.core.Consts.CLASS_RUNTIME_EXCEPTION
import jadx.core.Consts.CLASS_STRING
import jadx.core.Consts.CLASS_THROWABLE
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.typeinference.TypeCompareEnum
import jadx.core.utils.ListUtils.map
import jadx.core.utils.Utils.arrayToStr
import jadx.core.utils.Utils.cleanObjectName
import jadx.core.utils.Utils.listToString
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.Arrays
import java.util.Collections

/**
 * DEX 指令操作数的类型系统抽象基类。
 *
 * ArgType 是 JADX 类型推导引擎的核心，表示：
 * - 基本类型（int、boolean、float 等）
 * - 对象类型（java.lang.String 等）
 * - 数组类型（String[]、int[][] 等）
 * - 泛型类型（List<String>、Map<K,V> 等）
 * - 通配符类型（? extends Number、? super Object 等）
 * - 未知/不确定类型（??[INT,FLOAT] 表示可能是 int 或 float）
 *
 * 设计特点：
 * 1. 不可变性：所有子类都是 final，支持线程安全共享
 * 2. 类型缓存：常用类型（INT、STRING 等）作为静态常量避免重复创建
 * 3. 身份比较：equals/hashCode 基于值而非引用，适合做 Map 键
 * 4. 递归结构：数组/泛型类型内部嵌套其他 ArgType，支持 visitTypes() 遍历
 *
 * @see PrimitiveType 基本类型枚举
 * @see jadx.core.dex.visitors.ssa.SSA 静态单赋值形式中的类型推导
 */
abstract class ArgType private constructor() {
	/** hashCode 缓存，避免重复计算（不可变对象可安全缓存） */
	protected var hash: Int = 0

	companion object {
		// ==================== 预定义的基本类型常量 ====================

		/** 基本类型常量 - 对应 Java 的 8 种基本类型 */
		lateinit var INT: ArgType
		lateinit var BOOLEAN: ArgType
		lateinit var BYTE: ArgType
		lateinit var SHORT: ArgType
		lateinit var CHAR: ArgType
		lateinit var FLOAT: ArgType
		lateinit var DOUBLE: ArgType
		lateinit var LONG: ArgType
		lateinit var VOID: ArgType

		/** 常用对象类型常量 - Java 核心类 */
		lateinit var OBJECT: ArgType
		lateinit var CLASS: ArgType
		lateinit var STRING: ArgType
		lateinit var ENUM: ArgType
		lateinit var THROWABLE: ArgType
		lateinit var ERROR: ArgType
		lateinit var EXCEPTION: ArgType
		lateinit var RUNTIME_EXCEPTION: ArgType
		lateinit var OBJECT_ARRAY: ArgType

		/** 通配符类型 - 表示任意类型 */
		lateinit var WILDCARD: ArgType

		/** 未知类型常量 - 用于类型推导过程中的不确定状态 */
		lateinit var UNKNOWN: ArgType
		lateinit var UNKNOWN_OBJECT: ArgType
		lateinit var UNKNOWN_OBJECT_NO_ARRAY: ArgType
		lateinit var UNKNOWN_ARRAY: ArgType

		/** 窄化类型 - 表示可能的类型集合（用于类型约束） */
		lateinit var NARROW: ArgType
		lateinit var NARROW_NUMBERS: ArgType
		lateinit var NARROW_INTEGRAL: ArgType
		lateinit var NARROW_NUMBERS_NO_BOOL: ArgType
		lateinit var NARROW_NEG_NUMBERS: ArgType
		lateinit var NARROW_NUMBERS_NO_FLOAT: ArgType
		lateinit var WIDE: ArgType
		lateinit var INT_FLOAT: ArgType
		lateinit var INT_BOOLEAN: ArgType
		lateinit var BYTE_BOOLEAN: ArgType
		lateinit var UNKNOWN_INT: ArgType

		// 初始化所有静态常量（在类加载时执行）
		init {
			// 基本类型
			INT = primitive(PrimitiveType.INT)
			BOOLEAN = primitive(PrimitiveType.BOOLEAN)
			BYTE = primitive(PrimitiveType.BYTE)
			SHORT = primitive(PrimitiveType.SHORT)
			CHAR = primitive(PrimitiveType.CHAR)
			FLOAT = primitive(PrimitiveType.FLOAT)
			DOUBLE = primitive(PrimitiveType.DOUBLE)
			LONG = primitive(PrimitiveType.LONG)
			VOID = primitive(PrimitiveType.VOID)

			// 对象类型
			OBJECT = objectNoCache(CLASS_OBJECT)
			CLASS = objectNoCache(CLASS_CLASS)
			STRING = objectNoCache(CLASS_STRING)
			ENUM = objectNoCache(CLASS_ENUM)
			THROWABLE = objectNoCache(CLASS_THROWABLE)
			ERROR = objectNoCache(CLASS_ERROR)
			EXCEPTION = objectNoCache(CLASS_EXCEPTION)
			RUNTIME_EXCEPTION = objectNoCache(CLASS_RUNTIME_EXCEPTION)
			OBJECT_ARRAY = array(OBJECT)

			// 通配符
			WILDCARD = wildcard()

			// 未知类型
			UNKNOWN = unknown(*PrimitiveType.values())
			UNKNOWN_OBJECT = unknown(PrimitiveType.OBJECT, PrimitiveType.ARRAY)
			UNKNOWN_OBJECT_NO_ARRAY = unknown(PrimitiveType.OBJECT)
			UNKNOWN_ARRAY = array(UNKNOWN)

			// 窄化类型
			NARROW = unknown(
				PrimitiveType.INT,
				PrimitiveType.FLOAT,
				PrimitiveType.BOOLEAN,
				PrimitiveType.SHORT,
				PrimitiveType.BYTE,
				PrimitiveType.CHAR,
				PrimitiveType.OBJECT,
				PrimitiveType.ARRAY,
			)
			NARROW_NUMBERS = unknown(
				PrimitiveType.BOOLEAN,
				PrimitiveType.INT,
				PrimitiveType.FLOAT,
				PrimitiveType.SHORT,
				PrimitiveType.BYTE,
				PrimitiveType.CHAR,
			)
			NARROW_INTEGRAL = unknown(
				PrimitiveType.INT,
				PrimitiveType.SHORT,
				PrimitiveType.BYTE,
				PrimitiveType.CHAR,
			)
			NARROW_NUMBERS_NO_BOOL = unknown(
				PrimitiveType.INT,
				PrimitiveType.FLOAT,
				PrimitiveType.SHORT,
				PrimitiveType.BYTE,
				PrimitiveType.CHAR,
			)
			NARROW_NEG_NUMBERS = unknown(
				PrimitiveType.INT,
				PrimitiveType.SHORT,
				PrimitiveType.BYTE,
				PrimitiveType.FLOAT,
			)
			NARROW_NUMBERS_NO_FLOAT = unknown(
				PrimitiveType.INT,
				PrimitiveType.BOOLEAN,
				PrimitiveType.SHORT,
				PrimitiveType.BYTE,
				PrimitiveType.CHAR,
			)
			WIDE = unknown(PrimitiveType.LONG, PrimitiveType.DOUBLE)
			INT_FLOAT = unknown(PrimitiveType.INT, PrimitiveType.FLOAT)
			INT_BOOLEAN = unknown(PrimitiveType.INT, PrimitiveType.BOOLEAN)
			BYTE_BOOLEAN = unknown(PrimitiveType.BYTE, PrimitiveType.BOOLEAN)
			UNKNOWN_INT = unknown(PrimitiveType.INT)
		}

		// ==================== 类型构造工厂方法 ====================

		/** 创建基本类型实例（内部使用，外部通过 companion object 常量访问） */
		private fun primitive(stype: PrimitiveType): ArgType = PrimitiveArg(stype)

		/** 创建对象类型（不缓存，用于动态类名） */
		private fun objectNoCache(obj: String): ArgType = ObjectType(obj)

		/**
		 * 创建对象类型（带常用类缓存优化）。
		 * 对于 Object/String/Class 等高频类型直接返回共享实例。
		 */
		fun `object`(obj: String): ArgType {
			val cleanObjectName = cleanObjectName(obj)
			return when (cleanObjectName) {
				CLASS_OBJECT -> OBJECT
				CLASS_STRING -> STRING
				CLASS_CLASS -> CLASS
				CLASS_THROWABLE -> THROWABLE
				CLASS_EXCEPTION -> EXCEPTION
				else -> ObjectType(cleanObjectName)
			}
		}

		/** 创建泛型类型（如 "T extends Comparable"） */
		fun genericType(type: String): ArgType = GenericType(type)

		/** 创建带单个上界的泛型类型 */
		fun genericType(type: String, extendType: ArgType): ArgType = GenericType(type, extendType)

		/** 创建带多个上界（联合约束）的泛型类型 */
		fun genericType(type: String, extendTypes: List<ArgType>): ArgType = GenericType(type, extendTypes)

		/** 创建无界通配符 "?" */
		fun wildcard(): ArgType = WildcardType(OBJECT, WildcardBound.UNBOUND)

		/** 创建通配符类型（? extends T 或 ? super T） */
		fun wildcard(obj: ArgType, bound: WildcardBound): ArgType = WildcardType(obj, bound)

		/** 创建泛型对象类型 List<String> */
		fun generic(obj: ArgType, generics: List<ArgType>): ArgType {
			require(obj.isObject()) { "Expected Object as ArgType, got: $obj" }
			return GenericObject((obj as ObjectType).objName, generics)
		}

		/** 变长参数版本 */
		fun generic(obj: ArgType, vararg generics: ArgType): ArgType = generic(obj, Arrays.asList(*generics))

		/** 从类名字符串创建泛型类型 */
		fun generic(obj: String, generics: List<ArgType>): ArgType = GenericObject(cleanObjectName(obj), generics)

		/** 单个泛型参数快捷方式 */
		fun generic(obj: String, generic: ArgType): ArgType = generic(obj, Collections.singletonList(generic))

		/** Java vararg 兼容：generic(String, ArgType...) */
		fun generic(obj: String, vararg generics: ArgType): ArgType = generic(obj, Arrays.asList(*generics))

		/** 创建外部类$内部类的泛型类型 */
		fun outerGeneric(genericOuterType: ArgType, innerType: ArgType): ArgType = OuterGenericObject(genericOuterType as ObjectType, innerType as ObjectType)

		/** 创建一维数组类型 */
		@JvmOverloads
		fun array(vtype: ArgType, dimension: Int = 1): ArgType {
			require(dimension >= 1) { "dimension must be at least 1" }
			if (dimension == 1) return ArrayArg(vtype)
			var arrType: ArgType = vtype
			repeat(dimension) { arrType = ArrayArg(arrType) }
			return arrType
		}

		/** 创建未知类型（表示可能是多个类型之一） */
		fun unknown(vararg types: PrimitiveType): ArgType = UnknownArg(Arrays.copyOf(types, types.size))

		// ==================== 工具方法 ====================

		/** 从基本类型转换为 ArgType */
		fun convertFromPrimitiveType(primitiveType: PrimitiveType): ArgType = when (primitiveType) {
			PrimitiveType.BOOLEAN -> BOOLEAN
			PrimitiveType.CHAR -> CHAR
			PrimitiveType.BYTE -> BYTE
			PrimitiveType.SHORT -> SHORT
			PrimitiveType.INT -> INT
			PrimitiveType.FLOAT -> FLOAT
			PrimitiveType.LONG -> LONG
			PrimitiveType.DOUBLE -> DOUBLE
			PrimitiveType.OBJECT -> OBJECT
			PrimitiveType.ARRAY -> OBJECT_ARRAY
			PrimitiveType.VOID -> VOID
		}

		/** 从 DEX 类型字符串解析（Ljava/lang/String;、[I、V 等） */
		fun parse(type: String?): ArgType {
			require(!type.isNullOrBlank()) { "Failed to parse type string: $type" }
			val f = type[0]
			return when (f) {
				'L' -> `object`(type)
				'T' -> genericType(type.substring(1, type.length - 1))
				'[' -> array(parse(type.substring(1)))
				else -> if (type.length == 1) parse(f) else throw JadxRuntimeException("Unknown type string: \"$type\"")
			}
		}

		/** 从单个字符解析基本类型 */
		fun parse(f: Char): ArgType = when (f) {
			'Z' -> BOOLEAN
			'B' -> BYTE
			'C' -> CHAR
			'S' -> SHORT
			'I' -> INT
			'J' -> LONG
			'F' -> FLOAT
			'D' -> DOUBLE
			'V' -> VOID
			else -> throw JadxRuntimeException("Unknown type char: '$f' (0x${Integer.toHexString(f.code)})")
		}

		/** 尝试解析类别名 */
		fun tryToResolveClassAlias(root: RootNode, type: ArgType): ArgType {
			if (type.isGenericType()) return type
			if (type.isArray()) {
				val rootType = type.getArrayRootElement()
				val aliasType = tryToResolveClassAlias(root, rootType)
				if (aliasType == rootType) return type
				return array(aliasType, type.getArrayDimension())
			}
			if (type.isObject()) {
				val wildcardType = type.getWildcardType()
				if (wildcardType != null) {
					return WildcardType(tryToResolveClassAlias(root, wildcardType), checkNotNull(type.getWildcardBound()))
				}
				val clsInfo = ClassInfo.fromName(root, type.getObject())
				val baseType = if (clsInfo.hasAlias()) `object`(clsInfo.aliasFullName) else type
				if (!type.isGeneric()) return baseType
				type.getGenericTypes()?.let { generics ->
					return GenericObject(baseType.getObject(), tryToResolveClassAlias(root, generics))
				}
			}
			return type
		}

		fun tryToResolveClassAlias(root: RootNode, types: List<ArgType>): List<ArgType> = map(types) { t ->
			tryToResolveClassAlias(root, t)
		}

		/** 判断是否需要类型转换 */
		fun isCastNeeded(root: RootNode, from: ArgType, to: ArgType): Boolean {
			if (from == to) return false
			val result = root.typeCompare.compareTypes(from, to)
			return !result.isNarrow()
		}

		/** 判断 instanceof 是否成立 */
		fun isInstanceOf(root: RootNode, type: ArgType, of: ArgType): Boolean {
			if (type == of) return true
			if (!type.isObject() || !of.isObject()) return false
			return checkNotNull(root.getClsp()).isImplements(type.getObject(), of.getObject())
		}

		/** 判断类是否已知 */
		fun isClsKnown(root: RootNode, cls: ArgType): Boolean {
			if (cls.isObject()) return checkNotNull(root.getClsp()).isClsKnown(cls.getObject())
			return false
		}
	}

	// ==================== 抽象方法（子类实现） ====================

	/** 类型是否完全确定（非未知类型） */
	open fun isTypeKnown(): Boolean = false

	/** 获取基本类型（仅基本类型和数组返回非 null） */
	open fun getPrimitiveType(): PrimitiveType? = null

	/** 是否为基本类型 */
	open fun isPrimitive(): Boolean = false

	/** 获取对象类名（仅对象类型有效） */
	open fun getObject(): String = throw UnsupportedOperationException("ArgType.getObject(), call class: ${this.javaClass}")

	/** 是否为对象类型 */
	open fun isObject(): Boolean = false

	/** 是否为泛型相关类型 */
	open fun isGeneric(): Boolean = false

	/** 是否为类型变量（T extends X） */
	open fun isGenericType(): Boolean = false

	/** 获取泛型参数列表 */
	open fun getGenericTypes(): List<ArgType>? = null

	/** 获取泛型上界列表 */
	open fun getExtendTypes(): List<ArgType> = emptyList()

	/** 设置泛型上界（仅 GenericType 可变） */
	open fun setExtendTypes(extendTypes: List<ArgType>) {}

	/** 获取通配符的实际类型 */
	open fun getWildcardType(): ArgType? = null

	/** 获取通配符边界类型 */
	open fun getWildcardBound(): WildcardBound? = null

	/** 是否为通配符 */
	open fun isWildcard(): Boolean = false

	/** 获取外部类类型（内部类场景） */
	open fun getOuterType(): ArgType? = null

	/** 获取内部类类型 */
	open fun getInnerType(): ArgType? = null

	/** 是否为数组类型 */
	open fun isArray(): Boolean = false

	/** 获取数组维度 */
	open fun getArrayDimension(): Int = 0

	/** 获取数组元素类型 */
	open fun getArrayElement(): ArgType? = null

	/** 获取数组根元素类型（去掉所有 []） */
	open fun getArrayRootElement(): ArgType = this

	/** 是否包含指定基本类型 */
	abstract fun contains(type: PrimitiveType): Boolean

	/** 从未知类型中选择一个具体类型 */
	abstract fun selectFirst(): ArgType?

	/** 获取可能的类型列表 */
	abstract fun getPossibleTypes(): Array<PrimitiveType>

	// ==================== 内部类实现 ====================

	/** 已知类型的抽象基类（基本类型 + 对象类型） */
	protected abstract class KnownType : ArgType() {
		override fun isTypeKnown(): Boolean = true

		override fun contains(type: PrimitiveType): Boolean = getPrimitiveType() == type

		override fun selectFirst(): ArgType? = null

		override fun getPossibleTypes(): Array<PrimitiveType> = arrayOf()
	}

	/** 基本类型实现 */
	private class PrimitiveArg(private val type: PrimitiveType) : KnownType() {
		init {
			hash = type.hashCode()
		}

		override fun getPrimitiveType(): PrimitiveType = type

		override fun isPrimitive(): Boolean = true

		override fun internalEquals(obj: Any): Boolean = type == (obj as PrimitiveArg).type

		override fun toString(): String = type.toString()
	}

	/** 对象类型实现 */
	protected open class ObjectType(open val objName: String) : KnownType() {
		init {
			hash = objName.hashCode()
		}

		override fun getObject(): String = objName

		override fun isObject(): Boolean = true

		override fun getPrimitiveType(): PrimitiveType = PrimitiveType.OBJECT

		override fun internalEquals(obj: Any): Boolean = objName == (obj as ObjectType).objName

		override fun toString(): String = objName
	}

	/** 泛型类型变量 T extends Comparable */
	protected class GenericType(objName: String, internal var extendTypes_: List<ArgType>) : ObjectType(objName) {
		constructor(objName: String) : this(objName, emptyList())
		constructor(objName: String, extendType: ArgType) : this(objName, listOf(extendType))

		override fun isGenericType(): Boolean = true

		override fun getExtendTypes(): List<ArgType> = extendTypes_

		override fun setExtendTypes(extendTypes: List<ArgType>) {
			extendTypes_ = extendTypes
		}

		override fun internalEquals(obj: Any): Boolean = super.internalEquals(obj) && extendTypes_ == (obj as GenericType).extendTypes_

		override fun toString(): String = if (extendTypes_.isEmpty()) {
			objName
		} else {
			"$objName extends ${listToString(extendTypes_, " & ")}"
		}
	}

	/** 通配符边界枚举 */
	enum class WildcardBound(val num: Int, val str: String) {
		EXTENDS(1, "? extends "), // 上界 ? extends A
		UNBOUND(0, "?"), // 无界 ?
		SUPER(-1, "? super "), // 下界 ? super A
		;

		companion object {
			fun getByNum(num: Int): WildcardBound = when (num) {
				0 -> UNBOUND
				1 -> EXTENDS
				else -> SUPER
			}
		}
	}

	/** 通配符类型实现 */
	protected class WildcardType(type: ArgType, bound: WildcardBound) : ObjectType(CLASS_OBJECT) {
		private val type: ArgType = requireNotNull(type)
		private val bound: WildcardBound = requireNotNull(bound)

		override fun isWildcard(): Boolean = true
		override fun isGeneric(): Boolean = true
		override fun getWildcardType(): ArgType = type
		override fun getWildcardBound(): WildcardBound = bound

		override fun internalEquals(obj: Any): Boolean = super.internalEquals(obj) && bound == (obj as WildcardType).bound && type == obj.type

		override fun toString(): String = if (bound == WildcardBound.UNBOUND) bound.str else "${bound.str}$type"
	}

	/** 泛型对象 List<String> */
	protected class GenericObject(objName: String, private val generics: List<ArgType>) : ObjectType(objName) {
		init {
			hash = calcHash()
		}

		private fun calcHash(): Int = objName.hashCode() + 31 * generics.hashCode()

		override fun isGeneric(): Boolean = true
		override fun getGenericTypes(): List<ArgType> = generics

		override fun internalEquals(obj: Any): Boolean = super.internalEquals(obj) && generics == (obj as GenericObject).generics

		override fun toString(): String = "$objName<${listToString(generics)}>"
	}

	/** 外部类$内部类的泛型 */
	protected class OuterGenericObject(outerType_: ObjectType, innerType_: ObjectType) : ObjectType("${outerType_.objName}$${innerType_.objName}") {

		private val outerType_: ObjectType = outerType_
		private val innerType_: ObjectType = innerType_

		init {
			hash = calcHash()
		}

		private fun calcHash(): Int = objName.hashCode() + 31 * (outerType_.hashCode() + 31 * innerType_.hashCode())

		override fun isGeneric(): Boolean = true
		override fun getGenericTypes(): List<ArgType>? = innerType_.getGenericTypes()
		override fun getOuterType(): ArgType = outerType_
		override fun getInnerType(): ArgType = innerType_

		override fun internalEquals(obj: Any): Boolean = super.internalEquals(obj) && outerType_ == (obj as OuterGenericObject).getOuterType() && innerType_ == obj.getInnerType()

		override fun toString(): String = "${outerType_}$${innerType_}"
	}

	/** 数组类型实现 */
	private class ArrayArg(private val arrayElement_: ArgType) : KnownType() {
		init {
			hash = arrayElement_.hashCode()
		}

		override fun getArrayElement(): ArgType = arrayElement_
		override fun isArray(): Boolean = true
		override fun getPrimitiveType(): PrimitiveType = PrimitiveType.ARRAY
		override fun isTypeKnown(): Boolean = arrayElement_.isTypeKnown()
		override fun selectFirst(): ArgType? = arrayElement_.selectFirst()?.let { array(it) }
		override fun getPossibleTypes(): Array<PrimitiveType> = arrayOf(PrimitiveType.ARRAY)
		override fun getArrayDimension(): Int = 1 + arrayElement_.getArrayDimension()
		override fun getArrayRootElement(): ArgType = arrayElement_.getArrayRootElement()

		override fun internalEquals(other: Any): Boolean = arrayElement_ == (other as ArrayArg).getArrayElement()

		override fun toString(): String = "$arrayElement_[]"
	}

	/** 未知类型（可能是多个类型之一） */
	private class UnknownArg(private val possibleTypes: Array<PrimitiveType>) : ArgType() {
		init {
			hash = Arrays.hashCode(possibleTypes)
		}

		override fun getPossibleTypes(): Array<PrimitiveType> = possibleTypes
		override fun isTypeKnown(): Boolean = false

		override fun contains(type: PrimitiveType): Boolean = possibleTypes.any { it == type }

		override fun selectFirst(): ArgType? {
			if (contains(PrimitiveType.OBJECT)) return OBJECT
			if (contains(PrimitiveType.ARRAY)) return array(OBJECT)
			return primitive(possibleTypes[0])
		}

		override fun internalEquals(obj: Any): Boolean = Arrays.equals(possibleTypes, (obj as UnknownArg).possibleTypes)

		override fun toString(): String = if (possibleTypes.size == PrimitiveType.values().size) {
			"??"
		} else {
			"??[${arrayToStr<PrimitiveType>(possibleTypes)}]"
		}
	}

	// ==================== 实例工具方法 ====================

	/** 是否可以作为对象使用 */
	fun canBeObject(): Boolean = isObject() || (!isTypeKnown() && contains(PrimitiveType.OBJECT))

	/** 是否可以作为数组使用 */
	fun canBeArray(): Boolean = isArray() || (!isTypeKnown() && contains(PrimitiveType.ARRAY))

	/** 是否可以作为指定基本类型使用 */
	fun canBePrimitive(primitiveType: PrimitiveType): Boolean = (isPrimitive() && getPrimitiveType() == primitiveType) || (!isTypeKnown() && contains(primitiveType))

	/** 是否可以是任意数字类型 */
	fun canBeAnyNumber(): Boolean {
		if (isPrimitive()) return getPrimitiveType()?.isObjectOrArray() != true
		return getPossibleTypes().any { !it.isObjectOrArray() }
	}

	/** 获取类型占用的寄存器数量（long/double=2，其他=1） */
	val regCount: Int get() {
		if (isPrimitive()) {
			val type = getPrimitiveType()
			return if (type == PrimitiveType.LONG || type == PrimitiveType.DOUBLE) 2 else 1
		}
		return if (!isTypeKnown()) 0 else 1
	}

	/** 是否包含泛型 */
	fun containsGeneric(): Boolean {
		if (isGeneric() || isGenericType()) return true
		if (isArray()) {
			val arrayElement = getArrayElement()
			if (arrayElement != null && arrayElement.containsGeneric()) return true
		}
		return false
	}

	/** 是否包含类型变量 */
	fun containsTypeVariable(): Boolean {
		if (isGenericType()) return true
		val wildcardType = getWildcardType()
		if (wildcardType != null && wildcardType.containsTypeVariable()) return true
		if (isGeneric()) {
			getGenericTypes()?.any { it.containsTypeVariable() }?.let { if (it) return true }
			getOuterType()?.let { if (it.containsTypeVariable()) return true }
		}
		if (isArray()) {
			val arrayElement = getArrayElement()
			if (arrayElement != null && arrayElement.containsTypeVariable()) return true
		}
		return false
	}

	/** 是否为 void */
	fun isVoid(): Boolean = isPrimitive() && getPrimitiveType() == PrimitiveType.VOID

	/** 递归访问所有子类型（用于遍历泛型/数组嵌套） */
	fun <R> visitTypes(visitor: (ArgType) -> R?): R? {
		val r = visitor(this)
		if (r != null) return r
		if (isArray()) {
			val arrayElement = getArrayElement()
			if (arrayElement != null) {
				val result = arrayElement.visitTypes(visitor)
				if (result != null) return result
			}
		}
		val wildcardType = getWildcardType()
		if (wildcardType != null) {
			val res = wildcardType.visitTypes(visitor)
			if (res != null) return res
		}
		getGenericTypes()?.forEach { genericType ->
			val res = genericType.visitTypes(visitor)
			if (res != null) return res
		}
		return null
	}

	override fun toString(): String = "ARG_TYPE"

	override fun hashCode(): Int = hash

	protected abstract fun internalEquals(obj: Any): Boolean

	override fun equals(obj: Any?): Boolean {
		if (this === obj) return true
		if (obj == null) return false
		if (hash != obj.hashCode()) return false
		if (javaClass != obj.javaClass) return false
		return internalEquals(obj)
	}
}
