package jadx.tests.integration.switches

object TestSwitchInLoop4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.switches;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestSwitchInLoop4Fixture {

	@SuppressWarnings("SwitchStatementWithTooFewBranches")
	public static class TestCls {
		private static boolean test(String s, int start) {
			boolean foundSeparator = false;
			for (int i = start; i < s.length(); i++) {
				char c = s.charAt(i);
				switch (c) {
					case '.':
						foundSeparator = true;
						break;
				}
				if (foundSeparator) {
					break;
				}
			}
			return foundSeparator;
		}

		public void check() {
			assertThat(test("a.b", 0)).isTrue();
			assertThat(test("abc", 1)).isFalse();
		}
	}
}
"""
}
