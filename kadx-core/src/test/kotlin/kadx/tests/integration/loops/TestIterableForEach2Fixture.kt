package kadx.tests.integration.loops

object TestIterableForEach2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import java.io.IOException;
import java.util.List;

public class TestIterableForEach2Fixture {

	public static class TestCls {
		public static String test(final Service service) throws IOException {
			for (Authorization auth : service.getAuthorizations()) {
				if (isValid(auth)) {
					return auth.getToken();
				}
			}
			return null;
		}

		private static boolean isValid(Authorization auth) {
			return false;
		}

		private static class Service {
			public List<Authorization> getAuthorizations() {
				return null;
			}
		}

		private static class Authorization {
			public String getToken() {
				return "";
			}
		}
	}
}
"""
}
