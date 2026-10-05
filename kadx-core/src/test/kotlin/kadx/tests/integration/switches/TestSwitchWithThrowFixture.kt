package kadx.tests.integration.switches

object TestSwitchWithThrowFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class TestSwitchWithThrowFixture {

	public static class TestCls {
		public int test(int i) {
			if (i != 0) {
				switch (i % 4) {
					case 1:
						throw new IllegalStateException("1");
					case 2:
						throw new IllegalStateException("2");
					default:
						throw new IllegalStateException("Other");
				}
			}
			System.out.println("0");
			return -1;
		}

		public void check() {
			assertThat(test(0)).isEqualTo(-1);
			assertThat(catchThrowable(() -> test(1)))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("1");
			assertThat(catchThrowable(() -> test(3)))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Other");
		}
	}
}
"""
}
