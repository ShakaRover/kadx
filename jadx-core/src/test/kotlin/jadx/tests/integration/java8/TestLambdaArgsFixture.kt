package jadx.tests.integration.java8

object TestLambdaArgsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.java8;

import java.util.function.BiFunction;
import java.util.function.Function;

public class TestLambdaArgsFixture {

	public static class TestCls {
		public void test1() {
			call1(a -> -a);
		}

		public void test2() {
			call2((a, b) -> a - b);
		}

		private void call1(Function<Integer, Integer> func) {
		}

		private void call2(BiFunction<Integer, Integer, Integer> func) {
		}
	}
}
"""
}
