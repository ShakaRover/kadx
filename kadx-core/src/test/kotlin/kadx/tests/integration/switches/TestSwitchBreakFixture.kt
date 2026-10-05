package kadx.tests.integration.switches

object TestSwitchBreakFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestSwitchBreakFixture {

	public static class TestCls {
		public String test(int a) {
			String s = "";
			loop: while (a > 0) {
				switch (a % 4) {
					case 1:
						s += "1";
						break;
					case 3:
					case 4:
						s += "4";
						break;
					case 5:
						s += "+";
						break loop;
				}
				s += "-";
				a--;
			}
			return s;
		}

		public void check() {
			assertThat(test(9)).isEqualTo("1--4--1--4--1-");
		}
	}
}
"""
}
