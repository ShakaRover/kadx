package jadx.plugins.tools.resolvers.github

import jadx.core.utils.files.FileUtils.streamToString
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Instant

class GithubToolsTest {
	private companion object {
		val LOG = LoggerFactory.getLogger(GithubToolsTest::class.java)
	}

	private lateinit var server: MockWebServer
	private lateinit var githubTools: GithubTools

	@BeforeEach
	fun setup() {
		server = MockWebServer()
		server.start()
		val baseUrl = server.url("/").toString()
		githubTools = GithubTools(baseUrl)
	}

	@AfterEach
	fun close() {
		server.close()
	}

	@Test
	fun getReleaseGood() {
		server.enqueue(
			MockResponse.Builder()
				.body(loadFromResource("plugins-list-good.json"))
				.build(),
		)

		val pluginsList = LocationInfo("jadx-decompiler", "jadx-plugins-list", "list")
		val release = githubTools.getRelease(pluginsList)

		LOG.info("Result release: {}", release)
		org.assertj.core.api.Assertions.assertThat(release.name).isEqualTo("v15")
		org.assertj.core.api.Assertions.assertThat(release.assets).hasSize(1)
	}

	@Test
	fun getReleaseRateLimit() {
		server.enqueue(
			MockResponse.Builder()
				.code(403)
				.addHeader("x-ratelimit-remaining", "0")
				.addHeader("x-ratelimit-reset", Instant.now().plusSeconds(60 * 60).epochSecond.toString())
				.body("{}")
				.build(),
		)

		val pluginsList = LocationInfo("jadx-decompiler", "jadx-plugins-list", "list")
		assertThatThrownBy { githubTools.getRelease(pluginsList) }
			.hasMessageContaining("403")
			.hasMessageContaining("Client Error")
			.hasMessageContaining("rate limit reached")
	}

	private fun loadFromResource(resName: String): String {
		val stream = GithubToolsTest::class.java.getResourceAsStream("/github/$resName")!!
		return try {
			streamToString(stream)
		} catch (e: Exception) {
			throw RuntimeException("Failed to load resource: $resName", e)
		}
	}
}
