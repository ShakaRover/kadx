package jadx.core.dex.nodes

import jadx.api.data.IRenameNode

interface IDexNode : IRenameNode {
	fun typeName(): String

	fun root(): RootNode

	val inputFileName: String?
}
