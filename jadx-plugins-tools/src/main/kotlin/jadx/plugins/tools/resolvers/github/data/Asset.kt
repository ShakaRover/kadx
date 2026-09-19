package jadx.plugins.tools.resolvers.github.data

import com.google.gson.annotations.SerializedName

class Asset {
	var id: Int = 0
	var name: String? = null
	var size: Long = 0L

	@SerializedName("browser_download_url")
	var downloadUrl: String? = null

	@SerializedName("created_at")
	var createdAt: String? = null

	override fun toString(): String = "$name, size: ${String.format("%.2fMB", size / 1024.0 / 1024.0)}, url: $downloadUrl, date: $createdAt"
}
