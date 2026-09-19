package jadx.core.dex.nodes

interface IUsageInfoNode {
	fun getUseIn(): List<out ICodeNode>
}
