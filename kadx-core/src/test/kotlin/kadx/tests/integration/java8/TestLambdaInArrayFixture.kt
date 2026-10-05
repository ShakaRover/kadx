package kadx.tests.integration.java8

object TestLambdaInArrayFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.java8;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestLambdaInArrayFixture {

	public static class TestCls {

		public List<Function<String, Integer>> test() {
			return Arrays.asList(this::call1, this::call2);
		}

		private Integer call1(String s) {
			return null;
		}

		private Integer call2(String s) {
			return null;
		}

		public void check() throws Exception {
			assertThat(test()).hasSize(2);
		}
	}
}
"""
}
