package jadx.plugins.tools.resolvers.github.data

class Release {
	var id: Int = 0
	var name: String? = null
	var assets: List<Asset>? = null

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(name)
		for (asset in assets ?: emptyList()) {
			sb.append("\n ")
			sb.append(asset)
		}
		return sb.toString()
	}
}
