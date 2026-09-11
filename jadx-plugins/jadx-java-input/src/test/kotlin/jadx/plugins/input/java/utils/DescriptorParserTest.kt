package jadx.plugins.input.java.utils

import jadx.plugins.input.java.data.JavaMethodRef
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

class DescriptorParserTest {

	@Test
	fun testPrimitives() {
		check("()V", "V")
		check("(I)D", "D", "I")
	}

	@Test
	fun testObjects() {
		check("(Ljava/lang/String;Ljava/lang/Object;)V", "V", "Ljava/lang/String;", "Ljava/lang/Object;")
	}

	private fun check(desc: String, retType: String, vararg argTypes: String) {
		val mthRef = JavaMethodRef()
		try {
			DescriptorParser.fillMethodProto(desc, mthRef)
		} catch (e: Exception) {
			fail("Parse failed for: " + desc, e)
		}

		assertThat(mthRef.getReturnType()).isEqualTo(retType)
		assertThat(mthRef.getArgTypes()).isEqualTo(argTypes.toList())
	}
}
