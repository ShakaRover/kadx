package jadx.tests.integration.others;

import java.net.MalformedURLException;
import java.net.URL;

public class TestFieldInitInTryCatchFixture {

	public static class TestCls {
		public static final URL A;

		static {
			try {
				A = new URL("http://www.example.com/");
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static class TestCls2 {
		public static final URL[] A;

		static {
			try {
				A = new URL[] { new URL("http://www.example.com/") };
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static class TestCls3 {
		public static final String[] A;

		static {
			try {
				A = new String[] { "a" };
				// Note: follow code will not be extracted:
				// a = new String[]{new String("a")};
				new URL("http://www.example.com/");
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
