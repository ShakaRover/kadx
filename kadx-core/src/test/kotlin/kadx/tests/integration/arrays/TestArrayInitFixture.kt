package kadx.tests.integration.arrays

object TestArrayInitFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arrays;

public class TestArrayInitFixture {

	public static class TestCls {

		byte[] bytes;

		@SuppressWarnings("unused")
		public void test() {
			byte[] arr = new byte[] { 10, 20, 30 };
		}

		public void test2() {
			bytes = new byte[] { 10, 20, 30 };
		}
	}
}
"""
}
