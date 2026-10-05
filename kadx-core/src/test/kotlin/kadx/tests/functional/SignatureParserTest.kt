package kadx.tests.functional

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.ArgType.WildcardBound
import kadx.core.dex.nodes.parser.SignatureParser
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import java.util.Collections.emptyList
import java.util.Collections.singletonList

/**
 * [SignatureParser] 对类/方法泛型签名的解析结果校验。
 */
class SignatureParserTest {

	@Test
	fun testSimpleTypes() {
		checkType("", null)
		checkType("I", ArgType.INT)
		checkType("[I", ArgType.array(ArgType.INT))
		checkType("Ljava/lang/Object;", ArgType.OBJECT)
		checkType("[Ljava/lang/Object;", ArgType.array(ArgType.OBJECT))
		checkType("[[I", ArgType.array(ArgType.array(ArgType.INT)))
	}

	private fun checkType(str: String, type: ArgType?) {
		assertThat(SignatureParser(str).consumeType()).isEqualTo(type)
	}

	@Test
	fun testGenerics() {
		checkType("TD;", ArgType.genericType("D"))
		checkType("La<TV;Lb;>;", ArgType.generic("La;", ArgType.genericType("V"), ArgType.`object`("b")))
		checkType("La<Lb<Lc;>;>;", ArgType.generic("La;", ArgType.generic("Lb;", ArgType.`object`("Lc;"))))
		checkType("La/b/C<Ld/E<Lf/G;>;>;", ArgType.generic("La/b/C;", ArgType.generic("Ld/E;", ArgType.`object`("Lf/G;"))))
		checkType("La<TD;>.c;", ArgType.outerGeneric(ArgType.generic("La;", ArgType.genericType("D")), ArgType.`object`("c")))
		checkType("La<TD;>.c/d;", ArgType.outerGeneric(ArgType.generic("La;", ArgType.genericType("D")), ArgType.`object`("c.d")))
		checkType("La<Lb;>.c<TV;>;", ArgType.outerGeneric(ArgType.generic("La;", ArgType.`object`("Lb;")), ArgType.generic("c", ArgType.genericType("V"))))
	}

	@Test
	fun testInnerGeneric() {
		val signature = "La<TV;>.LinkedHashIterator<Lb\$c<Ls;TV;>;>;"
		val objectStr = checkNotNull(SignatureParser(signature).consumeType()).getObject()
		assertThat(objectStr).isEqualTo("a\$LinkedHashIterator")
	}

	@Test
	fun testNestedInnerGeneric() {
		val signature = "La<TV;>.I.X;"
		val result = checkNotNull(SignatureParser(signature).consumeType())
		assertThat(result.getObject()).isEqualTo("a\$I\$X")
		// nested 'outerGeneric' objects
		val obj = ArgType.generic("La;", ArgType.genericType("V"))
		assertThat(result).isEqualTo(ArgType.outerGeneric(ArgType.outerGeneric(obj, ArgType.`object`("I")), ArgType.`object`("X")))
	}

	@Test
	fun testNestedInnerGeneric2() {
		// full name in inner class
		val signature = "Lsome/long/pkg/ba<Lsome/pkg/s;>.some/long/pkg/bb<Lsome/pkg/p;Lsome/pkg/n;>;"
		val result = checkNotNull(SignatureParser(signature).consumeType())
		println(result)
		assertThat(result.getObject()).isEqualTo("some.long.pkg.ba\$some.long.pkg.bb")
		val baseObj = ArgType.generic("Lsome/long/pkg/ba;", ArgType.`object`("Lsome/pkg/s;"))
		val innerObj = ArgType.generic("Lsome/long/pkg/bb;", ArgType.`object`("Lsome/pkg/p;"), ArgType.`object`("Lsome/pkg/n;"))
		val obj = ArgType.outerGeneric(baseObj, innerObj)
		assertThat(result).isEqualTo(obj)
	}

