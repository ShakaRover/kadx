package jadx.tests.integration.arith;

public class TestArith4Fixture {

	public static class TestCls {
		public static byte test(byte b) {
			int k = b & 7;
			return (byte) (((b & 255) >>> (8 - k)) | (b << k));
		}

		public static int test2(String str) {
			int k = 'a' | str.charAt(0);
			return (1 - k) & (1 + k);
		}
	}
}
