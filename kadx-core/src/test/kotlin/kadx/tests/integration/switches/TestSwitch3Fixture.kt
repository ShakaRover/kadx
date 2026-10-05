package kadx.tests.integration.switches

object TestSwitch3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestSwitch3Fixture {

	public static class TestCls {
		private int i;

		void test(int a) {
			switch (a) {
				case 1:
					i = 1;
					return;
				case 2:
				case 3:
					i = 2;
					return;
				default:
					i = 4;
					break;
			}
			i = 5;
		}

		public void check() {
			test(1);
			assertThat(i).isEqualTo(1);
			test(2);
			assertThat(i).isEqualTo(2);
			test(3);
			assertThat(i).isEqualTo(2);
			test(4);
			assertThat(i).isEqualTo(5);
			test(10);
			assertThat(i).isEqualTo(5);
		}
	}
}
"""
}
