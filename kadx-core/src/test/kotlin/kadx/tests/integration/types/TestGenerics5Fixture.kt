package kadx.tests.integration.types

object TestGenerics5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import java.util.HashMap;
import java.util.Map;

public class TestGenerics5Fixture {

	public static class TestCls {
		private InheritableThreadLocal<Map<String, String>> inheritableThreadLocal;

		public void test(String key, String val) {
			if (key == null) {
				throw new IllegalArgumentException("key cannot be null");
			}
			Map<String, String> map = this.inheritableThreadLocal.get();
			if (map == null) {
				map = new HashMap<>();
				this.inheritableThreadLocal.set(map);
			}
			map.put(key, val);
		}

		public void remove(String key) {
			Map<String, String> map = this.inheritableThreadLocal.get();
			if (map != null) {
				map.remove(key);
			}
		}
	}
}
"""
}
