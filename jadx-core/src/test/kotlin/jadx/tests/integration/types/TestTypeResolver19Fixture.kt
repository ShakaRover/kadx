package jadx.tests.integration.types

object TestTypeResolver19Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestTypeResolver19Fixture {

	public static class TestCls {
		public static int[] test(byte[] bArr) {
			int[] iArr = new int[bArr.length];
			for (int i = 0; i < bArr.length; i++) {
				iArr[i] = bArr[i];
			}
			return iArr;
		}

		public static int[] test2(byte[] bArr) {
			int[] iArr = new int[bArr.length];
			for (int i = 0; i < bArr.length; i++) {
				int i2 = bArr[i];
				if (i2 < 0) {
					i2 = (int) ((long) i2 & 0xFFFF_FFFFL);
				}
				iArr[i] = i2;
			}
			return iArr;
		}
	}
}
"""
}
