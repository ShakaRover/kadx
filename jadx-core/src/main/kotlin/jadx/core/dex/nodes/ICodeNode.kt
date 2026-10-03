package jadx.core.dex.nodes

import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.info.AccessInfo

interface ICodeNode :
	IDexNode,
	IAttributeNode,
	IUsageInfoNode,
	ICodeNodeRef {
	val declaringClass: ClassNode?

	var accessFlags: AccessInfo
}
