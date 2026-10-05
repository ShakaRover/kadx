package kadx.tests.integration.enums

object TestEnums3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEnums3Fixture {

	public static class TestCls {

		private static int three = 3;

		public enum Numbers {
			ONE(1), TWO(2), THREE(three), FOUR(three + 1);

			private final int num;

			Numbers(int n) {
				this.num = n;
			}

			public int getNum() {
				return num;
			}
		}

		public void check() {
			assertThat(Numbers.ONE.getNum()).isEqualTo(1);
			assertThat(Numbers.THREE.getNum()).isEqualTo(3);
			assertThat(Numbers.FOUR.getNum()).isEqualTo(4);
		}
	}
}
"""
}
