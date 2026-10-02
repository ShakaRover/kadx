package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * try/catch/finally 对字段赋值：提取与不提取 finally 两种模式下的代码形态。
 */
class TestTryCatchFinally : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestTryCatchFinallyFixture.TestCls::class.java))
			.code()
			.containsOne("this.f = false;")
			.containsOne("exc(obj);")
			.containsOne("} catch (Exception e) {")
			.containsOne("e.printStackTrace();")
			.containsOne("} finally {")
			.containsOne("this.f = true;")
			.containsOne("return this.f;")
			.doesNotContain("boolean z")
	}

	@Test
	fun testWithoutFinally() {
		getArgs().isExtractFinally = false
		assertThat(getClassNode(TestTryCatchFinallyFixture.TestCls::class.java))
			.code()
			.containsOne("exc(obj);")
			.containsOne(indent(3) + "} catch (Exception e) {")
			.containsOne(indent(2) + "} catch (Throwable th) {")
			.containsOne("this.f = false;")
			.countString(3, "this.f = true;")
	}
}
