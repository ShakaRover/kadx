package kadx.tests.integration.enums

object TestEnums7Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestEnums7Fixture {

	public static class TestCls {
		public enum Numbers {
			ZERO,
			ONE;

			private final int n;

			Numbers() {
				this.n = this.name().equals("ZERO") ? 0 : 1;
			}

			public int getN() {
				return n;
			}
		}

		public void check() {
			assertThat(Numbers.ZERO.getN()).isEqualTo(0);
			assertThat(Numbers.ONE.getN()).isEqualTo(1);
		}
	}
}
"""
}
