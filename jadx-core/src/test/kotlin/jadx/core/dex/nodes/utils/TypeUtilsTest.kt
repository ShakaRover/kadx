package jadx.core.dex.nodes.utils

import jadx.api.JadxArgs
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.RootNode
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TypeUtilsTest {
	private lateinit var typeUtils: TypeUtils

	@BeforeEach
	fun init() {
		typeUtils = TypeUtils(RootNode(JadxArgs()))
	}

	@Test
	fun replaceTypeVariablesUsingMap() {
		val typeVar = ArgType.genericType("T")
		val listCls = ArgType.`object`("java.util.List")
		val typeMap: Map<ArgType, ArgType> = mapOf(typeVar to ArgType.STRING)

		replaceTypeVar(typeVar, typeMap, ArgType.STRING)
		replaceTypeVar(ArgType.generic(listCls, typeVar), typeMap, ArgType.generic(listCls, ArgType.STRING))
		replaceTypeVar(ArgType.array(typeVar), typeMap, ArgType.array(ArgType.STRING))
	}

	@Test
	fun replaceTypeVariablesUsingMap2() {
		val kVar = ArgType.genericType("K")
		val vVar = ArgType.genericType("V")
		val mapCls = ArgType.`object`("java.util.Map")
		val entryCls = ArgType.`object`("Entry")
		val typedMap = ArgType.generic(mapCls, kVar, vVar)
		val typedEntry = ArgType.generic(entryCls, kVar, vVar)

		val typeMap: MutableMap<ArgType, ArgType> = HashMap()
		typeMap[kVar] = ArgType.STRING
		typeMap[vVar] = ArgType.EXCEPTION

		val replacedMap = typeUtils.replaceTypeVariablesUsingMap(typedMap, typeMap)
		val replacedEntry = typeUtils.replaceTypeVariablesUsingMap(typedEntry, typeMap)

		replaceTypeVar(ArgType.outerGeneric(typedMap, entryCls), typeMap, ArgType.outerGeneric(checkNotNull(replacedMap), entryCls))
		replaceTypeVar(
			ArgType.outerGeneric(typedMap, typedEntry),
			typeMap,
			ArgType.outerGeneric(checkNotNull(replacedMap), checkNotNull(replacedEntry)),
		)
	}

	private fun replaceTypeVar(typeVar: ArgType, typeMap: Map<ArgType, ArgType>, expected: ArgType) {
		val resultType = typeUtils.replaceTypeVariablesUsingMap(typeVar, typeMap)
		assertThat(resultType)
			.`as`("Replace %s using map %s", typeVar, typeMap)
			.isEqualTo(expected)
	}
}
