package kadx.tests.integration.switches

object TestSwitchOverStrings2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestSwitchOverStrings2Fixture {

	public static class TestCls {

		public int test(String str) {
			switch (str) {
				case "branch1":
				case "branch2":
					return 1;
				case "branch3":
				case "branch4":
				default:
					return 0;
			}
		}

		public void check() {
			assertThat(test("branch1")).isEqualTo(1);
			assertThat(test("branch2")).isEqualTo(1);
			assertThat(test("branch3")).isEqualTo(0);
			assertThat(test("branch4")).isEqualTo(0);
			assertThat(test("other")).isEqualTo(0);
			assertThat(test("other2")).isEqualTo(0);
		}
	}
}
"""
}
