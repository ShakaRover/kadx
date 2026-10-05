package kadx.tests.integration.java8

object TestLambdaResugarFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.java8;

import java.util.function.Function;

public class TestLambdaResugarFixture {

	public static class TestCls {
		private String field;

		public void test() {
			call(s -> {
				this.field = s;
				return s.length();
			});
		}

		public void call(Function<String, Integer> func) {
		}
	}
}
"""
}
