package jadx.core.dex.visitors.typeinference

import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.ArgType.WildcardBound
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 类型比较器：判断两个 [ArgType] 之间的宽窄/冲突关系。
 *
 * **算法意图**：类型推导的核心判定工具。给定候选类型与边界类型，
 * 需要回答“候选是否满足边界”，因此这里实现了一套带泛型感知的比较规则，
 * 覆盖：未知类型（可能类型集合）、数组、基本类型宽度、对象继承、
 * 泛型变量/通配符等场景。同时提供 [Comparator]，用于在多个候选中选最窄/最宽。
 *
 * **Kotlin 转换说明**：
 * - Java 中对象引用的 `==` 严格改写为 `===`（如 `unknown === ArgType.UNKNOWN`）；
 * - [comparator]/[reversedComparator] 声明为属性，保留 `getXxx()` JVM 方法，
 *   同时兼容已有 Kotlin 调用点 `.comparator` 与 Java 调用点 `.getComparator()`；
 * - 流式 `max` 等逻辑保持原样，未做惯用化改造。
 */
class TypeCompare(private val root: RootNode) {

	val comparator: Comparator<ArgType> = ArgTypeComparator()
	val reversedComparator: Comparator<ArgType> = comparator.reversed()

	fun compareTypes(first: ClassNode, second: ClassNode): TypeCompareEnum = compareObjects(first.getType(), second.getType())

	fun compareTypes(first: ClassInfo, second: ClassInfo): TypeCompareEnum = compareObjects(first.type, second.type)

	fun compareObjects(first: ArgType, second: ArgType): TypeCompareEnum {
		if (first === second || first == second) {
			return TypeCompareEnum.EQUAL
		}
		return compareObjectsNoPreCheck(first, second)
	}

	/**
	 * 比较两个类型，返回“第一个相对第二个”的结果（narrow / wider / conflict）。
	 */
	fun compareTypes(first: ArgType, second: ArgType): TypeCompareEnum {
		if (first === second || first == second) {
			return TypeCompareEnum.EQUAL
		}
		val firstKnown = first.isTypeKnown()
		val secondKnown = second.isTypeKnown()
		if (firstKnown != secondKnown) {
			return if (firstKnown) {
				compareWithUnknown(first, second)
			} else {
				compareWithUnknown(second, first).invert()
			}
		}
		val firstArray = first.isArray()
		val secondArray = second.isArray()
		if (firstArray != secondArray) {
			return if (firstArray) {
				compareArrayWithOtherType(first, second)
			} else {
				compareArrayWithOtherType(second, first).invert()
			}
		}
		// 两边都是数组（此时 firstArray && secondArray）
		if (firstArray) {
			// 递归比较元素类型
			return compareTypes(checkNotNull(first.getArrayElement()), checkNotNull(second.getArrayElement()))
		}
		// 两边类型都未知
		if (!firstKnown) {
			val variantLen = first.getPossibleTypes().size.compareTo(second.getPossibleTypes().size)
			return if (variantLen > 0) TypeCompareEnum.WIDER else TypeCompareEnum.NARROW
		}
		val firstPrimitive = first.isPrimitive()
		val secondPrimitive = second.isPrimitive()

		val firstObj = first.isObject()
		val secondObj = second.isObject()
		if (firstObj && secondObj) {
			return compareObjectsNoPreCheck(first, second)
		} else {
			// 基本类型与对象类型互不兼容
			if (firstObj && secondPrimitive) {
				return TypeCompareEnum.CONFLICT
			}
			if (firstPrimitive && secondObj) {
				return TypeCompareEnum.CONFLICT
			}
		}
		if (firstPrimitive && secondPrimitive) {
			return comparePrimitives(checkNotNull(first.getPrimitiveType()), checkNotNull(second.getPrimitiveType()))
		}

		LOG.warn("Type compare function not complete, can't compare {} and {}", first, second)
		return TypeCompareEnum.CONFLICT
	}

	private fun compareArrayWithOtherType(array: ArgType, other: ArgType): TypeCompareEnum {
		if (!other.isTypeKnown()) {
			return if (other.contains(PrimitiveType.ARRAY)) TypeCompareEnum.NARROW else TypeCompareEnum.CONFLICT
		}
		if (other.isObject()) {
			return if (other == ArgType.OBJECT) TypeCompareEnum.NARROW else TypeCompareEnum.CONFLICT
		}
		if (other.isPrimitive()) {
			return TypeCompareEnum.CONFLICT
		}
		throw JadxRuntimeException("Unprocessed type: $other in array compare")
	}

