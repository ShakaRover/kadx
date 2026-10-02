package jadx.core.dex.nodes

import jadx.api.data.ICodeData

fun interface ICodeDataUpdateListener {
	fun updated(codeData: ICodeData)
}
