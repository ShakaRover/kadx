package kadx.core.dex.visitors.typeinference

import kadx.NotYetImplementedExtension
import kadx.api.KadxArgs
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.ArgType.WildcardBound
import kadx.core.dex.nodes.RootNode
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory

@ExtendWith(NotYetImplementedExtension::class)
class TypeCompareTest {
	private lateinit var compare: TypeCompare

	@BeforeEach
	fun init() {
		val args = KadxArgs()
		val root = RootNode(args)
		root.loadClasses(emptyList())
		root.initClassPath()
		compare = TypeCompare(root)
	}

	@Test
	fun compareTypes() {
		check(ArgType.INT, ArgType.UNKNOWN_OBJECT, TypeCompareEnum.CONFLICT)
		check(ArgType.INT, ArgType.OBJECT, TypeCompareEnum.CONFLICT)

		firstIsNarrow(ArgType.INT, ArgType.UNKNOWN)
		firstIsNarrow(ArgType.CHAR, ArgType.NARROW_INTEGRAL)

		firstIsNarrow(ArgType.array(ArgType.UNKNOWN), ArgType.UNKNOWN)
		firstIsNarrow(ArgType.array(ArgType.UNKNOWN), ArgType.NARROW)
		firstIsNarrow(ArgType.array(ArgType.CHAR), ArgType.UNKNOWN_OBJECT)
	}

	@Test
	fun compareArrays() {
		firstIsNarrow(ArgType.array(ArgType.CHAR), ArgType.OBJECT)
		firstIsNarrow(ArgType.array(ArgType.CHAR), ArgType.array(ArgType.UNKNOWN))

		firstIsNarrow(ArgType.array(ArgType.OBJECT), ArgType.OBJECT)
		firstIsNarrow(ArgType.array(ArgType.OBJECT), ArgType.array(ArgType.UNKNOWN_OBJECT))
		firstIsNarrow(ArgType.array(ArgType.STRING), ArgType.array(ArgType.UNKNOWN_OBJECT))
		firstIsNarrow(ArgType.array(ArgType.STRING), ArgType.array(ArgType.OBJECT))

		firstIsNarrow(ArgType.UNKNOWN_ARRAY, ArgType.OBJECT)

		firstIsNarrow(ArgType.array(ArgType.BYTE), ArgType.OBJECT)
		firstIsNarrow(ArgType.array(ArgType.array(ArgType.BYTE)), ArgType.array(ArgType.OBJECT))

		check(ArgType.array(ArgType.OBJECT), ArgType.array(ArgType.INT), TypeCompareEnum.CONFLICT)

		val integerType = ArgType.`object`("java.lang.Integer")
		check(ArgType.array(ArgType.OBJECT), ArgType.array(integerType), TypeCompareEnum.WIDER)
		check(ArgType.array(ArgType.INT), ArgType.array(integerType), TypeCompareEnum.CONFLICT)
		check(ArgType.array(ArgType.INT), ArgType.array(ArgType.INT), TypeCompareEnum.EQUAL)

		val wildClass = ArgType.generic(ArgType.CLASS, ArgType.wildcard())
		check(ArgType.array(wildClass), ArgType.array(ArgType.CLASS), TypeCompareEnum.NARROW_BY_GENERIC)
		check(ArgType.array(ArgType.CLASS), ArgType.array(wildClass), TypeCompareEnum.WIDER_BY_GENERIC)
	}

	@Test
	fun compareGenerics() {
		val mapCls = ArgType.`object`("java.util.Map")
		val setCls = ArgType.`object`("java.util.Set")

		val keyType = ArgType.genericType("K")
		val valueType = ArgType.genericType("V")
		val mapGeneric = ArgType.generic(mapCls.getObject(), keyType, valueType)

		check(mapCls, mapGeneric, TypeCompareEnum.WIDER_BY_GENERIC)
		check(mapCls, setCls, TypeCompareEnum.CONFLICT)

		val setGeneric = ArgType.generic(setCls.getObject(), valueType)
		val setWildcard = ArgType.generic(setCls.getObject(), ArgType.wildcard())

		check(setWildcard, setGeneric, TypeCompareEnum.CONFLICT)
		check(setWildcard, setCls, TypeCompareEnum.NARROW_BY_GENERIC)
		// TODO implement compare for wildcard with bounds
	}

