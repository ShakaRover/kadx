package jadx.core.dex.nodes

import jadx.api.data.ICodeData

interface ICodeDataUpdateListener {
	fun updated(codeData: ICodeData)
}
