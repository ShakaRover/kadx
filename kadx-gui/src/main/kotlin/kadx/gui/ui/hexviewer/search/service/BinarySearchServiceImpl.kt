package kadx.gui.ui.hexviewer.search.service

import kadx.gui.ui.hexviewer.search.SearchCondition
import kadx.gui.ui.hexviewer.search.SearchParameters
import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.bined.CharsetStreamTranslator
import org.exbin.bined.CodeAreaUtils
import org.exbin.bined.highlight.swing.SearchCodeAreaColorAssessor
import org.exbin.bined.highlight.swing.SearchMatch
import org.exbin.bined.swing.CodeAreaSwingUtils
import org.exbin.bined.swing.capability.ColorAssessorPainterCapable
import org.exbin.bined.swing.section.SectCodeArea
import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.util.Arrays
import java.util.Locale

/**
 * 二进制搜索服务实现。
 *
 * **做什么**：在 [SectCodeArea] 的数据上执行文本搜索或二进制搜索，
 * 把命中的位置交给 [SearchCodeAreaColorAssessor] 做高亮，并通过回调通知界面。
 *
 * **线程模型**：保持原 Swing 模型——搜索由调用方（[kadx.gui.ui.hexviewer.search.BinarySearch]）
 * 放在后台线程执行；本类内部在文本搜索的长循环里用 [Thread.interrupted] 响应取消。
 *
 * 移植自 ExBin Project（Apache-2.0）。
 */
class BinarySearchServiceImpl(private val codeArea: SectCodeArea) : BinarySearchService {

	private val lastSearchParameters = SearchParameters()

	override fun performFind(searchParameters: SearchParameters, searchStatusListener: BinarySearchService.SearchStatusListener) {
		val searchAssessor = findSearchAssessor()
		val condition = searchParameters.getCondition()
		searchStatusListener.clearStatus()
		if (condition.isEmpty) {
			searchAssessor.clearMatches()
			codeArea.repaint()
			return
		}

		val position = when (searchParameters.getSearchDirection()) {
			SearchParameters.SearchDirection.FORWARD -> {
				if (searchParameters.isSearchFromCursor) {
					codeArea.getActiveCaretPosition().getDataPosition()
				} else {
					0L
				}
			}

			SearchParameters.SearchDirection.BACKWARD -> {
				if (searchParameters.isSearchFromCursor) {
					codeArea.getActiveCaretPosition().getDataPosition() - 1
				} else {
					val searchDataSize = when (condition.getSearchMode()) {
						SearchCondition.SearchMode.TEXT -> condition.getSearchText().length.toLong()
						SearchCondition.SearchMode.BINARY -> checkNotNull(condition.getBinaryData()).getDataSize()
						else -> throw CodeAreaUtils.getInvalidTypeException(condition.getSearchMode())
					}
					codeArea.getDataSize() - searchDataSize
				}
			}

			else -> throw CodeAreaUtils.getInvalidTypeException(searchParameters.getSearchDirection())
		}
		searchParameters.setStartPosition(position)

		when (condition.getSearchMode()) {
			SearchCondition.SearchMode.TEXT -> searchForText(searchParameters, searchStatusListener)
			SearchCondition.SearchMode.BINARY -> searchForBinaryData(searchParameters, searchStatusListener)
			else -> throw CodeAreaUtils.getInvalidTypeException(condition.getSearchMode())
		}
	}

