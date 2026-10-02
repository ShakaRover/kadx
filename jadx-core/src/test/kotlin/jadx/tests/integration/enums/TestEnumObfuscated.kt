package jadx.tests.integration.enums

import jadx.api.CommentsLevel
import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 混淆过的枚举：`$VLS`、重命名的 `vo()`/`vs()` 都应在还原枚举时被清理，
 * 而自定义的 `values()` 与 `getNum()` 需要保留。
 */
class TestEnumObfuscated : SmaliTest() {
	// @formatter:off
	/*
		public enum TestEnumObfuscated {
			private static final synthetic TestEnumObfuscated[] $VLS = {ONE, TWO};
			public static final TestEnumObfuscated ONE = new TestEnumObfuscated("ONE", 0, 1);
			public static final TestEnumObfuscated TWO = new TestEnumObfuscated("TWO", 1, 2);
			private final int num;

			private TestEnumObfuscated(String str, int i, int i2) {
				super(str, i);
				this.num = i2;
			}

			public static TestEnumObfuscated vo(String str) {
				return (TestEnumObfuscated) Enum.valueOf(TestEnumObfuscated.class, str);
			}

			public static TestEnumObfuscated[] vs() {
				return (TestEnumObfuscated[]) $VLS.clone();
			}

			public synthetic int getNum() {
				return this.num;
			}

			// custom values method
			// should be kept and renamed to avoid collision to enum 'values()' method
			public static int values() {
				return new TestEnumObfuscated[0];
			}

			// usage of renamed 'values()' method, should be renamed back to 'values'
			public static int valuesCount() {
				return vs().length;
			}

			// usage of renamed '$VALUES' field, should be replaced with 'values()' method call
			public static int valuesFieldUse() {
				return $VLS.length;
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		getArgs().renameFlags = mutableSetOf()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("\$VLS")
			.doesNotContain("vo(")
			.doesNotContain("vs(")
			.containsOne("int getNum() {")
	}
}
