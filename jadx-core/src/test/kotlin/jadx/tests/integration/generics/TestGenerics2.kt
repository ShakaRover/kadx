package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 泛型弱引用：构造器签名、`WeakReference<V>` 局部变量与返回值类型都应正确还原。
 */
class TestGenerics2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestGenerics2Fixture.TestCls::class.java))
			.code()
			.containsOne("public ItemReference(V item, Object objId, ReferenceQueue<? super V> queue) {")
			.containsOne("public V get(Object id) {")
			.containsOne("WeakReference<V> ref = ")
			.containsOne("return ref.get();")
	}

	@Test
	fun testDebug() {
		noDebugInfo()
		assertThat(getClassNode(TestGenerics2Fixture.TestCls::class.java))
			.code()
			.containsOne("ItemReference<V> itemReference = this.items.get(obj);")
			.containsOne("return itemReference.get();")
	}
}
