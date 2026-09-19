package jadx.core.dex.nodes

import jadx.core.dex.info.FieldInfo

/**
 * Common interface for FieldInfo and FieldNode
 */
interface IFieldInfoRef {
	fun getFieldInfo(): FieldInfo
}
