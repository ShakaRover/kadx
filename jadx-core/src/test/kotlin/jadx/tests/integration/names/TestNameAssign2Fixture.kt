package jadx.tests.integration.names

object TestNameAssign2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.names;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;

import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.visitors.ssa.LiveVarAnalysis;

public class TestNameAssign2Fixture {

	public static class TestCls {

		public static void test(MethodNode mth, int regNum, LiveVarAnalysis la) {
			List<BlockNode> blocks = mth.getBasicBlocks();
			int blocksCount = blocks.size();
			BitSet hasPhi = new BitSet(blocksCount);
			BitSet processed = new BitSet(blocksCount);
			Deque<BlockNode> workList = new ArrayDeque<>();

			BitSet assignBlocks = la.getAssignBlocks(regNum);
			for (int id = assignBlocks.nextSetBit(0); id >= 0; id = assignBlocks.nextSetBit(id + 1)) {
				processed.set(id);
				workList.add(blocks.get(id));
			}
			while (!workList.isEmpty()) {
				BlockNode block = workList.pop();
				BitSet domFrontier = block.getDomFrontier();
				for (int id = domFrontier.nextSetBit(0); id >= 0; id = domFrontier.nextSetBit(id + 1)) {
					if (!hasPhi.get(id) && la.isLive(id, regNum)) {
						BlockNode df = blocks.get(id);
						addPhi(df, regNum);
						hasPhi.set(id);
						if (!processed.get(id)) {
							processed.set(id);
							workList.add(df);
						}
					}
				}
			}
		}

		private static void addPhi(BlockNode df, int regNum) {
		}
	}
}
"""
}
