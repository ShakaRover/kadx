package kadx.tests.integration.invoke

object TestSuperInvokeFixture {
	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSuperInvokeFixture {

	public class A {
		public int a() {
			return 1;
		}
	}

	public class B extends A {
		@Override
		public int a() {
			return super.a() + 2;
		}

		public int test() {
			return a();
		}
	}

	public void check() {
		assertThat(new B().test()).isEqualTo(3);
	}
}
"""
}
