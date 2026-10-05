package kadx.tests.integration.switches

object TestSwitchInLoop3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestSwitchInLoop3Fixture {

	@SuppressWarnings("SwitchStatementWithTooFewBranches")
	public static class TestCls {
		public int test(int k) {
			int a = 0;
			while (true) {
				int x = 0; // keep this: force to generate the necessary CFG
				switch (k) {
					case 0:
						return a;
					default:
						a++;
						k >>= 1;
				}
			}
		}

		public void check() {
			assertThat(test(1)).isEqualTo(1);
		}
	}
}
"""
}
