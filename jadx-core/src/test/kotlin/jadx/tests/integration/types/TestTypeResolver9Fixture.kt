package jadx.tests.integration.types

object TestTypeResolver9Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestTypeResolver9Fixture {

	public static class TestCls {
		public int test(byte b) {
			return 16777216 * b;
		}

		public int test2(byte[] array, int offset) {
			return array[offset] * 128 + (array[offset + 1] & 0xFF);
		}
	}
}
"""
}
