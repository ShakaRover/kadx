package jadx.tests.integration.conditions;

public class TestCmpOpFixture {

	public static class TestCls {
		public boolean testGT(float a) {
			return a > 3.0f;
		}

		public boolean testLT(float b) {
			return b < 2.0f;
		}

		public boolean testEQ(float c) {
			return c == 1.0f;
		}

		public boolean testNE(float d) {
			return d != 0.0f;
		}

		public boolean testGE(float e) {
			return e >= -1.0f;
		}

		public boolean testLE(float f) {
			return f <= -2.0f;
		}

		public boolean testGT2(float g) {
			return 4.0f > g;
		}

		public boolean testLT2(long h) {
			return 5 < h;
		}

		public boolean testGE2(double i) {
			return 6.5d < i;
		}
	}
}
