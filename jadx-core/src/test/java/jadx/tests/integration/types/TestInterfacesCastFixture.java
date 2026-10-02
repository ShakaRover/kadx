package jadx.tests.integration.types;

import java.io.Closeable;
import java.io.IOException;

public class TestInterfacesCastFixture {

	public static class TestCls {

		public Runnable test(Closeable obj) throws IOException {
			return (Runnable) obj;
		}
	}
}
