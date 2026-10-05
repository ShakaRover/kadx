package kadx.tests.integration.enums

object TestEnums2aFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;
import static kadx.tests.integration.enums.TestEnums2aFixture.TestCls.DoubleOperations.DIVIDE;
import static kadx.tests.integration.enums.TestEnums2aFixture.TestCls.DoubleOperations.TIMES;

public class TestEnums2aFixture {

	public static class TestCls {

		public interface IOps {
			double apply(double x, double y);
		}

		public enum DoubleOperations implements IOps {
			TIMES("*") {
				@Override
				public double apply(double x, double y) {
					return x * y;
				}
			},
			DIVIDE("/") {
				@Override
				public double apply(double x, double y) {
					return x / y;
				}
			};

			private final String op;

			DoubleOperations(String op) {
				this.op = op;
			}

			public String getOp() {
				return op;
			}
		}

		public void check() {
			assertThat(TIMES.getOp()).isEqualTo("*");
			assertThat(DIVIDE.getOp()).isEqualTo("/");

			assertThat(TIMES.apply(2, 3)).isEqualTo(6);
			assertThat(DIVIDE.apply(10, 5)).isEqualTo(2);
		}
	}
}
"""
}
