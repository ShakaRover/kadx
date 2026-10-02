package jadx.tests.integration.conditions

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 复杂条件 2：嵌套 if 与逻辑与应合并为单个条件表达式。
 */
class TestComplexIf2 : SmaliTest() {

	// @formatter:off
	/*
		public void test() {
			if (this.isSaved) {
				throw new RuntimeException("Error");
			}
			if (LoaderUtils.isContextLoaderAvailable()) {
				this.savedContextLoader = LoaderUtils.getContextClassLoader();
				ClassLoader loader = this;
				if (this.project != null && "simple".equals(this.project)) {
					loader = getClass().getClassLoader();
				}
				LoaderUtils.setContextClassLoader(loader);
				this.isSaved = true;
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmaliWithPkg("conditions", "TestComplexIf2")).code()
			.containsOne("if (this.project != null && \"simple\".equals(this.project)) {")
	}
}
