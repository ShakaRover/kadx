package jadx.tests.integration.loops

object TestIndexedLoopFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class TestIndexedLoopFixture {

	public static class TestCls {

		public File test(File[] files) {
			File file = null;
			if (files != null) {
				int length = files.length;
				if (length == 0) {
					file = null;
				} else {
					for (int i = 0; i < length; i++) {
						file = files[i];
						if (file.getName().equals("f")) {
							break;
						}
					}
				}
			} else {
				file = null;
			}
			if (file != null) {
				file.deleteOnExit();
			}
			return file;
		}

		public void check() {
			assertThat(test(null)).isNull();
			assertThat(test(new File[] {})).isNull();

			File file = new File("f");
			assertThat(test(new File[] { new File("a"), file })).isEqualTo(file);
		}
	}
}
"""
}
