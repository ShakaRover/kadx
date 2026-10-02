package jadx.core.utils.android

import java.io.DataInput
import java.io.IOException

/**
 * [DataInput] 的委托基类：把全部读取方法转发给内部 [mDelegate]。
 *
 * **来源**：改编自 Android apktool（Ryszard Wiśniewski）的实现。
 *
 * **用途**：需要“包装/增强”某个 [DataInput]（例如 [ExtDataInput] 增加跳过与校验方法）时，
 * 只需继承本类并覆写少量方法，其余方法自动委托。
 *
 * **Kotlin 转换说明**：
 * - `mDelegate` 用 `@JvmField protected` 暴露，保留原 Java 的 protected 字段访问语义；
 * - 每个方法都保留原 Java 的 `throws IOException`，通过 `@Throws` 写入 JVM 异常表，
 *   使 Java 调用方仍能看到受检异常声明；
 * - [readLine] 在 [DataInput] 约定下可能返回 null，故返回类型标为可空。
 */
abstract class DataInputDelegate(
	@JvmField protected val mDelegate: DataInput,
) : DataInput {

	@Throws(IOException::class)
	override fun skipBytes(n: Int): Int = mDelegate.skipBytes(n)

	@Throws(IOException::class)
	override fun readUnsignedShort(): Int = mDelegate.readUnsignedShort()

	@Throws(IOException::class)
	override fun readUnsignedByte(): Int = mDelegate.readUnsignedByte()

	@Throws(IOException::class)
	override fun readUTF(): String = mDelegate.readUTF()

	@Throws(IOException::class)
	override fun readShort(): Short = mDelegate.readShort()

	@Throws(IOException::class)
	override fun readLong(): Long = mDelegate.readLong()

	@Throws(IOException::class)
	override fun readLine(): String? = mDelegate.readLine()

	@Throws(IOException::class)
	override fun readInt(): Int = mDelegate.readInt()

	@Throws(IOException::class)
	override fun readFully(b: ByteArray, off: Int, len: Int) {
		mDelegate.readFully(b, off, len)
	}

	@Throws(IOException::class)
	override fun readFully(b: ByteArray) {
		mDelegate.readFully(b)
	}

	@Throws(IOException::class)
	override fun readFloat(): Float = mDelegate.readFloat()

	@Throws(IOException::class)
	override fun readDouble(): Double = mDelegate.readDouble()

	@Throws(IOException::class)
	override fun readChar(): Char = mDelegate.readChar()

	@Throws(IOException::class)
	override fun readByte(): Byte = mDelegate.readByte()

	@Throws(IOException::class)
	override fun readBoolean(): Boolean = mDelegate.readBoolean()
}
