package kadx.core.dex.nodes

import kadx.api.data.ICodeData

fun interface ICodeDataUpdateListener {
	fun updated(codeData: ICodeData)
}
