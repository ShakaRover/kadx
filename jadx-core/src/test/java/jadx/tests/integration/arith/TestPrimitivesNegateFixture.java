package jadx.tests.integration.arith;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestPrimitivesNegateFixture {

	@SuppressWarnings("UnnecessaryUnaryMinus")
	public static class TestCls {
		public double test() {
			double[] arr = new double[5];
			arr[0] = -20;
			arr[0] += -79;
			return arr[0];
		}

		public void check() {
			assertThat(test()).isEqualTo(-99);
		}
	}
}
