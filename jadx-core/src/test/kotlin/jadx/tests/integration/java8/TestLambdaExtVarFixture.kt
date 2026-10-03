package jadx.tests.integration.java8

object TestLambdaExtVarFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.java8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestLambdaExtVarFixture {

	public static class TestCls {

		public void test(List<String> list, String str) {
			list.removeIf(s -> s.equals(str));
		}

		public void check() {
			List<String> list = new ArrayList<>(Arrays.asList("a", "str", "b"));
			test(list, "str");
			assertThat(list).isEqualTo(Arrays.asList("a", "b"));
		}
	}
}
"""
}
