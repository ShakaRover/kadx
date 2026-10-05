package kadx.tests.integration.enums

object TestEnumsWithCustomInitFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import java.util.HashMap;
import java.util.Map;

public class TestEnumsWithCustomInitFixture {

	public enum TestCls {
		ONE("I"),
		TWO("II"),
		THREE("III");

		public static final Map<String, TestCls> MAP = new HashMap<>();

		static {
			for (TestCls value : values()) {
				MAP.put(value.toString(), value);
			}
		}

		private final String str;

		TestCls(String str) {
			this.str = str;
		}

		public String toString() {
			return str;
		}
	}
}
"""
}
