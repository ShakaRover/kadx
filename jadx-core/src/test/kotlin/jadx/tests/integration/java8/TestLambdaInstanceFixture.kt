package jadx.tests.integration.java8

object TestLambdaInstanceFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.java8;

import java.util.function.Function;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestLambdaInstanceFixture {

	@SuppressWarnings("Convert2MethodRef")
	public static class TestCls {

		public Function<String, Integer> test() {
			return str -> this.call(str);
		}

		public Function<String, Integer> testMthRef() {
			return this::call;
		}

		public Integer call(String str) {
			return Integer.parseInt(str);
		}

		public Function<Integer, String> test2() {
			return num -> num.toString();
		}

		public Function<Integer, String> testMthRef2() {
			return Object::toString;
		}

		public void check() throws Exception {
			assertThat(test().apply("11")).isEqualTo(11);
			assertThat(testMthRef().apply("7")).isEqualTo(7);

			assertThat(test2().apply(15)).isEqualTo("15");
			assertThat(testMthRef2().apply(13)).isEqualTo("13");
		}
	}
}
"""
}
