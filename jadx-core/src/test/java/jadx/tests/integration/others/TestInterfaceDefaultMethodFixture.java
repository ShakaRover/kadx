package jadx.tests.integration.others;

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
