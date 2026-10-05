package kadx.tests.integration.others

object TestInterfaceDefaultMethodFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestInterfaceDefaultMethodFixture {

	public static class TestCls {

		@SuppressWarnings("UnnecessaryInterfaceModifier")
		public interface ITest {
			void test1();

			default void test2() {
			}

			static void test3() {
			}

			abstract void test4();
		}
	}
}
"""
}
