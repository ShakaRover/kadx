package jadx.tests.integration.others

object TestOverridePrivateMethodFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestOverridePrivateMethodFixture {

	public static class TestCls {
		public static class BaseClass {
			private int a() {
				return 1;
			}
		}

		public static class MyClass extends BaseClass {
			public int a() {
				return 2;
			}
		}

		public void check() {
			assertThat(new MyClass().a()).isEqualTo(2);
			assertThat(new BaseClass().a()).isEqualTo(1);
			// TODO: assertThat(((BaseClass) new MyClass()).a()).isEqualTo(1);
		}
	}
}
"""
}
