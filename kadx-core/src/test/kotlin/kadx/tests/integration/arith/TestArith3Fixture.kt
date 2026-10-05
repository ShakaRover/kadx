package kadx.tests.integration.arith

object TestArith3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arith;

public class TestArith3Fixture {

	public static class TestCls {
		public int vp;

		public void test(byte[] buffer) {
			int n = ((buffer[3] & 255) + 4) + ((buffer[2] & 15) << 8);
			while (n + 4 < buffer.length) {
				int p = (buffer[n + 2] & 255) + ((buffer[n + 1] & 31) << 8);
				int len = (buffer[n + 4] & 255) + ((buffer[n + 3] & 15) << 8);
				int c = buffer[n] & 255;
				switch (c) {
					case 27:
						this.vp = p;
						break;
				}
				n += len + 5;
			}
		}
	}
}
"""
}
