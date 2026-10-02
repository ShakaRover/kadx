package jadx.tests.integration.variables;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestVariables5Fixture {

	public static class TestCls {
		public String f = "str//ing";
		private boolean enabled;

		private void testIfInLoop() {
			int i = 0;
			for (int i2 = 0; i2 < f.length(); i2++) {
				char ch = f.charAt(i2);
				if (ch == '/') {
					i++;
					if (i == 2) {
						setEnabled(true);
						return;
					}
				}
			}
			setEnabled(false);
		}

		private void setEnabled(boolean b) {
			this.enabled = b;
		}

		public void check() {
			setEnabled(false);
			testIfInLoop();
			assertThat(enabled).isTrue();
		}
	}
}