	private fun compareWithUnknown(known: ArgType, unknown: ArgType): TypeCompareEnum {
		if (unknown === ArgType.UNKNOWN) {
			return TypeCompareEnum.NARROW
		}
		if (unknown === ArgType.UNKNOWN_OBJECT && (known.isObject() || known.isArray())) {
			return TypeCompareEnum.NARROW
		}
		if (known == ArgType.OBJECT && unknown.isArray()) {
			return TypeCompareEnum.WIDER
		}
		val knownPrimitive: PrimitiveType = if (known.isPrimitive()) {
			checkNotNull(known.getPrimitiveType())
		} else if (known.isArray()) {
			PrimitiveType.ARRAY
		} else {
			PrimitiveType.OBJECT
		}
		val possibleTypes = unknown.getPossibleTypes()
		for (possibleType in possibleTypes) {
			if (possibleType == knownPrimitive) {
				return TypeCompareEnum.NARROW
			}
		}
		return TypeCompareEnum.CONFLICT
	}

	private fun compareObjectsNoPreCheck(first: ArgType, second: ArgType): TypeCompareEnum {
		val objectsEquals = first.getObject() == second.getObject()
		val firstGenericType = first.isGenericType()
		val secondGenericType = second.isGenericType()
		if (firstGenericType && secondGenericType && !objectsEquals) {
			return TypeCompareEnum.CONFLICT
		}
		val firstGeneric = first.isGeneric()
		val secondGeneric = second.isGeneric()

		if (firstGenericType || secondGenericType) {
			val firstWildcardType = first.getWildcardType()
			val secondWildcardType = second.getWildcardType()
			if (firstWildcardType != null || secondWildcardType != null) {
				if (firstWildcardType != null && secondGenericType && first.getWildcardBound() == WildcardBound.UNBOUND) {
					return TypeCompareEnum.CONFLICT
				}
				if (firstGenericType && secondWildcardType != null && second.getWildcardBound() == WildcardBound.UNBOUND) {
					return TypeCompareEnum.CONFLICT
				}
			}
			return if (firstGenericType) {
				compareGenericTypeWithObject(first, second)
			} else {
				compareGenericTypeWithObject(second, first).invert()
			}
		}
		if (objectsEquals) {
			if (firstGeneric != secondGeneric) {
				return if (firstGeneric) TypeCompareEnum.NARROW_BY_GENERIC else TypeCompareEnum.WIDER_BY_GENERIC
			}
			// 同一对象上的两个泛型
			if (first.getWildcardBound() != null && second.getWildcardBound() != null) {
				return compareWildcardTypes(first, second)
			}
			val firstGenericTypes = first.getGenericTypes()
			val secondGenericTypes = second.getGenericTypes()
			if (Utils.isEmpty(firstGenericTypes) || Utils.isEmpty(secondGenericTypes)) {
				// 泛型缺失：改为比较外部类类型
				val firstOuterType = first.getOuterType()
				val secondOuterType = second.getOuterType()
				if (firstOuterType != null && secondOuterType != null) {
					return compareTypes(firstOuterType, secondOuterType)
				}
			} else {
				// 逐个比较泛型实参
				val firstGenerics = checkNotNull(firstGenericTypes)
				val secondGenerics = checkNotNull(secondGenericTypes)
				val len = firstGenerics.size
				if (len == secondGenerics.size) {
					for (i in 0 until len) {
						val res = compareTypes(firstGenerics[i], secondGenerics[i])
						if (res != TypeCompareEnum.EQUAL) {
							return res
						}
					}
				}
			}
		}
		val firstIsObjCls = first == ArgType.OBJECT
		if (firstIsObjCls || second == ArgType.OBJECT) {
			return if (firstIsObjCls) TypeCompareEnum.WIDER else TypeCompareEnum.NARROW
		}
		if (ArgType.isInstanceOf(root, first, second)) {
			return TypeCompareEnum.NARROW
		}
		if (ArgType.isInstanceOf(root, second, first)) {
			return TypeCompareEnum.WIDER
		}
		if (!ArgType.isClsKnown(root, first) || !ArgType.isClsKnown(root, second)) {
			return TypeCompareEnum.UNKNOWN
		}
		return TypeCompareEnum.CONFLICT
	}

	private fun compareWildcardTypes(first: ArgType, second: ArgType): TypeCompareEnum {
		val firstWildcardBound = first.getWildcardBound()
		val secondWildcardBound = second.getWildcardBound()
		if (firstWildcardBound == WildcardBound.UNBOUND) {
			return TypeCompareEnum.WIDER
		}
		if (secondWildcardBound == WildcardBound.UNBOUND) {
			return TypeCompareEnum.NARROW
		}
		val wildcardCompare = compareTypes(checkNotNull(first.getWildcardType()), checkNotNull(second.getWildcardType()))
		if (firstWildcardBound == secondWildcardBound) {
			return wildcardCompare
		}
		return TypeCompareEnum.CONFLICT
	}

