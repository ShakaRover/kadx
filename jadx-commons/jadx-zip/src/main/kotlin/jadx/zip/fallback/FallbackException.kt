package jadx.zip.fallback

import java.io.IOException

/**
 * 回退（fallback）解析器抛出异常时使用的 IOException。
 *
 * 继承 IOException（而非 RuntimeException），Java 调用方的 throws 声明和 catch 行为与原代码保持一致；
 * 构造方法签名 (message, cause) 与原 Java 版一致，保证互操作无差异。
 */
class FallbackException(message: String, cause: Throwable) : IOException(message, cause)
