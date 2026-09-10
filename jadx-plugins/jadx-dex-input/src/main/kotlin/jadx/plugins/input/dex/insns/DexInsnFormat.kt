package jadx.plugins.input.dex.insns

import jadx.api.plugins.input.insns.custom.impl.SwitchPayload
import jadx.plugins.input.dex.DexException
import jadx.plugins.input.dex.insns.payloads.DexArrayPayload
import jadx.plugins.input.dex.sections.SectionReader

/**
 * DEX 指令格式定义：描述每种指令的编码结构（长度、寄存器个数）与操作数解码逻辑。
 *
 * **背景**：DEX 规范把指令分为 10X/12X/21T/35C 等固定格式，本类的每个 [FORMAT_*]
 * 常量对应一种格式；[decode] 从字节流读取操作数填入 [DexInsnData]，[skip] 在不需要
 * 解码时按长度快速跳过。payload 伪指令（switch/数组数据块）额外覆写 [skip] 以匹配
 * 其变长编码。
 *
 * **Kotlin 转换说明**：
 * - 原 Java `public static final DexInsnFormat` → companion object 内 `@JvmField val`，
 *   Java 调用方（如 DexInsnInfo）`DexInsnFormat.FORMAT_12X` 零改动；
 * - 参数名 `in` 是 Kotlin 关键字，重命名为 `reader`（均为位置传参，无命名调用受影响）。
 *
 * @param length 指令长度（单位：16-bit code unit）；-1 表示变长 payload
 * @param regsCount 固定寄存器操作数个数；-1 表示由指令内容决定
 */
