package jadx.tests.integration.arith

object TestSpecialValuesFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arith;

public class TestSpecialValuesFixture {

	public static class TestCls {

		public void test() {
			shorts(Short.MIN_VALUE, Short.MAX_VALUE);
			ints(Integer.MIN_VALUE, Integer.MAX_VALUE);
			longs(Long.MIN_VALUE, Long.MAX_VALUE);

			floats(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
					Float.MIN_VALUE, Float.MAX_VALUE, Float.MIN_NORMAL);

			doubles(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
					Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_NORMAL);
		}

		private void shorts(short... v) {
		}

		private void ints(int... v) {
		}

		private void longs(long... v) {
		}

		private void floats(float... v) {
		}

		private void doubles(double... v) {
		}
	}
}
"""
}
