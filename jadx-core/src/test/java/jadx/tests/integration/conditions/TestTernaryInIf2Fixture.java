package jadx.tests.integration.conditions;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestTernaryInIf2Fixture {

	public static class TestCls {
		private String a = "a";
		private String b = "b";

		public boolean equals(TestCls other) {
			if (this.a == null ? other.a == null : this.a.equals(other.a)) {
				if (this.b == null ? other.b == null : this.b.equals(other.b)) {
					return true;
				}
			}
			return false;
		}

		public void check() {
			TestCls other = new TestCls();
			other.a = "a";
			other.b = "b";
			assertThat(this.equals(other)).isTrue();

			other.b = "not-b";
			assertThat(this.equals(other)).isFalse();

			other.b = null;
			assertThat(this.equals(other)).isFalse();

			this.b = null;
			assertThat(this.equals(other)).isTrue();
		}
	}
}
