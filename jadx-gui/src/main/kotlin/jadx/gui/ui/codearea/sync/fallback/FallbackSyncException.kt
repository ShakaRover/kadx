package jadx.gui.ui.codearea.sync.fallback

/**
 * 回退（正则 / 字符串）同步过程中抛出的受检异常。
 *
 * **为什么继承 [Exception]**：原 Java 实现继承 `Exception`，调用方显式 try/catch，
 * 这里保持相同的受检异常层次。
 */
class FallbackSyncException(msg: String) : Exception(msg)
