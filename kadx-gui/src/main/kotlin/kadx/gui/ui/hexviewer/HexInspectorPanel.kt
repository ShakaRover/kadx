package kadx.gui.ui.hexviewer

import org.apache.commons.lang3.ArrayUtils
import org.exbin.auxiliary.binary_data.BinaryData
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.nio.ByteBuffer
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 十六进制视图右侧的「值检查器」面板。
 *
 * **做什么**：把光标所在偏移处的若干字节，按多种格式（有/无符号整数、浮点、十六进制、
 * 八进制、二进制）同时展示出来；勾选「Little endian」后会按小端序解读。
 *
 * 移植自 kadx 原有的 HexInspectorPanel。
 */
class HexInspectorPanel : JPanel() {

	private val formatters: MutableList<ValueFormatter> = ArrayList()

	private var data: BinaryData? = null
	private var offset: Int? = null
	private var isLittleEndian = false
	private var row = 0

	init {
		setLayout(GridBagLayout())
		addValueFormat("Signed 8 bit", 1) { b -> b.get().toInt().toString() }
		addValueFormat("Unsigned 8 bit", 1) { b -> (b.get().toInt() and 0xFF).toString() }
		addValueFormat("Signed 16 bit", 2) { b -> b.getShort().toString() }
		addValueFormat("Unsigned 16 bit", 2) { b -> (b.getShort().toInt() and 0xFFFF).toString() }
		addValueFormat("Float 32 bit", 4) { b -> b.getFloat().toString() }
		addValueFormat("Signed 32 bit", 4) { b -> b.getInt().toString() }
		addValueFormat("Unsigned 32 bit", 4) { b -> java.lang.Integer.toUnsignedString(b.getInt()) }
		addValueFormat("Signed 64 bit", 8) { b -> b.getLong().toString() }
		addValueFormat("Float 64 bit", 8) { b -> b.getDouble().toString() }
		addValueFormat("Unsigned 64 bit", 8) { b -> java.lang.Long.toUnsignedString(b.getLong()) }
		addValueFormat("Hexadecimal", 1) { b -> java.lang.Integer.toString(b.get().toInt(), 16) }
		addValueFormat("Octal", 1) { b -> java.lang.Integer.toString(b.get().toInt(), 8) }
		addValueFormat("Binary", 1) { b -> java.lang.Integer.toString(b.get().toInt(), 2) }

		var constraints = getConstraints()
		constraints.gridwidth = 2
		val littleEndianCheckBox = JCheckBox("Little endian", false)
		littleEndianCheckBox.addItemListener { ev ->
			isLittleEndian = ev.stateChange == ItemEvent.SELECTED
			reloadOffset()
		}
		add(littleEndianCheckBox, constraints)

		// 让控件从顶部开始排列（否则 GridBagLayout 会把它们垂直居中）
		constraints = getConstraints()
		constraints.weighty = 1.0
		add(JLabel(" "), constraints)
	}

	fun setOffset(offset: Int) {
		this.offset = offset
		reloadOffset()
	}

	fun setContentData(data: BinaryData) {
		this.data = data
	}

	private fun reloadOffset() {
		val currentOffset = offset
		if (data == null || currentOffset == null) {
			return
		}

		for (i in formatters.indices) {
			val formatter = formatters[i]
			if (canDisplay(currentOffset, formatter.dataSize)) {
				val buffer = decodeByteArray(currentOffset, formatter.dataSize)
				val value = formatter.function.invoke(buffer)
				(getComponent(i * 2 + 1) as JTextField).setText(value)
			}
		}
	}

	private fun getConstraints(): GridBagConstraints {
		val constraints = GridBagConstraints()
		constraints.insets = Insets(5, 5, 5, 5)
		constraints.gridy = row
		row++
		return constraints
	}

	private fun addValueFormat(name: String, dataSize: Int, formatter: (ByteBuffer) -> String) {
		formatters.add(ValueFormatter(dataSize, formatter))

		val constraints = getConstraints()
		constraints.gridx = 0
		constraints.anchor = GridBagConstraints.WEST
		add(JLabel(name), constraints)

		constraints.fill = GridBagConstraints.HORIZONTAL
		constraints.gridx = 1

		val textField = JTextField()
		textField.setEditable(false)

		add(textField, constraints)
	}

	private fun canDisplay(offset: Int, size: Int): Boolean {
		val currentData = data ?: return false
		return offset + size <= currentData.getDataSize()
	}

	private fun decodeByteArray(offset: Int, size: Int): ByteBuffer {
		val chunk = sliceBytes(offset, size)
		if (isLittleEndian) {
			ArrayUtils.reverse(chunk)
		}
		return ByteBuffer.wrap(chunk)
	}

	private fun sliceBytes(offset: Int, size: Int): ByteArray {
		val slice = ByteArray(size)
		data?.copyToArray(offset.toLong(), slice, 0, size)
		return slice
	}

	private class ValueFormatter(val dataSize: Int, val function: (ByteBuffer) -> String)
}
