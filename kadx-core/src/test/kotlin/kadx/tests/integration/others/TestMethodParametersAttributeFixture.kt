package kadx.tests.integration.others

object TestMethodParametersAttributeFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Collectors;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestMethodParametersAttributeFixture {

	public static class TestCls {
		public String test(String paramStr, final int number) {
			return paramStr + number;
		}

		public String paramNames() throws NoSuchMethodException {
			Method testMethod = TestCls.class.getMethod("test", String.class, int.class);
			return Arrays.stream(testMethod.getParameters())
					.map(Parameter::getName)
					.collect(Collectors.joining(", "));
		}

		public void check() throws NoSuchMethodException {
			assertThat(paramNames()).isEqualTo("paramStr, number");
		}
	}
}
"""
}
