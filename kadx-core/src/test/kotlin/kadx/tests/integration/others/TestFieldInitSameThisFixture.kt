package kadx.tests.integration.others

object TestFieldInitSameThisFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestFieldInitSameThisFixture {

	public static class TestCls {
		public final int value;

		public TestCls() {
			this.value = System.identityHashCode(this);
		}

		public TestCls(long ignored) {
			this.value = System.identityHashCode(this);
		}

		public void check() {
			TestCls first = new TestCls();
			TestCls second = new TestCls(0L);
			assertThat(first.value).isEqualTo(System.identityHashCode(first));
			assertThat(second.value).isEqualTo(System.identityHashCode(second));
		}
	}
}
"""
}
