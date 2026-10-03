package jadx.tests.integration.types

object TestTypeResolver7Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestTypeResolver7Fixture {

	public static class TestCls {
		public void test(boolean a, boolean b) {
			Object obj = null;
			if (a) {
				use(b ? (Exception) getObj() : (Exception) obj);
			} else {
				Runnable r = (Runnable) obj;
				if (b) {
					r = (Runnable) getObj();
				}
				use(r);
			}
		}

		private Object getObj() {
			return null;
		}

		private void use(Exception e) {
		}

		private void use(Runnable r) {
		}
	}
}
"""
}
