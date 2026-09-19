package jadx.plugins.tools.resolvers.github

data class LocationInfo(
	val owner: String,
	val project: String,
	val artifactPrefix: String,
	val version: String? = null,
) {
	constructor(owner: String, project: String, artifactPrefix: String) : this(owner, project, artifactPrefix, null)
}
