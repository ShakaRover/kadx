package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import org.junit.jupiter.api.Test

/**
 * 私有内部类的合成构造器应被移除；反编译结果必须可编译。
 */
class TestInnerClassSyntheticConstructor : IntegrationTest() {

	@Test
	fun test() {
		getClassNode(TestInnerClassSyntheticConstructorFixture::class.java)
		// must compile, no usage of removed synthetic empty class
	}
}