	/** 按二进制字节搜索。 */
	private fun searchForBinaryData(searchParameters: SearchParameters, searchStatusListener: BinarySearchService.SearchStatusListener) {
		val searchAssessor = findSearchAssessor()
		val condition = searchParameters.getCondition()
		var position = searchParameters.getStartPosition()

		val searchData = checkNotNull(condition.getBinaryData())
		val searchDataSize = searchData.getDataSize()
		val data = codeArea.getContentData()

		val foundMatches = ArrayList<SearchMatch>()

		val dataSize = data.getDataSize()
		while (position >= 0 && position <= dataSize - searchDataSize) {
			var matchLength = 0L
			while (matchLength < searchDataSize) {
				if (data.getByte(position + matchLength) != searchData.getByte(matchLength)) {
					break
				}
				matchLength++
			}

			if (matchLength == searchDataSize) {
				val match = SearchMatch()
				match.setPosition(position)
				match.setLength(searchDataSize)
				if (searchParameters.getSearchDirection() == SearchParameters.SearchDirection.BACKWARD) {
					foundMatches.add(0, match)
				} else {
					foundMatches.add(match)
				}

				if (foundMatches.size == MAX_MATCHES_COUNT || searchParameters.getMatchMode() == SearchParameters.MatchMode.SINGLE) {
					break
				}
			}

			position++
		}

		searchAssessor.setMatches(foundMatches)
		if (foundMatches.isNotEmpty()) {
			if (searchParameters.getSearchDirection() == SearchParameters.SearchDirection.BACKWARD) {
				searchAssessor.setCurrentMatchIndex(foundMatches.size - 1)
			} else {
				searchAssessor.setCurrentMatchIndex(0)
			}
			val firstMatch = checkNotNull(searchAssessor.getCurrentMatch())
			codeArea.revealPosition(firstMatch.getPosition(), 0, codeArea.getActiveSection())
		}
		lastSearchParameters.setFromParameters(searchParameters)
		searchStatusListener.setStatus(
			BinarySearchService.FoundMatches(foundMatches.size, if (foundMatches.isEmpty()) -1 else searchAssessor.getCurrentMatchIndex()),
			searchParameters.getMatchMode(),
		)
		codeArea.repaint()
	}

	private fun findSearchAssessor(): SearchCodeAreaColorAssessor = checkNotNull(
		CodeAreaSwingUtils.findColorAssessor(
			codeArea.getPainter() as ColorAssessorPainterCapable,
			SearchCodeAreaColorAssessor::class.java,
		),
	)

	/** 按文本 / 字符搜索。 */
	private fun searchForText(searchParameters: SearchParameters, searchStatusListener: BinarySearchService.SearchStatusListener) {
		val searchAssessor = findSearchAssessor()
		val condition = searchParameters.getCondition()

		var position = searchParameters.getStartPosition()
		val findText = if (searchParameters.isMatchCase) {
			condition.getSearchText()
		} else {
			// 与 Java 的 String.toLowerCase() 一致：使用系统默认 Locale
			condition.getSearchText().lowercase(Locale.getDefault())
		}
		val searchDataSize = findText.length.toLong()
		val data = codeArea.getContentData()

		val foundMatches = ArrayList<SearchMatch>()

		val charset = codeArea.getCharset()
		val maxBytesPerChar: Int = try {
			val encoder: CharsetEncoder = charset.newEncoder()
			encoder.maxBytesPerChar().toInt()
		} catch (ex: UnsupportedOperationException) {
			CharsetStreamTranslator.DEFAULT_MAX_BYTES_PER_CHAR
		}
		val charData = ByteArray(maxBytesPerChar)
		val dataSize = data.getDataSize()
		var lastPosition = position
		while (position >= 0 && position <= dataSize - searchDataSize) {
			var matchCharLength = 0
			var matchLength = 0
			while (matchCharLength < searchDataSize.toInt()) {
				if (Thread.interrupted()) {
					return
				}

				val searchPosition = position + matchLength.toLong()
				var bytesToUse = maxBytesPerChar
				if (searchPosition + bytesToUse > dataSize) {
					bytesToUse = (dataSize - searchPosition).toInt()
				}

				if (searchPosition == lastPosition + 1) {
					System.arraycopy(charData, 1, charData, 0, maxBytesPerChar - 1)
					charData[bytesToUse - 1] = data.getByte(searchPosition + bytesToUse - 1)
				} else if (searchPosition == lastPosition - 1) {
					System.arraycopy(charData, 0, charData, 1, maxBytesPerChar - 1)
					charData[0] = data.getByte(searchPosition)
				} else {
					data.copyToArray(searchPosition, charData, 0, bytesToUse)
				}
				if (bytesToUse < maxBytesPerChar) {
					Arrays.fill(charData, bytesToUse, maxBytesPerChar, 0.toByte())
				}
				lastPosition = searchPosition
				val singleChar = String(charData, charset)[0]

				if (searchParameters.isMatchCase) {
					if (singleChar != findText[matchCharLength]) {
						break
					}
				} else if (Character.toLowerCase(singleChar) != findText[matchCharLength]) {
					break
				}
				val characterLength = singleChar.toString().toByteArray(charset).size
				matchCharLength++
				matchLength += characterLength
			}

			if (matchCharLength == findText.length) {
				val match = SearchMatch()
				match.setPosition(position)
				match.setLength(matchLength.toLong())
				if (searchParameters.getSearchDirection() == SearchParameters.SearchDirection.BACKWARD) {
					foundMatches.add(0, match)
				} else {
					foundMatches.add(match)
				}

				if (foundMatches.size == MAX_MATCHES_COUNT || searchParameters.getMatchMode() == SearchParameters.MatchMode.SINGLE) {
					break
				}
			}

			when (searchParameters.getSearchDirection()) {
				SearchParameters.SearchDirection.FORWARD -> position++
				SearchParameters.SearchDirection.BACKWARD -> position--
				else -> throw CodeAreaUtils.getInvalidTypeException(searchParameters.getSearchDirection())
			}
		}

		if (Thread.interrupted()) {
			return
		}

		searchAssessor.setMatches(foundMatches)
		if (foundMatches.isNotEmpty()) {
			if (searchParameters.getSearchDirection() == SearchParameters.SearchDirection.BACKWARD) {
				searchAssessor.setCurrentMatchIndex(foundMatches.size - 1)
			} else {
				searchAssessor.setCurrentMatchIndex(0)
			}
			val firstMatch = checkNotNull(searchAssessor.getCurrentMatch())
			codeArea.revealPosition(firstMatch.getPosition(), 0, codeArea.getActiveSection())
		}
		lastSearchParameters.setFromParameters(searchParameters)
		searchStatusListener.setStatus(
			BinarySearchService.FoundMatches(foundMatches.size, if (foundMatches.isEmpty()) -1 else searchAssessor.getCurrentMatchIndex()),
			searchParameters.getMatchMode(),
		)
		codeArea.repaint()
	}

