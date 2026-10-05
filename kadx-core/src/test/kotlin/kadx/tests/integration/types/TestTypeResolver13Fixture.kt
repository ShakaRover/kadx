package kadx.tests.integration.types

object TestTypeResolver13Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestTypeResolver13Fixture {

	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	public static class TestCls {
		private static final Set<?> CONST = new HashSet<>();
		private Map<Set<?>, List<?>> map = new HashMap<>();

		@SuppressWarnings("unchecked")
		public <T> List<T> test(Set<T> type) {
			List<?> obj = this.map.get(type == null ? CONST : type);
			if (obj != null) {
				return (List<T>) obj;
			}
			return null;
		}
	}
}
"""
}
