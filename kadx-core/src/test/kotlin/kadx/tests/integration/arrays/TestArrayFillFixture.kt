package kadx.tests.integration.arrays

object TestArrayFillFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arrays;

public class TestArrayFillFixture {

	public static class TestCls {

		public String[] method() {
			return new String[] { "1", "2", "3" };
		}
	}
}
"""
}
