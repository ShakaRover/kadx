// SPDX-License-Identifier: Apache-2.0
package kadx.analysis.callgraph

import kadx.core.dex.attributes.AttrNode

/**
 * 调用图专用的属性节点类
 *
 * 继承自 AttrNode，用于存储调用图相关的元数据。
 * 目前是一个空实现，预留扩展点供未来添加调用图特有的属性使用。
 */
class CallGraphAttrNode : AttrNode()
