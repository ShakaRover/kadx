package kadx.tests.integration.others

object TestImportsFixture {
	class TestCls1
	class TestCls2

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestImportsFixture {

	public static class TestCls1 {
		public Character.UnicodeBlock test1() {
			return null;
		}

		public Character test2() {
			return 'a';
		}
	}

	public static class TestCls2 {
		public static final class Character {
		}

		public Character test1() {
			return new Character();
		}

		public java.lang.Character test2() {
			return 'c';
		}

		public java.lang.Character.UnicodeBlock test3() {
			return null;
		}
	}
}
"""
}
