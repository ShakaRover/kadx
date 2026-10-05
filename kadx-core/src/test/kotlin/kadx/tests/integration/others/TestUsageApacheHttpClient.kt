package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Apache HttpClient 使用检测：类中引用 `org.apache.http.client.HttpClient` 时，
 * 应记录使用旧版 Apache HTTP 的 Gradle 信息。
 */
class TestUsageApacheHttpClient : SmaliTest() {

	// @formatter:off
	/*
		package others;
		import org.apache.http.client.HttpClient;

		public class HttpClientTest {
			private HttpClient httpClient;
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		val cls = getClassNodeFromSmali()
		assertThat(cls.root().gradleInfoStorage.isUseApacheHttpLegacy).isTrue()
	}
}
