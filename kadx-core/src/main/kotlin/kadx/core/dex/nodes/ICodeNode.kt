package kadx.core.dex.nodes

import kadx.api.metadata.ICodeNodeRef
import kadx.core.dex.attributes.IAttributeNode
import kadx.core.dex.info.AccessInfo

interface ICodeNode :
	IDexNode,
	IAttributeNode,
	IUsageInfoNode,
	ICodeNodeRef {
	val declaringClass: ClassNode?

	var accessFlags: AccessInfo
}
