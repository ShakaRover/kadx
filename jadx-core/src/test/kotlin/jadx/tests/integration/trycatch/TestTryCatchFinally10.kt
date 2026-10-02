package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * finally 中的嵌套 try/catch 关闭资源：不应产生多余的 boolean 变量或 Throwable catch。
 */
class TestTryCatchFinally10 : SmaliTest() {

	// @formatter:off
	/*
		public static String test(Context context, int i) {
			CommonContracts.requireNonNull(context);
			InputStream inputStream = null;
			try {
				inputStream = context.getResources().openRawResource(i);
				Scanner useDelimiter = new Scanner(inputStream).useDelimiter("\\A");
				return useDelimiter.hasNext() ? useDelimiter.next() : "";
			} finally {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (IOException e) {
						l.logException(LogLevel.ERROR, e);
					}
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("boolean z = null;")
			.doesNotContain("} catch (Throwable")
			.containsOne("} finally {")
			.containsOne(".close();")
			.containsOne("} catch (IOException e")
			.containsOne(".logException(")
	}
}
