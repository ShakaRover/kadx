package kadx.tests.integration.arrays

object TestArrayFill3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arrays;

public class TestArrayFill3Fixture {

	public static class TestCls {
		public byte[] test() {
			return new byte[] { 0, 1, 2 };
		}
	}
}
"""
}
