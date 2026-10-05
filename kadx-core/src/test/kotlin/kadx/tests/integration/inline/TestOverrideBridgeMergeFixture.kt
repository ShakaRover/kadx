package kadx.tests.integration.inline

object TestOverrideBridgeMergeFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inline;

import java.util.function.Function;

public class TestOverrideBridgeMergeFixture {

	public static class TestCls implements Function<String, Integer> {
		@Override
		public /* bridge */ /* synthetic */ Integer apply(String str) {
			return test(str);
		}

		public Integer test(String str) {
			return str.length();
		}
	}
}
"""
}
