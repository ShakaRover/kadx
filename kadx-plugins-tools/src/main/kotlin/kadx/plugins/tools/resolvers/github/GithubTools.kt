package kadx.plugins.tools.resolvers.github

import com.google.gson.reflect.TypeToken
import kadx.core.utils.GsonUtils.buildGson
import kadx.core.utils.files.FileUtils.streamToByteArray
import kadx.plugins.tools.resolvers.github.data.Release
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

class GithubTools(private val baseUrl: String) {
	companion object {
		private val GITHUB_INSTANCE = GithubTools("https://api.github.com")

		private val RELEASE_TYPE: Type = object : TypeToken<Release>() {}.type
		private val RELEASE_LIST_TYPE: Type = object : TypeToken<List<Release>>() {}.type

		fun fetchRelease(info: LocationInfo): Release = GITHUB_INSTANCE.getRelease(info)

		fun fetchReleases(info: LocationInfo, page: Int, perPage: Int): List<Release> = GITHUB_INSTANCE.getReleases(info, page, perPage)
	}

	fun getRelease(info: LocationInfo): Release {
		val projectUrl = "$baseUrl/repos/${info.owner}/${info.project}"
		val version = info.version
		if (version == null) {
			return get("$projectUrl/releases/latest", RELEASE_TYPE)
		}
		val releases = fetchReleases(info, 1, 50)
		return releases.firstOrNull { it.name == version }
			?: throw RuntimeException("Release with version: $version not found. Available versions: ${releases.joinToString(", ") { it.name ?: "" }}")
	}

	fun getReleases(info: LocationInfo, page: Int, perPage: Int): List<Release> {
		val projectUrl = "$baseUrl/repos/${info.owner}/${info.project}"
		val requestUrl = "$projectUrl/releases?page=$page&per_page=$perPage"
		return get(requestUrl, RELEASE_LIST_TYPE)
	}

	private fun <T> get(url: String, type: Type): T {
		var con: HttpURLConnection? = null
		try {
			con = URI.create(url).toURL().openConnection() as HttpURLConnection
			con.requestMethod = "GET"
			con.instanceFollowRedirects = true
			val code = con.responseCode
			if (code != 200) {
				throw RuntimeException(buildErrorDetails(con, url))
			}
			try {
				val reader = InputStreamReader(con.inputStream, UTF_8)
				return buildGson().fromJson(reader, type)
			} catch (e: Exception) {
				throw RuntimeException("Failed to parse response, url: $url", e)
			}
		} catch (e: IOException) {
			throw RuntimeException("Request failed, url: $url", e)
		} finally {
			con?.disconnect()
		}
	}

	private fun buildErrorDetails(con: HttpURLConnection, url: String): String {
		val shortMsg = con.responseMessage ?: ""
		val remainRateLimit = con.getHeaderField("X-RateLimit-Remaining")
		var msg = shortMsg
		if (remainRateLimit == "0") {
			val resetTimeMs = con.getHeaderField("X-RateLimit-Reset")
			val timeStr = if (resetTimeMs != null) "after ${Instant.ofEpochSecond(resetTimeMs.toLong())}" else "in one hour"
			msg += " (rate limit reached, try again $timeStr)"
		}
		val headers = StringBuilder()
		var i = 0
		while (true) {
			val value = con.getHeaderField(i) ?: break
			val key = con.getHeaderFieldKey(i)
			if (key != null) {
				headers.append('\n').append(key).append(": ").append(value)
			}
			i++
		}
		val responseStr = getResponseString(con)
		return "Request failed: ${con.responseCode} $msg\nURL: $url\nHeaders:$headers${if (responseStr.isEmpty()) "" else "\nresponse:\n$responseStr"}"
	}

	private fun getResponseString(con: HttpURLConnection): String {
		try {
			val inStream = con.inputStream
			return String(streamToByteArray(inStream), UTF_8)
		} catch (e: Exception) {
			return ""
		}
	}
}