	@Test
	fun testWildcards() {
		checkWildcards("*", ArgType.wildcard())
		checkWildcards("+Lb;", ArgType.wildcard(ArgType.`object`("b"), WildcardBound.EXTENDS))
		checkWildcards("-Lb;", ArgType.wildcard(ArgType.`object`("b"), WildcardBound.SUPER))
		checkWildcards("+TV;", ArgType.wildcard(ArgType.genericType("V"), WildcardBound.EXTENDS))
		checkWildcards("-TV;", ArgType.wildcard(ArgType.genericType("V"), WildcardBound.SUPER))

		checkWildcards("**", ArgType.wildcard(), ArgType.wildcard())
		checkWildcards("*Lb;", ArgType.wildcard(), ArgType.`object`("b"))
		checkWildcards("*TV;", ArgType.wildcard(), ArgType.genericType("V"))
		checkWildcards("TV;*", ArgType.genericType("V"), ArgType.wildcard())
		checkWildcards("Lb;*", ArgType.`object`("b"), ArgType.wildcard())

		checkWildcards("***", ArgType.wildcard(), ArgType.wildcard(), ArgType.wildcard())
		checkWildcards("*Lb;*", ArgType.wildcard(), ArgType.`object`("b"), ArgType.wildcard())
	}

	private fun checkWildcards(w: String, vararg types: ArgType) {
		val parsedType = SignatureParser("La<" + w + ">;").consumeType()
		val expectedType = ArgType.generic("La;", *types)
		assertThat(parsedType).isEqualTo(expectedType)
	}

	@Test
	fun testGenericMap() {
		checkGenerics("")
		checkGenerics("<T:Ljava/lang/Object;>", "T", emptyList<ArgType>())
		checkGenerics("<K:Ljava/lang/Object;LongType:Ljava/lang/Object;>", "K", emptyList<ArgType>(), "LongType", emptyList<ArgType>())
		checkGenerics("<ResultT:Ljava/lang/Exception;:Ljava/lang/Object;>", "ResultT", singletonList(ArgType.`object`("java.lang.Exception")))
	}

	@Suppress("UNCHECKED_CAST")
	private fun checkGenerics(g: String, vararg objs: Any) {
		val genericsList = SignatureParser(g).consumeGenericTypeParameters()
		val expectedList = ArrayList<ArgType>()
		var i = 0
		while (i < objs.size) {
			val typeVar = objs[i] as String
			val list = objs[i + 1] as List<ArgType>
			expectedList.add(ArgType.genericType(typeVar, list))
			i += 2
		}
		assertThat(genericsList).isEqualTo(expectedList)
	}

	@Test
	fun testMethodArgs() {
		val argTypes = SignatureParser("(Ljava/util/List<*>;)V").consumeMethodArgs(1)

		assertThat(argTypes).hasSize(1)
		assertThat(argTypes[0]).isEqualTo(ArgType.generic("Ljava/util/List;", ArgType.wildcard()))
	}

	@Test
	fun testMethodArgs2() {
		val argTypes = SignatureParser("(La/b/C<TT;>.d/E;)V").consumeMethodArgs(1)

		assertThat(argTypes).hasSize(1)
		val argType = argTypes[0]
		assertThat(argType.getObject().indexOf('/')).isEqualTo(-1)
		assertThat(argType).isEqualTo(ArgType.outerGeneric(ArgType.generic("La/b/C;", ArgType.genericType("T")), ArgType.`object`("d.E")))
	}

	@Test
	fun testBadGenericMap() {
		assertThatExceptionOfType(KadxRuntimeException::class.java)
			.isThrownBy { SignatureParser("<A:Ljava/lang/Object;B").consumeGenericTypeParameters() }
	}

	@Test
	fun testBadArgs() {
		assertThatExceptionOfType(KadxRuntimeException::class.java)
			.isThrownBy { SignatureParser("(TCONTENT)Lpkg/Cls;").consumeMethodArgs(1) }
	}
}
