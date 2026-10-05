package kadx.tests.integration.generics

object TestConstructorGenericsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

import java.util.HashMap;
import java.util.Map;

public class TestConstructorGenericsFixture {

	@SuppressWarnings({ "MismatchedQueryAndUpdateOfCollection", "RedundantOperationOnEmptyContainer" })
	public static class TestCls {
		public String test() {
			Map<String, String> map = new HashMap<>();
			return map.get("test");
		}
	}
}
"""
}
