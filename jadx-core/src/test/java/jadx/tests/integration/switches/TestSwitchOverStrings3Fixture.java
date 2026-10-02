package jadx.tests.integration.switches;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestSwitchOverStrings3Fixture {

	@SuppressWarnings("SwitchStatementWithTooFewBranches")
	public static class TestCls {
		public int test(String v) {
			switch (v) {
				case "a":
					return 1;
				default:
					switch (v) {
						case "b":
							return 2;
						case "c":
							return 3;
						default:
							return 4;
					}
			}
		}

		public void check() {
			assertThat(test("a")).isEqualTo(1);
			assertThat(test("b")).isEqualTo(2);
			assertThat(test("c")).isEqualTo(3);
			assertThat(test("d")).isEqualTo(4);
		}
	}
}
