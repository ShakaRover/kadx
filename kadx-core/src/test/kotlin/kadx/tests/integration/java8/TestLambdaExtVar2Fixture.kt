package kadx.tests.integration.java8

object TestLambdaExtVar2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.java8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestLambdaExtVar2Fixture {

	public static class TestCls {

		public void test(List<String> list) {
			String space = " ";
			list.removeIf(s -> s.equals(space) || s.contains(space));
		}

		public void check() {
			List<String> list = new ArrayList<>(Arrays.asList("a", " ", "b", "r "));
			test(list);
			assertThat(list).isEqualTo(Arrays.asList("a", "b"));
		}
	}
}
"""
}
