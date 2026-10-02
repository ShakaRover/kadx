package jadx.tests.integration.arith;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestXorFixture {

	@SuppressWarnings("PointlessBooleanExpression")
	public static class TestCls {
		public boolean test1() {
			return test() ^ true;
		}

		public boolean test2(boolean v) {
			return v ^ true;
		}

		public boolean test() {
			return true;
		}

		public void check() {
			assertThat(test1()).isFalse();
			assertThat(test2(true)).isFalse();
			assertThat(test2(false)).isTrue();
		}
	}
}
