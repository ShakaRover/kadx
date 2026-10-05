package kadx.tests.integration.loops

object TestDoWhileBreak2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import java.util.Arrays;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDoWhileBreak2Fixture {

	public static class TestCls {
		Iterator<String> it;

		@SuppressWarnings("ConstantConditions")
		public Object test() {
			String obj;
			do {
				obj = this.it.next();
				if (obj == null) {
					return obj; // 'return null' or 'break' also fine
				}
			} while (this.it.hasNext());
			return obj;
		}

		public void check() {
			this.it = Arrays.asList("a", "b").iterator();
			assertThat(test()).isEqualTo("b");

			this.it = Arrays.asList("a", "b", null).iterator();
			assertThat(test()).isEqualTo(null);
		}
	}
}
"""
}
