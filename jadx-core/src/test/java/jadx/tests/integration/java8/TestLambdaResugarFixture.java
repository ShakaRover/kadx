package jadx.tests.integration.java8;

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
