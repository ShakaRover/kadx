package jadx.tests.integration.others

object TestFloatValueFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class TestFloatValueFixture {

	public static class TestCls {
		public float[] test() {
			float[] fa = { 0.55f };
			fa[0] /= 2;
			return fa;
		}

		public void check() {
			assertThat(test()[0]).isCloseTo(0.275f, within(0.0001f));
		}
	}
}
"""
}
