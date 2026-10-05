package kadx.core.dex.nodes

import kadx.api.data.IRenameNode

interface IDexNode : IRenameNode {
	fun typeName(): String

	fun root(): RootNode

	val inputFileName: String?
}
