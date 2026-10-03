package jadx.tests.integration.conditions

object TestTernaryOneBranchInConstructorFixture {
	class TestCls
	class TestCls2

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestTernaryOneBranchInConstructorFixture {

	public static class TestCls {
		public TestCls(String str, int i) {
			this(str == null ? 0 : i);
		}

		public TestCls(int i) {
		}
	}

	public static class TestCls2 {
		public TestCls2(String str, int i) {
			this(i == 1 ? str : "", i == 0 ? "" : str);
		}

		public TestCls2(String a, String b) {
		}
	}
}
"""
}
