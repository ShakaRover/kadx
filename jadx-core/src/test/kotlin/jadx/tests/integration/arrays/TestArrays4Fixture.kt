package jadx.tests.integration.arrays

object TestArrays4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arrays;

public class TestArrays4Fixture {

	public static class TestCls {
		char[] payload;

		public TestCls(byte[] bytes) {
			char[] a = toChars(bytes);
			this.payload = new char[a.length];
			System.arraycopy(a, 0, this.payload, 0, bytes.length);
		}

		private static char[] toChars(byte[] bArr) {
			return new char[bArr.length];
		}
	}
}
"""
}
