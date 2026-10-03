package jadx.gui.device.debugger

import io.github.skylot.jdwp.JDWP.Event.Composite.BreakpointEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ClassPrepareEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ClassUnloadEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ExceptionEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.FieldAccessEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.FieldModificationEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MethodEntryEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MethodExitEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MethodExitWithReturnValueEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorContendedEnterEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorContendedEnteredEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorWaitEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorWaitedEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.SingleStepEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ThreadDeathEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ThreadStartEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.VMDeathEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.VMStartEvent

/**
 * JDWP 事件监听适配器。
 *
 * **做什么**：把 JDWP 的各类事件回调集中到一个基类里，默认全部空实现；
 * 调用方只覆写自己关心的事件（例如断点 [onBreakpoint]、单步 [onSingleStep]）。
 *
 * **为什么全部是 `open fun`**：[SmaliDebugger] 会用匿名对象覆写其中部分方法，
 * 原 Java 是包级可见的普通方法，这里保持可覆写。
 */
abstract class EventListenerAdapter {
	open fun onVMStart(event: VMStartEvent) {
	}

	open fun onVMDeath(event: VMDeathEvent) {
	}

	open fun onSingleStep(event: SingleStepEvent) {
	}

	open fun onBreakpoint(event: BreakpointEvent) {
	}

	open fun onMethodEntry(event: MethodEntryEvent) {
	}

	open fun onMethodExit(event: MethodExitEvent) {
	}

	open fun onMethodExitWithReturnValue(event: MethodExitWithReturnValueEvent) {
	}

	open fun onMonitorContendedEnter(event: MonitorContendedEnterEvent) {
	}

	open fun onMonitorContendedEntered(event: MonitorContendedEnteredEvent) {
	}

	open fun onMonitorWait(event: MonitorWaitEvent) {
	}

	open fun onMonitorWaited(event: MonitorWaitedEvent) {
	}

	open fun onException(event: ExceptionEvent) {
	}

	open fun onThreadStart(event: ThreadStartEvent) {
	}

	open fun onThreadDeath(event: ThreadDeathEvent) {
	}

	open fun onClassPrepare(event: ClassPrepareEvent) {
	}

	open fun onClassUnload(event: ClassUnloadEvent) {
	}

	open fun onFieldAccess(event: FieldAccessEvent) {
	}

	open fun onFieldModification(event: FieldModificationEvent) {
	}
}
