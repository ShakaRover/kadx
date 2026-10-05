package kadx.tests.integration.deobf.a

object TestNegativeRenameConditionFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.deobf.a;

public class TestNegativeRenameConditionFixture {

	public static class TestCls {

		@SuppressWarnings("checkstyle:TypeName")
		public interface a {

			@SuppressWarnings("checkstyle:MethodName")
			void a();
		}

		public void test(a a) {
			a.a();
		}
	}
}
"""
}
