package jadx.plugins.input.java.data.code

import jadx.api.plugins.input.insns.Opcode
import jadx.core.utils.Utils
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.stack.StackFrame
import jadx.plugins.input.java.data.attributes.stack.StackValueType
import jadx.plugins.input.java.data.attributes.types.StackMapTableAttr
import org.jetbrains.annotations.Nullable

/**
 * 字节码解码主循环的状态机。
 *
 **做什么**：持有当前指令、操作数栈状态与异常 handler 集合；各 [IJavaInsnDecoder]
 * 通过本类的 local/pop/push/idx/lit/jump 等"动词方法"声明式地描述指令语义，
 * 由主循环统一完成寄存器分配。
 *
 **为什么需要 jumpStack**：遇到跳转时把当前栈状态快照存到目标偏移，
 * 顺序执行到达目标时直接恢复——等价于验证器的前向分析。
 */
class CodeDecodeState(
	private val clsData: JavaClassData,
	private val reader: DataReader,
	private val maxStack: Int,
	private val excHandlers: Set<Int>,
	@Nullable stackMapTable: StackMapTableAttr?,
) {

	// 保存跳转目标处的当前栈快照（到达时恢复）
	private val jumpStack: HashMap<Int, StackState> = HashMap()

	private var insn: JavaInsnData? = null
	private var stack: StackState = StackState(maxStack)
	private var excHandler = false
	private val stackMapTable: StackMapTableAttr = Utils.getOrElse(stackMapTable, StackMapTableAttr.EMPTY)

	fun onInsn(offset: Int) {
		val newStack = loadStack(offset)
		if (newStack != null) {
			stack = newStack
		}
		if (excHandlers.contains(offset)) {
			clear()
			stack.push(StackValueType.NARROW) // push exception
			excHandler = true
		} else {
			excHandler = false
		}
	}

	@Nullable
	private fun loadStack(offset: Int): StackState? {
		val stackState = jumpStack[offset]
		if (stackState != null) {
			return stackState.copy()
		}
		val frame: StackFrame? = stackMapTable.getFor(offset)
		if (frame != null) {
			return StackState(maxStack).fillFromFrame(frame)
		}
		return null
	}

	fun registerJump(jumpOffset: Int) {
		if (!jumpStack.containsKey(jumpOffset)) {
			jumpStack[jumpOffset] = stack.copy()
		}
	}

	fun decoded() {
		val insn = checkNotNull(this.insn)
		if (excHandler && insn.getOpcode() == Opcode.MOVE) {
			// replace first 'move' in exception handler with 'move-exception'
			insn.setOpcode(Opcode.MOVE_EXCEPTION)
			insn.setRegsCount(1)
		}
	}

	fun insn(): JavaInsnData = checkNotNull(this.insn)

	fun setInsn(insn: JavaInsnData) {
		this.insn = insn
	}

	fun reader(): DataReader = reader

	fun clsData(): JavaClassData = clsData

	fun local(arg: Int, local: Int): CodeDecodeState {
		insn().setArgReg(arg, localToReg(local))
		return this
	}

	fun pop(arg: Int): CodeDecodeState {
		insn().setArgReg(arg, stack.pop())
		return this
	}

	fun peek(arg: Int): CodeDecodeState {
		insn().setArgReg(arg, stack.peek())
		return this
	}

	fun peekType(at: Int): StackValueType = stack.peekTypeAt(at)

	fun peekFrom(pos: Int, arg: Int): CodeDecodeState {
		insn().setArgReg(arg, stack.peekAt(pos))
		return this
	}

	fun push(arg: Int): CodeDecodeState {
		insn().setArgReg(arg, stack.push(StackValueType.NARROW))
		return this
	}

	fun push(arg: Int, type: StackValueType): CodeDecodeState {
		insn().setArgReg(arg, stack.push(type))
		return this
	}

	fun pushWide(arg: Int): CodeDecodeState {
		insn().setArgReg(arg, stack.push(StackValueType.WIDE))
		return this
	}

	fun insert(pos: Int, type: StackValueType): Int = stack.insert(pos, type)

	fun discard() {
		stack.pop()
	}

	fun discardWord() {
		val type = stack.peekTypeAt(0)
		stack.pop()
		if (type == StackValueType.NARROW) {
			stack.pop()
		}
	}

	fun clear(): CodeDecodeState {
		stack.clear()
		return this
	}

	fun push(type: String): Int = stack.push(getSVType(type))

	/** Must be after all pop and push */
	fun jump(offset: Int) {
		val insn = checkNotNull(this.insn)
		val jumpOffset = insn.getOffset() + offset
		insn.setTarget(jumpOffset)
		registerJump(jumpOffset)
	}

	fun idx(idx: Int): CodeDecodeState {
		insn().setIndex(idx)
		return this
	}

	fun lit(lit: Long): CodeDecodeState {
		insn().setLiteral(lit)
		return this
	}

	private fun localToReg(local: Int): Int = maxStack + local

	fun fieldType(): StackValueType {
		val insn = checkNotNull(this.insn)
		val type = insn.constPoolReader().getFieldType(insn.getIndex())
		return getSVType(type)
	}

	fun getSVType(type: String): StackValueType {
		if (type == "J" || type == "D") {
			return StackValueType.WIDE
		}
		return StackValueType.NARROW
	}

	fun u1(): Int = reader.readU1()

	fun u2(): Int = reader.readU2()

	fun s1(): Int = reader.readS1()

	fun s2(): Int = reader.readS2()
}
