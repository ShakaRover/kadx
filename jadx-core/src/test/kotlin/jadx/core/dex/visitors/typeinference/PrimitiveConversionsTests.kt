package jadx.core.dex.visitors.typeinference

import jadx.api.JadxArgs
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PrimitiveConversionsTests {

	@DisplayName("Check conversion of numeric types")
	@ParameterizedTest(name = "{0} -> {1} (should be {2})")
	@MethodSource("provideArgsForNumericConversionsTest")
	fun testNumericConversions(firstType: ArgType, secondType: ArgType, expectedResult: TypeCompareEnum) {
		assertThat(comparator.compareTypes(firstType, secondType)).isEqualTo(expectedResult)
	}

	@DisplayName("Ensure that `boolean` is not convertible to other primitive types")
	@ParameterizedTest(name = "{0} <-> boolean")
	@MethodSource("providePrimitiveTypesWithVoid")
	fun testBooleanConversions(type: ArgType) {
		val expectedResult = if (type == ArgType.BOOLEAN) TypeCompareEnum.EQUAL else TypeCompareEnum.CONFLICT
		assertThat(comparator.compareTypes(type, ArgType.BOOLEAN)).isEqualTo(expectedResult)
		assertThat(comparator.compareTypes(ArgType.BOOLEAN, type)).isEqualTo(expectedResult)
	}

	@DisplayName("Ensure that `void` is not convertible to other primitive types")
	@ParameterizedTest(name = "{0} <-> void")
	@MethodSource("providePrimitiveTypesWithVoid")
	fun testVoidConversions(type: ArgType) {
		val expectedResult = if (type == ArgType.VOID) TypeCompareEnum.EQUAL else TypeCompareEnum.CONFLICT
		assertThat(comparator.compareTypes(type, ArgType.VOID)).isEqualTo(expectedResult)
		assertThat(comparator.compareTypes(ArgType.VOID, type)).isEqualTo(expectedResult)
	}

	companion object {
		private lateinit var comparator: TypeCompare

		@BeforeAll
		@JvmStatic
		fun before() {
			val args = JadxArgs()
			val root = RootNode(args)
			comparator = TypeCompare(root)
		}

		@JvmStatic
		private fun provideArgsForNumericConversionsTest(): Stream<Arguments> = Stream.of(
			Arguments.of(ArgType.BYTE, ArgType.BYTE, TypeCompareEnum.EQUAL),
			Arguments.of(ArgType.BYTE, ArgType.SHORT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.BYTE, ArgType.CHAR, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.BYTE, ArgType.INT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.BYTE, ArgType.LONG, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.BYTE, ArgType.FLOAT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.BYTE, ArgType.DOUBLE, TypeCompareEnum.NARROW),

			Arguments.of(ArgType.SHORT, ArgType.BYTE, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.SHORT, ArgType.SHORT, TypeCompareEnum.EQUAL),
			Arguments.of(ArgType.SHORT, ArgType.CHAR, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.SHORT, ArgType.INT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.SHORT, ArgType.LONG, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.SHORT, ArgType.FLOAT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.SHORT, ArgType.DOUBLE, TypeCompareEnum.NARROW),

			Arguments.of(ArgType.CHAR, ArgType.BYTE, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.CHAR, ArgType.SHORT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.CHAR, ArgType.CHAR, TypeCompareEnum.EQUAL),
			Arguments.of(ArgType.CHAR, ArgType.INT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.CHAR, ArgType.LONG, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.CHAR, ArgType.FLOAT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.CHAR, ArgType.DOUBLE, TypeCompareEnum.NARROW),

			Arguments.of(ArgType.INT, ArgType.BYTE, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.INT, ArgType.SHORT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.INT, ArgType.CHAR, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.INT, ArgType.INT, TypeCompareEnum.EQUAL),
			Arguments.of(ArgType.INT, ArgType.LONG, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.INT, ArgType.FLOAT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.INT, ArgType.DOUBLE, TypeCompareEnum.NARROW),

			Arguments.of(ArgType.LONG, ArgType.BYTE, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.LONG, ArgType.SHORT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.LONG, ArgType.CHAR, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.LONG, ArgType.INT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.LONG, ArgType.LONG, TypeCompareEnum.EQUAL),
			Arguments.of(ArgType.LONG, ArgType.FLOAT, TypeCompareEnum.NARROW),
			Arguments.of(ArgType.LONG, ArgType.DOUBLE, TypeCompareEnum.NARROW),

			Arguments.of(ArgType.FLOAT, ArgType.BYTE, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.FLOAT, ArgType.SHORT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.FLOAT, ArgType.CHAR, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.FLOAT, ArgType.INT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.FLOAT, ArgType.LONG, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.FLOAT, ArgType.FLOAT, TypeCompareEnum.EQUAL),
			Arguments.of(ArgType.FLOAT, ArgType.DOUBLE, TypeCompareEnum.NARROW),

			Arguments.of(ArgType.DOUBLE, ArgType.BYTE, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.DOUBLE, ArgType.SHORT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.DOUBLE, ArgType.CHAR, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.DOUBLE, ArgType.INT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.DOUBLE, ArgType.LONG, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.DOUBLE, ArgType.FLOAT, TypeCompareEnum.WIDER),
			Arguments.of(ArgType.DOUBLE, ArgType.DOUBLE, TypeCompareEnum.EQUAL),
		)

		@JvmStatic
		private fun providePrimitiveTypesWithVoid(): Stream<ArgType> = Stream.of(
			ArgType.BYTE,
			ArgType.SHORT,
			ArgType.CHAR,
			ArgType.INT,
			ArgType.LONG,
			ArgType.FLOAT,
			ArgType.DOUBLE,
			ArgType.BOOLEAN,
			ArgType.VOID,
		)
	}
}
