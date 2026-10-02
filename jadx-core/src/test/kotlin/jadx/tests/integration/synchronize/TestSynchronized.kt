package jadx.tests.integration.synchronize

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * synchronized 方法 / 同步块：应还原为 `synchronized` 关键字，而不是 try/catch(Throwable) 的等价形式。
 */
class TestSynchronized : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestSynchronizedFixture.TestCls::class.java))
			.code()
			.doesNotContain("synchronized (this) {")
			.containsOne("public synchronized boolean test1() {")
			.containsOne("return this.f")
			.containsOne("synchronized (this.o) {")
			.doesNotContain(indent(3) + ';')
			.doesNotContain("try {")
			.doesNotContain("} catch (Throwable th) {")
			.doesNotContain("throw th;")
	}
}
