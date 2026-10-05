package kadx.tests.integration.trycatch

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 多重 catch 中含子类异常时，应只保留 Throwable 一项。
 */
class TestTryCatchMultiException2 : SmaliTest() {

	// @formatter:off
	/*
    public static boolean test() {
        try {
            Class<?> cls = Class.forName("c");
            return ((Boolean) cls.getMethod("b", new Class[0]).invoke(cls, new Object[0])).booleanValue();
        } catch (ClassNotFoundException | NoSuchMethodException | Exception | Throwable unused) {
        	// java compiler don't allow shadow subclasses in multi-catch
        	// in this case leave only Throwable
            return false;
        }
    }
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("} catch (Throwable unused) {")
	}
}
