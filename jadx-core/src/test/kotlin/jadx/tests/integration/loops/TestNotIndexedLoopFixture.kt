package jadx.tests.integration.loops

object TestNotIndexedLoopFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNotIndexedLoopFixture {

	public static class TestCls {

		public File test(File[] files) {
			File file;
			if (files != null) {
				int length = files.length;
				if (length == 0) {
					file = null;
				} else {
					int i = 0;
					while (true) {
						if (i >= length) {
							file = new File("h");
							break;
						}
						file = files[i];
						if (file.getName().equals("f")) {
							break;
						}
						i++;
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

			assertThat(test(new File[] { new File("a") }).getName()).isEqualTo("h");
		}
	}
}
"""
}
