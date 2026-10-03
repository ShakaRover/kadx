package jadx.tests.integration.arrays

object TestArrayInitFieldFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arrays;

public class TestArrayInitFieldFixture {

	public static class TestCls {
		static byte[] a = new byte[] { 10, 20, 30 };
		byte[] b = new byte[] { 40, 50, 60 };
	}
}
"""
}