	@Test
	fun compareWildCards() {
		val clsWildcard = ArgType.generic(ArgType.CLASS.getObject(), ArgType.wildcard())
		check(clsWildcard, ArgType.CLASS, TypeCompareEnum.NARROW_BY_GENERIC)

		val clsExtendedWildcard = ArgType.generic(ArgType.CLASS.getObject(), ArgType.wildcard(ArgType.STRING, WildcardBound.EXTENDS))
		check(clsWildcard, clsExtendedWildcard, TypeCompareEnum.WIDER)

		val listWildcard = ArgType.generic(
			ArgType.CLASS.getObject(),
			ArgType.wildcard(ArgType.`object`("java.util.List"), WildcardBound.EXTENDS),
		)
		val collWildcard = ArgType.generic(
			ArgType.CLASS.getObject(),
			ArgType.wildcard(ArgType.`object`("java.util.Collection"), WildcardBound.EXTENDS),
		)
		check(listWildcard, collWildcard, TypeCompareEnum.NARROW)

		val collSuperWildcard = ArgType.generic(
			ArgType.CLASS.getObject(),
			ArgType.wildcard(ArgType.`object`("java.util.Collection"), WildcardBound.SUPER),
		)
		check(collSuperWildcard, listWildcard, TypeCompareEnum.CONFLICT)
	}

	@Test
	fun compareGenericWildCards() {
		// 'java.util.List<T>' and 'java.util.List<? extends T>'
		val listCls = ArgType.`object`("java.util.List")
		val genericType = ArgType.genericType("T")
		val genericList = ArgType.generic(listCls, genericType)
		val genericExtendedList = ArgType.generic(listCls, ArgType.wildcard(genericType, WildcardBound.EXTENDS))
		check(genericList, genericExtendedList, TypeCompareEnum.CONFLICT_BY_GENERIC)
	}

	@Test
	fun compareGenericTypes() {
		val vType = ArgType.genericType("V")
		check(vType, ArgType.OBJECT, TypeCompareEnum.NARROW)
		check(vType, ArgType.STRING, TypeCompareEnum.CONFLICT)

		val rType = ArgType.genericType("R")
		check(vType, rType, TypeCompareEnum.CONFLICT)
		check(vType, vType, TypeCompareEnum.EQUAL)

		val tType = ArgType.genericType("T")
		val tStringType = ArgType.genericType("T", ArgType.STRING)

		check(tStringType, ArgType.STRING, TypeCompareEnum.NARROW)
		check(tStringType, ArgType.OBJECT, TypeCompareEnum.NARROW)
		check(tStringType, tType, TypeCompareEnum.NARROW)

		val tObjType = ArgType.genericType("T", ArgType.OBJECT)

		check(tObjType, ArgType.OBJECT, TypeCompareEnum.NARROW)
		check(tObjType, tType, TypeCompareEnum.EQUAL)

		check(tStringType, tObjType, TypeCompareEnum.NARROW)
	}

	@Test
	fun compareGenericTypes2() {
		val npeType = ArgType.`object`("java.lang.NullPointerException")

		// check clsp graph
		check(npeType, ArgType.THROWABLE, TypeCompareEnum.NARROW)
		check(npeType, ArgType.EXCEPTION, TypeCompareEnum.NARROW)
		check(ArgType.EXCEPTION, ArgType.THROWABLE, TypeCompareEnum.NARROW)

		val typeVar = ArgType.genericType("T", ArgType.EXCEPTION) // T extends Exception

		// target checks
		check(ArgType.THROWABLE, typeVar, TypeCompareEnum.WIDER)
		check(ArgType.EXCEPTION, typeVar, TypeCompareEnum.WIDER)
		check(npeType, typeVar, TypeCompareEnum.NARROW)
	}

	@Test
	fun compareOuterGenerics() {
		val hashMapType = ArgType.`object`("java.util.HashMap")
		val innerEntrySetType = ArgType.`object`("EntrySet")
		val firstInstance = ArgType.outerGeneric(ArgType.generic(hashMapType, ArgType.STRING, ArgType.STRING), innerEntrySetType)
		val secondInstance = ArgType.outerGeneric(ArgType.generic(hashMapType, ArgType.OBJECT, ArgType.OBJECT), innerEntrySetType)

		check(firstInstance, secondInstance, TypeCompareEnum.NARROW)
	}

	private fun firstIsNarrow(first: ArgType, second: ArgType) {
		check(first, second, TypeCompareEnum.NARROW)
	}

	private fun check(first: ArgType, second: ArgType, expectedResult: TypeCompareEnum) {
		LOG.debug("Compare: '{}' and '{}', expect: '{}'", first, second, expectedResult)

		assertThat(compare.compareTypes(first, second))
			.`as`("Compare '%s' and '%s'", first, second)
			.isEqualTo(expectedResult)

		assertThat(compare.compareTypes(second, first))
			.`as`("Compare '%s' and '%s'", second, first)
			.isEqualTo(expectedResult.invert())
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(TypeCompareTest::class.java)
	}
}