public abstract class DexInsnFormat(
	public val length: Int,
	public val regsCount: Int,
) {

	public companion object {
		/** 格式 10X：无操作数（nop）*/
		@JvmField
		public val FORMAT_10X = object : DexInsnFormat(1, 0) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				// no op
			}
		}

		/** 格式 12X：两个 4-bit 寄存器（move）*/
		@JvmField
		public val FORMAT_12X = object : DexInsnFormat(1, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = nibble2(opcodeUnit)
				regs[1] = nibble3(opcodeUnit)
			}
		}

		/** 格式 11N：一个寄存器 + 4-bit 有符号立即数（const/4）*/
		@JvmField
		public val FORMAT_11N = object : DexInsnFormat(1, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = nibble2(opcodeUnit)
				insn.setLiteral(signedNibble3(opcodeUnit).toLong())
			}
		}

		/** 格式 11X：一个 8-bit 寄存器（return-void）*/
		@JvmField
		public val FORMAT_11X = object : DexInsnFormat(1, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
			}
		}

		/** 格式 10T：8-bit 有符号相对跳转（goto）*/
		@JvmField
		public val FORMAT_10T = object : DexInsnFormat(1, 0) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				insn.setTarget(insn.getOffset() + signedByte1(opcodeUnit))
			}
		}

		/** 格式 20T：16-bit 有符号相对跳转（goto/16）*/
		@JvmField
		public val FORMAT_20T = object : DexInsnFormat(2, 0) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				insn.setTarget(insn.getOffset() + reader.readShort())
			}
		}

		/** 格式 20BC：8-bit 立即数 + 16-bit 无符号索引（const/4 变体、check-cast 等）*/
		@JvmField
		public val FORMAT_20BC = object : DexInsnFormat(2, 0) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				insn.setLiteral(byte1(opcodeUnit).toLong())
				insn.setIndex(reader.readUShort())
			}
		}

		/** 格式 22X：一个寄存器 + 一个 16-bit 值（move/16）*/
		@JvmField
		public val FORMAT_22X = object : DexInsnFormat(2, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				regs[1] = reader.readUShort()
			}
		}

		/** 格式 21T：一个寄存器 + 16-bit 有符号相对跳转（if-*）*/
		@JvmField
		public val FORMAT_21T = object : DexInsnFormat(2, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setTarget(insn.getOffset() + reader.readShort())
			}
		}

		/** 格式 21S：一个寄存器 + 16-bit 有符号立即数（const/16）*/
		@JvmField
		public val FORMAT_21S = object : DexInsnFormat(2, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setLiteral(reader.readShort().toLong())
			}
		}

		/**
		 * 格式 21H：一个寄存器 + 高位置位立即数（const/high16、const-wide/16）。
		 * 立即数左移位数由操作码决定：CONST_HIGH16 移 16 位，否则（const-wide/16）移 48 位。
		 */
		@JvmField
		public val FORMAT_21H = object : DexInsnFormat(2, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)

				var literal = reader.readShort().toLong()
				literal = literal shl if (byte0(opcodeUnit) == DexOpcodes.CONST_HIGH16) 16 else 48
				insn.setLiteral(literal)
			}
		}

		/** 格式 21C：一个寄存器 + 16-bit 无符号索引（const-string、new-instance 等）*/
		@JvmField
		public val FORMAT_21C = object : DexInsnFormat(2, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setIndex(reader.readUShort())
			}
		}

		/** 格式 23X：三个寄存器（add-int 等双操作数指令）*/
		@JvmField
		public val FORMAT_23X = object : DexInsnFormat(2, 3) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				val next = reader.readUShort()
				regs[1] = byte0(next)
				regs[2] = byte1(next)
			}
		}

		/** 格式 22B：两个寄存器 + 8-bit 有符号立即数（add-int/lit8）*/
		@JvmField
		public val FORMAT_22B = object : DexInsnFormat(2, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				val next = reader.readUShort()
				regs[1] = byte0(next)
				insn.setLiteral(signedByte1(next).toLong())
			}
		}

		/** 格式 22T：两个寄存器 + 16-bit 有符号相对跳转（if-eq 等）*/
		@JvmField
		public val FORMAT_22T = object : DexInsnFormat(2, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = nibble2(opcodeUnit)
				regs[1] = nibble3(opcodeUnit)
				insn.setTarget(insn.getOffset() + reader.readShort())
			}
		}

		/** 格式 22S：两个寄存器 + 16-bit 有符号立即数（const/16 双寄存器变体）*/
		@JvmField
		public val FORMAT_22S = object : DexInsnFormat(2, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = nibble2(opcodeUnit)
				regs[1] = nibble3(opcodeUnit)
				insn.setLiteral(reader.readShort().toLong())
			}
		}

		/** 格式 22C：两个寄存器 + 16-bit 无符号索引（aget、sget 等）*/
		@JvmField
		public val FORMAT_22C = object : DexInsnFormat(2, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = nibble2(opcodeUnit)
				regs[1] = nibble3(opcodeUnit)
				insn.setIndex(reader.readUShort())
				insn.setLiteral(0L)
			}
		}

		/** 格式 22CS：同 [FORMAT_22C]（const-class 等复用）*/
		@JvmField
		public val FORMAT_22CS: DexInsnFormat = FORMAT_22C

		/** 格式 30T：32-bit 有符号相对跳转（goto/32）*/
		@JvmField
		public val FORMAT_30T = object : DexInsnFormat(3, 0) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				insn.setTarget(insn.getOffset() + reader.readInt())
			}
		}

		/** 格式 32X：两个 16-bit 寄存器（move-wide/16）*/
		@JvmField
		public val FORMAT_32X = object : DexInsnFormat(3, 2) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = reader.readUShort()
				regs[1] = reader.readUShort()
			}
		}

		/** 格式 31I：一个寄存器 + 32-bit 立即数（const）*/
		@JvmField
		public val FORMAT_31I = object : DexInsnFormat(3, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setLiteral(reader.readInt().toLong())
			}
		}

		/** 格式 31T：一个寄存器 + 32-bit 有符号相对跳转（if-eqz 长跳变体）*/
		@JvmField
		public val FORMAT_31T = object : DexInsnFormat(3, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setTarget(insn.getOffset() + reader.readInt())
			}
		}

		/** 格式 31C：一个寄存器 + 32-bit 无符号索引（const-string/jumbo）*/
		@JvmField
		public val FORMAT_31C = object : DexInsnFormat(3, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setIndex(reader.readInt())
			}
		}

		/** 格式 35C：最多 5 个寄存器 + 索引（invoke-*、filled-new-array）*/
		@JvmField
		public val FORMAT_35C = object : DexInsnFormat(3, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				readRegsList(insn, opcodeUnit, reader)
			}
		}

		/** 格式 35MS：同 [FORMAT_35C]（invoke-polymorphic）*/
		@JvmField
		public val FORMAT_35MS: DexInsnFormat = FORMAT_35C

		/** 格式 35MI：同 [FORMAT_35C]（invoke-custom）*/
		@JvmField
		public val FORMAT_35MI: DexInsnFormat = FORMAT_35C

		/** 格式 3RC：连续寄存器范围 + 索引（invoke-super/range 等 range 形式调用）*/
		@JvmField
		public val FORMAT_3RC = object : DexInsnFormat(3, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				readRegsRange(insn, opcodeUnit, reader)
			}
		}

		/** 格式 3RMS：同 [FORMAT_3RC]（invoke-polymorphic/range）*/
		@JvmField
		public val FORMAT_3RMS: DexInsnFormat = FORMAT_3RC

		/** 格式 3RMI：同 [FORMAT_3RC]（invoke-custom/range）*/
		@JvmField
		public val FORMAT_3RMI: DexInsnFormat = FORMAT_3RC

		/** 格式 45CC：最多 5 个寄存器 + 索引 + 类型索引（filled-new-array）*/
		@JvmField
		public val FORMAT_45CC = object : DexInsnFormat(4, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				readRegsList(insn, opcodeUnit, reader)
				insn.setTarget(reader.readUShort())
			}
		}

		/** 格式 4RCC：连续寄存器范围 + 索引 + 类型索引（filled-new-array/range）*/
		@JvmField
		public val FORMAT_4RCC = object : DexInsnFormat(4, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				readRegsRange(insn, opcodeUnit, reader)
				insn.setTarget(reader.readUShort())
			}
		}

		/** 格式 51I：一个寄存器 + 64-bit 立即数（const-wide）*/
		@JvmField
		public val FORMAT_51I = object : DexInsnFormat(5, 1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val regs = insn.getArgsReg()
				regs[0] = byte1(opcodeUnit)
				insn.setLiteral(reader.readLong())
			}
		}

		/**
		 * packed-switch payload：first_key + 连续 targets 数组。
		 * 长度 = size*2+4 个 code unit（size 个 target，key 由 first_key+i 推算）。
		 */
		@JvmField
		public val FORMAT_PACKED_SWITCH_PAYLOAD = object : DexInsnFormat(-1, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val size = reader.readUShort()
				val firstKey = reader.readInt()
				val keys = IntArray(size)
				val targets = IntArray(size)
				for (i in 0 until size) {
					targets[i] = reader.readInt()
					keys[i] = firstKey + i
				}
				insn.setPayload(SwitchPayload(size, keys, targets))
				insn.length = size * 2 + 4
			}

			override fun skip(insn: DexInsnData, reader: SectionReader) {
				val size = reader.readUShort()
				reader.skip(4 + size * 4)
				insn.length = size * 2 + 4
			}
		}

		/** sparse-switch payload：keys 与 targets 两个独立数组 */
		@JvmField
		public val FORMAT_SPARSE_SWITCH_PAYLOAD = object : DexInsnFormat(-1, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val size = reader.readUShort()
				val keys = IntArray(size)
				for (i in 0 until size) {
					keys[i] = reader.readInt()
				}
				val targets = IntArray(size)
				for (i in 0 until size) {
					targets[i] = reader.readInt()
				}
				insn.setPayload(SwitchPayload(size, keys, targets))
				insn.length = size * 4 + 2
			}

			override fun skip(insn: DexInsnData, reader: SectionReader) {
				val size = reader.readUShort()
				reader.skip(size * 8)
				insn.length = size * 4 + 2
			}
		}

		/** fill-array-data payload：按元素大小（1/2/4/8 字节）读取原始数组数据 */
		@JvmField
		public val FORMAT_FILL_ARRAY_DATA_PAYLOAD = object : DexInsnFormat(-1, -1) {
			override fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
				val elemSize = reader.readUShort()
				val size = reader.readInt()
				val data: Any = when (elemSize) {
					1 -> {
						val bytes = reader.readByteArray(size)
						if (size % 2 != 0) {
							// DEX 规范：奇数字节数组后补一个对齐字节
							reader.readUByte()
						}
						bytes
					}

					2 -> {
						val array = ShortArray(size)
						for (i in 0 until size) {
							array[i] = reader.readShort().toShort()
						}
						array
					}

					4 -> {
						val array = IntArray(size)
						for (i in 0 until size) {
							array[i] = reader.readInt()
						}
						array
					}

					8 -> {
						val array = LongArray(size)
						for (i in 0 until size) {
							array[i] = reader.readLong()
						}
						array
					}

					0 -> ByteArray(0)

					else -> throw DexException("Unexpected element size in FILL_ARRAY_DATA_PAYLOAD: $elemSize")
				}
				insn.length = (size * elemSize + 1) / 2 + 4
				insn.setPayload(DexArrayPayload(size, elemSize, data))
			}

			override fun skip(insn: DexInsnData, reader: SectionReader) {
				val elemSize = reader.readUShort()
				val size = reader.readInt()
				if (elemSize == 1) {
					reader.skip(size + size % 2)
				} else {
					reader.skip(size * elemSize)
				}
				insn.length = (size * elemSize + 1) / 2 + 4
			}
		}
	}

	/** 解码指令操作数（寄存器/字面量/索引），结果写入 [insn] */
	public abstract fun decode(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader)

	/** 按格式长度跳过本条指令的操作数字节（不解析内容）*/
	public open fun skip(insn: DexInsnData, reader: SectionReader) {
		if (length == 1) {
			return
		}
		reader.skip((length - 1) * 2)
	}

	/** 读取 35C/45CC 形式的寄存器列表（最多 5 个）与索引 */
	protected fun readRegsList(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
		val regsCount1 = nibble3(opcodeUnit)
		val index = reader.readUShort()
		val rs = reader.readUShort()

		val regs = insn.getArgsReg()
		regs[0] = nibble0(rs)
		regs[1] = nibble1(rs)
		regs[2] = nibble2(rs)
		regs[3] = nibble3(rs)
		regs[4] = nibble2(opcodeUnit)

		insn.setRegsCount(regsCount1)
		insn.setIndex(index)
	}

	/** 读取 3RC/4RCC 形式的连续寄存器范围 [startReg, startReg+regsCount) 与索引 */
	protected fun readRegsRange(insn: DexInsnData, opcodeUnit: Int, reader: SectionReader) {
		val regsCount = byte1(opcodeUnit)
		val index = reader.readUShort()
		val startReg = reader.readUShort()

		var regs = insn.getArgsReg()
		if (regs.size < regsCount) {
			// invoke-*/range 最多 255 个寄存器，超出默认数组容量时扩容
			regs = IntArray(regsCount)
			insn.setArgsReg(regs)
		}
		var regNum = startReg
		for (i in 0 until regsCount) {
			regs[i] = regNum
			regNum++
		}
		insn.setRegsCount(regsCount)
		insn.setIndex(index)
	}
}

/** 取 value 的第 0 字节（低 8 位）*/
private fun byte0(value: Int): Int = value and 0xFF

/** 取 value 的第 1 字节 */
private fun byte1(value: Int): Int = (value ushr 8) and 0xFF

/** 取 value 的第 1 字节并按有符号扩展为 int（对应 Java `(byte)(value >> 8)`）*/
private fun signedByte1(value: Int): Int = ((value ushr 8) and 0xFF).toByte().toInt()

/** 取 value 的第 0 个半字节（低 4 位）*/
private fun nibble0(value: Int): Int = value and 0xF

/** 取 value 的第 1 个半字节 */
private fun nibble1(value: Int): Int = (value ushr 4) and 0xF

/** 取 value 的第 2 个半字节 */
private fun nibble2(value: Int): Int = (value ushr 8) and 0xF

/** 取 value 的第 3 个半字节（高 4 位）*/
private fun nibble3(value: Int): Int = (value ushr 12) and 0xF

/** 取 value 的高 4 位并按有符号扩展为 int（const/4 的立即数范围 -8..7）*/
private fun signedNibble3(value: Int): Int = (((value ushr 12) and 0xF) shl 28) shr 28
