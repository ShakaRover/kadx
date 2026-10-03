package jadx.tests.integration.enums

object TestEnums6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.enums;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestEnums6Fixture {

	public static class TestCls {
		public enum Numbers {
			ZERO,
			ONE(1);

			private final int n;

			Numbers() {
				this(0);
			}

			Numbers(int n) {
				this.n = n;
			}

			public int getN() {
				return n;
			}
		}

		public void check() {
			assertThat(TestCls.Numbers.ZERO.getN()).isEqualTo(0);
			assertThat(TestCls.Numbers.ONE.getN()).isEqualTo(1);
		}
	}
}
"""
}
