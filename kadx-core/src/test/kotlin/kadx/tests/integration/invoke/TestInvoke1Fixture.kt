package kadx.tests.integration.invoke

object TestInvoke1Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

import java.io.IOException;

public class TestInvoke1Fixture {

	public static class TestCls {

		private A is;

		public C test(int start) throws IOException {
			int id = is.readInt32();
			String name = is.readString16Fixed(128);

			long typeStringsOffset = start + is.readInt32();
			long keyStringsOffset = start + is.readInt32();

			String[] types = null;
			if (typeStringsOffset != 0) {
				types = strs();
			}
			String[] keys = null;
			if (keyStringsOffset != 0) {
				keys = strs();
			}

			C pkg = new C(id, name, types, keys);
			if (id == 0x7F) {
				is.readInt32();
			}
			return pkg;
		}

		private String[] strs() {
			return new String[0];
		}

		private static final class C {
			public C(int id, String name, String[] types, String[] keys) {
			}
		}

		private final class A {
			public int readInt32() {
				return 0;
			}

			public String readString16Fixed(int i) {
				return null;
			}
		}
	}
}
"""
}
