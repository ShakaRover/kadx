package jadx.tests.integration.switches;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSwitchWithFallThroughCaseFixture {

	@SuppressWarnings("fallthrough")
	public static class TestCls {
		public String test(int a, boolean b, boolean c) {
			String str = "";
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
					// fallthrough
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
			str += ";";
			return str;
		}

		public void check() {
			assertThat(test(5, true, true)).isEqualTo(">1;");
			assertThat(test(1, true, true)).isEqualTo(">2;");
			assertThat(test(3, true, true)).isEqualTo(";");
			assertThat(test(0, true, true)).isEqualTo("default;");
		}
	}
}
