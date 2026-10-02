package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestDeboxing4Fixture {

	public static class TestCls {

		public boolean test(Integer i) {
			return ((Integer) 1).equals(i);
		}

		public void check() {
			assertThat(test(null)).isFalse();
			assertThat(test(0)).isFalse();
			assertThat(test(1)).isTrue();
		}
	}
}
