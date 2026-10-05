package kadx.tests.integration.arith

object TestXorFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.arith;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

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
"""
}
