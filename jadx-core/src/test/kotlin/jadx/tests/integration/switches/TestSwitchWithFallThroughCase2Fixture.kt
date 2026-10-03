package jadx.tests.integration.switches

object TestSwitchWithFallThroughCase2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.switches;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSwitchWithFallThroughCase2Fixture {

	@SuppressWarnings("fallthrough")
	public static class TestCls {
		public String test(int a, boolean b, boolean c) {
			String str = "";
			if (a > 0) {
				switch (a % 4) {
					case 1:
						str += ">";
						if (a == 5 && b) {
							if (c) {
								str += "1";
							} else {
								str += "!c";
							}
							break;
						}
					case 2:
						if (b) {
							str += "2";
						}
						break;
					case 3:
						break;
					default:
						str += "default";
						break;
				}
				str += "+";
			}
			if (b && c) {
				str += "-";
			}
			return str;
		}

		public void check() {
			assertThat(test(5, true, true)).isEqualTo(">1+-");
			assertThat(test(1, true, true)).isEqualTo(">2+-");
			assertThat(test(3, true, true)).isEqualTo("+-");
			assertThat(test(16, true, true)).isEqualTo("default+-");
			assertThat(test(-1, true, true)).isEqualTo("-");
		}
	}
}
"""
}
