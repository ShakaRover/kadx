package jadx.tests.integration.java8

object TestLambdaInstance2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.java8;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestLambdaInstance2Fixture {

	public static class TestCls {
		private String field;

		public Runnable test(String str, int i) {
			return () -> call(str, i);
		}

		public void call(String str, int i) {
			field = str + '=' + i;
		}

		public void check() throws Exception {
			field = "";
			test("num", 7).run();
			assertThat(field).isEqualTo("num=7");
		}
	}
}
"""
}
