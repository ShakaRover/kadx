package jadx.tests.integration.invoke

object TestSuperInvoke2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.invoke;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestSuperInvoke2Fixture {

	public static class TestCls {
		@Override
		public String toString() {
			return super.toString();
		}

		public void check() {
			assertThat(new TestCls().toString()).containsOne("@");
		}
	}
}
"""
}
