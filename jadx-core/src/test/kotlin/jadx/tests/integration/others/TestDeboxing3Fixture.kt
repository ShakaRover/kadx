package jadx.tests.integration.others

object TestDeboxing3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import java.util.HashMap;
import java.util.Map;

public class TestDeboxing3Fixture {

	public static class TestCls {

		public static class Pair<F, S> {
			public F first;
			public S second;
		}

		private Map<String, Pair<Long, String>> cache = new HashMap<>();

		public boolean test(String id, Long l) {
			if (l == null) {
				l = 900000L;
			}
			Pair<Long, String> pair = this.cache.get(id);
			if (pair == null) {
				return false;
			}
			return pair.first + l > System.currentTimeMillis();
		}
	}
}
"""
}
