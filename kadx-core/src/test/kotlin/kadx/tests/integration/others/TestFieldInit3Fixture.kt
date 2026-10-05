package kadx.tests.integration.others

object TestFieldInit3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestFieldInit3Fixture {

	public static class TestCls {

		public abstract static class A {
			public int field = 4;
		}

		public static final class B extends A {
			public B() {
				// IPUT for A.field
				super.field = 7;
			}
		}

		public static final class C extends A {
			public int other = 11;

			public C() {
				// IPUT for C.field not A.field !!!
				this.field = 9;
			}
		}

		public static final class D extends A {
		}

		public void check() {
			assertThat(new B().field).isEqualTo(7);
			assertThat(new C().field).isEqualTo(9);
			assertThat(new C().other).isEqualTo(11);
			assertThat(new D().field).isEqualTo(4);
		}
	}
}
"""
}
