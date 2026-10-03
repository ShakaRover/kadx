package jadx.tests.integration.inner

object TestAnonymousClassFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

import java.io.File;
import java.io.FilenameFilter;

public class TestAnonymousClassFixture {

	public static class TestCls {

		public int test() {
			String[] files = new File("a").list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.equals("a");
				}
			});
			return files.length;
		}
	}
}
"""
}
