package jadx.core.dex.nodes

interface IUsageInfoNode {
	val useIn: List<out ICodeNode>
}
