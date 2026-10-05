package kadx.core.dex.nodes

import kadx.core.dex.info.FieldInfo

/**
 * Common interface for FieldInfo and FieldNode
 */
interface IFieldInfoRef {
	fun getFieldInfo(): FieldInfo
}
