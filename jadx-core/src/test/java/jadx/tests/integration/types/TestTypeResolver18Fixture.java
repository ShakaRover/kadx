package jadx.tests.integration.types;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class TestTypeResolver18Fixture {

	public static class TestCls<T> {
		private final AtomicReference<T> reference = new AtomicReference<>();

		public void test() {
			T t = this.reference.get();
			if (t instanceof Closeable) {
				try {
					((Closeable) t).close();
				} catch (IOException unused) {
					// ignore
				}
			}
			this.reference.set(null);
		}
	}
}
