package kadx.tests.integration.others

object TestOverrideStaticMethodFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestOverrideStaticMethodFixture {

	public static class TestCls {
		public static class BaseClass {
			public static int a() {
				return 1;
			}
		}

		public static class MyClass extends BaseClass {
			public static int a() {
				return 2;
			}
		}

		public void check() {
			assertThat(BaseClass.a()).isEqualTo(1);
			assertThat(MyClass.a()).isEqualTo(2);
		}
	}
}
"""
}
