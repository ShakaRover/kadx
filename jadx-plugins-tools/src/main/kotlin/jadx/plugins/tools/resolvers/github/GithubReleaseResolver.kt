package jadx.plugins.tools.resolvers.github

import jadx.core.utils.ListUtils.filterOnlyOne
import jadx.plugins.tools.data.JadxPluginMetadata
import jadx.plugins.tools.resolvers.IJadxPluginResolver
import jadx.plugins.tools.resolvers.github.data.Asset
import jadx.plugins.tools.resolvers.github.data.Release
import jadx.plugins.tools.utils.PluginUtils.extractVersion
import jadx.plugins.tools.utils.PluginUtils.removePrefix
import java.util.Optional
import java.util.regex.Pattern

class GithubReleaseResolver : IJadxPluginResolver {
	companion object {
		private val VERSION_PATTERN = Pattern.compile("v?\\d+\\.\\d+(\\.\\d+)?")

		private fun parseLocation(locationId: String): LocationInfo? {
			if (!locationId.startsWith("github:")) {
				return null
			}
			val parts = locationId.split(":")
			if (parts.size < 3) {
				return null
			}
			val owner = parts[1]
			val project = parts[2]
			var version: String? = null
			var artifactPrefix = project
			if (parts.size >= 4) {
				val part = parts[3]
				if (VERSION_PATTERN.matcher(part).matches()) {
					version = part
					if (parts.size >= 5) {
						artifactPrefix = parts[4]
					}
				} else {
					artifactPrefix = part
				}
			}
			return LocationInfo(owner, project, artifactPrefix, version)
		}

		private fun searchPluginAsset(assets: List<Asset>, artifactPrefix: String, releaseVersion: String): Asset {
			val assetJar = searchAssetWithExt(assets, artifactPrefix, releaseVersion, ".jar")
			if (assetJar != null) {
				return assetJar
			}
			val assetZip = searchAssetWithExt(assets, artifactPrefix, releaseVersion, ".zip")
			if (assetZip != null) {
				return assetZip
			}
			throw RuntimeException("Release artifact with prefix '$artifactPrefix' not found")
		}

		private fun searchAssetWithExt(assets: List<Asset>, artifactPrefix: String, releaseVersion: String, ext: String): Asset? {
			val artifactName = "$artifactPrefix-$releaseVersion$ext"
			val exactAsset = filterOnlyOne(assets) { it.name == artifactName }
			if (exactAsset != null) {
				return exactAsset
			}
			return filterOnlyOne(assets) { asset ->
				val fileName = asset.name ?: return@filterOnlyOne false
				fileName.startsWith(artifactPrefix) && fileName.endsWith(ext)
			}
		}

		private fun buildLocationIdWithoutVersion(info: LocationInfo): String {
			val baseLocation = "github:${info.owner}:${info.project}"
			if (info.project == info.artifactPrefix) {
				return baseLocation
			}
			return "$baseLocation:${info.artifactPrefix}"
		}
	}

	override fun id(): String = "github"

	override fun isUpdateSupported(): Boolean = true

	override fun resolve(locationId: String): Optional<JadxPluginMetadata> {
		val info = parseLocation(locationId) ?: return Optional.empty()
		val release = GithubTools.fetchRelease(info)
		val metadata = buildMetadata(release, info)
		return Optional.of(metadata)
	}

	override fun resolveVersions(locationId: String, page: Int, perPage: Int): List<JadxPluginMetadata> {
		val info = parseLocation(locationId) ?: return emptyList()
		return GithubTools.fetchReleases(info, page, perPage).map { buildMetadata(it, info) }
	}

	override fun hasVersion(locationId: String): Boolean {
		val locationInfo = parseLocation(locationId)
		return locationInfo != null && locationInfo.version != null
	}

	private fun buildMetadata(release: Release, info: LocationInfo): JadxPluginMetadata {
		val assets = release.assets ?: emptyList()
		var releaseVersion = removePrefix(release.name!!, "v")
		val asset = searchPluginAsset(assets, info.artifactPrefix, releaseVersion)
		if (!asset.name!!.contains(releaseVersion)) {
			val assetVersion = extractVersion(asset.name!!)
			if (assetVersion != null) {
				releaseVersion = assetVersion
			}
		}

		return JadxPluginMetadata().apply {
			this.version = releaseVersion
			this.locationId = buildLocationIdWithoutVersion(info)
			this.path = asset.downloadUrl
		}
	}
}
