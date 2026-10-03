package jadx.tests.integration.types

object TestTypeResolver12Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

import java.lang.ref.WeakReference;

public class TestTypeResolver12Fixture {

	public abstract static class TestCls<T> {
		private WeakReference<T> ref;

		public void test(String str) {
			T obj = this.ref.get();
			if (obj != null) {
				call(obj, str);
			}
		}

		public abstract void call(T t, String str);
	}
}
"""
}
