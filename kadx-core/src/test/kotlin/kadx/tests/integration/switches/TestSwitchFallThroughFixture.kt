package kadx.tests.integration.switches

object TestSwitchFallThroughFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestSwitchFallThroughFixture {

	public static class TestCls {
		public int r;

		@SuppressWarnings("fallthrough")
		public void test(int a) {
			int i = 10;
			switch (a) {
				case 1:
					i = 1000;
					// fallthrough
				case 2:
					r = i;
					break;

				default:
					r = -1;
					break;
			}
			r *= 2;
			System.out.println("in: " + a + ", out: " + r);
		}

		public int testWrap(int a) {
			r = 0;
			test(a);
			return r;
		}

		public void check() {
			assertThat(testWrap(1)).isEqualTo(2000);
			assertThat(testWrap(2)).isEqualTo(20);
			assertThat(testWrap(0)).isEqualTo(-2);
		}
	}
}
"""
}
