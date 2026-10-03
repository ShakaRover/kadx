package jadx.tests.integration.inline

object TestSyntheticInline3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inline;

import java.util.function.Function;

public class TestSyntheticInline3Fixture {

	@SuppressWarnings({ "Convert2Lambda", "TrivialFunctionalExpressionUsage" })
	public static class TestCls {
		private String strField;

		private String str() {
			return "a";
		}

		private void test() {
			new Function<String, Void>() {
				@Override
				public Void apply(String s) {
					System.out.println(s + strField + str());
					return null;
				}
			}.apply("c");
		}
	}
}
"""
}