	private fun compareGenericTypeWithObject(genericType: ArgType, objType: ArgType): TypeCompareEnum {
		if (objType.isGenericType()) {
			return compareTypeVariables(genericType, objType)
		}
		if (objType.isWildcard()) {
			return TypeCompareEnum.CONFLICT_BY_GENERIC
		}
		val rootObject = objType == ArgType.OBJECT
		val extendTypes = genericType.getExtendTypes()
		if (extendTypes.isEmpty()) {
			return if (rootObject) TypeCompareEnum.NARROW else TypeCompareEnum.CONFLICT
		}
		if (extendTypes.contains(objType) || rootObject) {
			return TypeCompareEnum.NARROW
		}
		for (extendType in extendTypes) {
			val res = compareObjectsNoPreCheck(extendType, objType)
			if (!res.isNarrow()) {
				return res
			}
		}
		return TypeCompareEnum.NARROW
	}

	private fun compareTypeVariables(first: ArgType, second: ArgType): TypeCompareEnum {
		if (first.getObject() == second.getObject()) {
			val firstExtendTypes = removeObject(first.getExtendTypes())
			val secondExtendTypes = removeObject(second.getExtendTypes())
			if (firstExtendTypes == secondExtendTypes) {
				return TypeCompareEnum.EQUAL
			}
			val firstExtSize = firstExtendTypes.size
			val secondExtSize = secondExtendTypes.size
			if (firstExtSize == 0) {
				return TypeCompareEnum.WIDER
			}
			if (secondExtSize == 0) {
				return TypeCompareEnum.NARROW
			}
			if (firstExtSize == 1 && secondExtSize == 1) {
				return compareTypes(firstExtendTypes[0], secondExtendTypes[0])
			}
		}
		return TypeCompareEnum.CONFLICT
	}

	private fun removeObject(extendTypes: List<ArgType>): List<ArgType> {
		if (extendTypes.contains(ArgType.OBJECT)) {
			if (extendTypes.size == 1) {
				return emptyList()
			}
			val result = ArrayList(extendTypes)
			result.remove(ArgType.OBJECT)
			return result
		}
		return extendTypes
	}

	private fun comparePrimitives(type1: PrimitiveType, type2: PrimitiveType): TypeCompareEnum {
		if (type1 == PrimitiveType.BOOLEAN || type2 == PrimitiveType.BOOLEAN) {
			return if (type1 == type2) TypeCompareEnum.EQUAL else TypeCompareEnum.CONFLICT
		}

		if (type1 == PrimitiveType.VOID || type2 == PrimitiveType.VOID) {
			return if (type1 == type2) TypeCompareEnum.EQUAL else TypeCompareEnum.CONFLICT
		}

		if (type1 == PrimitiveType.BYTE && type2 == PrimitiveType.CHAR) {
			return TypeCompareEnum.WIDER
		}

		if (type1 == PrimitiveType.SHORT && type2 == PrimitiveType.CHAR) {
			return TypeCompareEnum.WIDER
		}

		val type1Width = getTypeWidth(type1)
		val type2Width = getTypeWidth(type2)
		return when {
			type1Width > type2Width -> TypeCompareEnum.WIDER
			type1Width < type2Width -> TypeCompareEnum.NARROW
			else -> TypeCompareEnum.EQUAL
		}
	}

	/** 基本类型的“宽度”序：byte < short < char < int < long < float < double。 */
	private fun getTypeWidth(type: PrimitiveType): Byte = when (type) {
		PrimitiveType.BYTE -> 0

		PrimitiveType.SHORT -> 1

		PrimitiveType.CHAR -> 2

		PrimitiveType.INT -> 3

		PrimitiveType.LONG -> 4

		PrimitiveType.FLOAT -> 5

		PrimitiveType.DOUBLE -> 6

		PrimitiveType.BOOLEAN,
		PrimitiveType.OBJECT,
		PrimitiveType.ARRAY,
		PrimitiveType.VOID,
		-> throw JadxRuntimeException("Type $type should not be here")
	}

	/**
	 * 把 [TypeCompareEnum] 映射成排序权重：wider 更小、narrow 更大。
	 * 这样对候选列表升序排序后，narrow 的候选排在后面（优先选用更窄类型）。
	 */
	private inner class ArgTypeComparator : Comparator<ArgType> {
		override fun compare(a: ArgType, b: ArgType): Int {
			val result = compareTypes(a, b)
			return when (result) {
				TypeCompareEnum.CONFLICT -> -2

				TypeCompareEnum.WIDER, TypeCompareEnum.WIDER_BY_GENERIC -> -1

				TypeCompareEnum.NARROW, TypeCompareEnum.NARROW_BY_GENERIC -> 1

				TypeCompareEnum.EQUAL,
				TypeCompareEnum.UNKNOWN,
				TypeCompareEnum.CONFLICT_BY_GENERIC,
				-> 0
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TypeCompare::class.java)
	}
}
