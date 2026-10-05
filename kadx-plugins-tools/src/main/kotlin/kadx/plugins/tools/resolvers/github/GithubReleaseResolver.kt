package kadx.plugins.tools.resolvers.github

import kadx.core.utils.ListUtils.filterOnlyOne
import kadx.plugins.tools.data.KadxPluginMetadata
import kadx.plugins.tools.resolvers.IKadxPluginResolver
import kadx.plugins.tools.resolvers.github.data.Asset
import kadx.plugins.tools.resolvers.github.data.Release
import kadx.plugins.tools.utils.PluginUtils.extractVersion
import kadx.plugins.tools.utils.PluginUtils.removePrefix
import java.util.regex.Pattern

class GithubReleaseResolver : IKadxPluginResolver {
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

	override val isUpdateSupported: Boolean = true

	override fun resolve(locationId: String): KadxPluginMetadata? {
		val info = parseLocation(locationId) ?: return null
		val release = GithubTools.fetchRelease(info)
		val metadata = buildMetadata(release, info)
		return metadata
	}

	override fun resolveVersions(locationId: String, page: Int, perPage: Int): List<KadxPluginMetadata> {
		val info = parseLocation(locationId) ?: return emptyList()
		return GithubTools.fetchReleases(info, page, perPage).map { buildMetadata(it, info) }
	}

	override fun hasVersion(locationId: String): Boolean {
		val locationInfo = parseLocation(locationId)
		return locationInfo != null && locationInfo.version != null
	}

	private fun buildMetadata(release: Release, info: LocationInfo): KadxPluginMetadata {
		val assets = release.assets ?: emptyList()
		var releaseVersion = removePrefix(checkNotNull(release.name), "v")
		val asset = searchPluginAsset(assets, info.artifactPrefix, releaseVersion)
		if (!checkNotNull(asset.name).contains(releaseVersion)) {
			val assetVersion = extractVersion(checkNotNull(asset.name))
			if (assetVersion != null) {
				releaseVersion = assetVersion
			}
		}

		return KadxPluginMetadata().apply {
			this.version = releaseVersion
			this.locationId = buildLocationIdWithoutVersion(info)
			this.path = asset.downloadUrl
		}
	}
}
