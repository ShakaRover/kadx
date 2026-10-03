package jadx.tests.integration.loops

object TestLoopDetection5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLoopDetection5Fixture {

	public static class TestCls {

		public String test(String str) {
			Iterator<String> it = getStrings().iterator();
			String otherStr = null;
			while (it.hasNext()) {
				otherStr = it.next();
				if (otherStr.equalsIgnoreCase(str)) {
					break;
				}
			}
			return otherStr;
		}

		private List<String> getStrings() {
			return Arrays.asList("str", "otherStr", "STR", "OTHERSTR");
		}

		public void check() {
			assertThat(test("OTHERSTR")).isEqualTo("otherStr");
		}
	}
}
"""
}
