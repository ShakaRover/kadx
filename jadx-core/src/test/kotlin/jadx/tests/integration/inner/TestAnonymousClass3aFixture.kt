package jadx.tests.integration.inner

object TestAnonymousClass3aFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestAnonymousClass3aFixture {

	public static class TestCls {
		public static class Inner {
			private int f;
			private int r;

			public void test() {
				new Runnable() {
					@Override
					public void run() {
						int a = --Inner.this.f;
						p(a);
					}

					public void p(int a) {
						Inner.this.r = a;
					}
				}.run();
			}
		}

		public void check() {
			Inner inner = new Inner();
			inner.f = 2;
			inner.test();
			assertThat(inner.f).isEqualTo(1);
			assertThat(inner.r).isEqualTo(1);
		}
	}
}
"""
}
