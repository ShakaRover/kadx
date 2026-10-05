package kadx.tests.integration.java8

object TestLambdaConstructorFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.java8;

import java.util.function.Supplier;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestLambdaConstructorFixture {

	public static class TestCls {

		public Supplier<Exception> test() {
			return RuntimeException::new;
		}

		public void check() throws Exception {
			assertThat(test().get()).isInstanceOf(RuntimeException.class);
		}
	}
}
"""
}
