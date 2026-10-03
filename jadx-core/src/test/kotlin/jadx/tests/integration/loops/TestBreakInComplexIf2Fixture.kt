package jadx.tests.integration.loops

object TestBreakInComplexIf2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBreakInComplexIf2Fixture {

	public static class TestCls {

		private int test(List<String> list) {
			int length = 0;
			for (String str : list) {
				if (str.isEmpty() || str.length() > 4) {
					break;
				}
				if (str.equals("skip")) {
					continue;
				}
				if (str.equals("a")) {
					break;
				}
				length++;
			}
			return length;
		}

		public void check() {
			assertThat(test(Arrays.asList("x", "y", "skip", "z", "a"))).isEqualTo(3);
			assertThat(test(Arrays.asList("x", "skip", ""))).isEqualTo(1);
			assertThat(test(Arrays.asList("skip", "y", "12345"))).isEqualTo(1);
		}
	}
}
"""
}