	override fun setMatchPosition(matchPosition: Int) {
		val searchAssessor = findSearchAssessor()
		searchAssessor.setCurrentMatchIndex(matchPosition)
		val currentMatch = checkNotNull(searchAssessor.getCurrentMatch())
		codeArea.revealPosition(currentMatch.getPosition(), 0, codeArea.getActiveSection())
		codeArea.repaint()
	}

	override fun performFindAgain(searchStatusListener: BinarySearchService.SearchStatusListener) {
		val searchAssessor = findSearchAssessor()
		val foundMatches = searchAssessor.getMatches()
		val matchesCount = foundMatches.size
		if (matchesCount > 0) {
			when (lastSearchParameters.getMatchMode()) {
				SearchParameters.MatchMode.MULTIPLE -> {
					if (matchesCount > 1) {
						val currentMatchIndex = searchAssessor.getCurrentMatchIndex()
						setMatchPosition(if (currentMatchIndex < matchesCount - 1) currentMatchIndex + 1 else 0)
						searchStatusListener.setStatus(
							BinarySearchService.FoundMatches(foundMatches.size, searchAssessor.getCurrentMatchIndex()),
							lastSearchParameters.getMatchMode(),
						)
					}
				}

				SearchParameters.MatchMode.SINGLE -> {
					when (lastSearchParameters.getSearchDirection()) {
						SearchParameters.SearchDirection.FORWARD -> lastSearchParameters.setStartPosition(foundMatches[0].getPosition() + 1)

						SearchParameters.SearchDirection.BACKWARD -> {
							val match = foundMatches[0]
							lastSearchParameters.setStartPosition(match.getPosition() - 1)
						}
					}

					val condition = lastSearchParameters.getCondition()
					when (condition.getSearchMode()) {
						SearchCondition.SearchMode.TEXT -> searchForText(lastSearchParameters, searchStatusListener)
						SearchCondition.SearchMode.BINARY -> searchForBinaryData(lastSearchParameters, searchStatusListener)
						else -> throw CodeAreaUtils.getInvalidTypeException(condition.getSearchMode())
					}
				}
			}
		}
	}

	override fun getLastSearchParameters(): SearchParameters = lastSearchParameters

	override fun clearMatches() {
		findSearchAssessor().clearMatches()
	}

	companion object {
		private const val MAX_MATCHES_COUNT = 100
	}
}
