package ru.admincontrol.cw;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwStoryGraphTest {
	private static CwStoryGraph.Node node(String id, CwStoryGraph.Kind kind) {
		CwStoryGraph.Node n = new CwStoryGraph.Node(); n.id=id; n.kind=kind; return n;
	}
	private static CwStoryGraph.Edge edge(String id, String from, String to) {
		CwStoryGraph.Edge e = new CwStoryGraph.Edge(); e.id=id; e.from=from; e.to=to; return e;
	}
	@Test void acceptsReachableBranchingGraph() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph(); g.graphId="g";
		g.nodes.put("start",node("start",CwStoryGraph.Kind.START));
		g.nodes.put("choice",node("choice",CwStoryGraph.Kind.CHOICE));
		g.nodes.put("a",node("a",CwStoryGraph.Kind.END)); g.nodes.put("b",node("b",CwStoryGraph.Kind.END));
		g.edges.add(edge("e1","start","choice")); g.edges.add(edge("e2","choice","a")); g.edges.add(edge("e3","choice","b"));
		assertTrue(CwStoryGraph.validate(g).isEmpty());
	}
	@Test void rejectsMissingEndAndUnreachableNode() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph(); g.graphId="g";
		g.nodes.put("start",node("start",CwStoryGraph.Kind.START));
		g.nodes.put("orphan",node("orphan",CwStoryGraph.Kind.DIALOGUE));
		var errors=CwStoryGraph.validate(g);
		assertTrue(errors.stream().anyMatch(x->x.contains("Недостижимый")));
		assertTrue(errors.stream().anyMatch(x->x.contains("END")));
	}
	@Test void rejectsDuplicateEdgeIds() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph(); g.graphId="g";
		g.nodes.put("start",node("start",CwStoryGraph.Kind.START)); g.nodes.put("end",node("end",CwStoryGraph.Kind.END));
		g.edges.add(edge("same","start","end")); g.edges.add(edge("same","start","end"));
		assertEquals(1,CwStoryGraph.validate(g).stream().filter(x->x.contains("Дублирующийся")).count());
	}
	@Test void rejectsReachableCycleWithoutExit() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph(); g.graphId="g";
		g.nodes.put("start",node("start",CwStoryGraph.Kind.START)); g.nodes.put("end",node("end",CwStoryGraph.Kind.END));
		g.nodes.put("loopA",node("loopA",CwStoryGraph.Kind.CHOICE)); g.nodes.put("loopB",node("loopB",CwStoryGraph.Kind.CONDITION));
		g.edges.add(edge("to-end","start","end")); g.edges.add(edge("to-loop","start","loopA"));
		g.edges.add(edge("ab","loopA","loopB")); g.edges.add(edge("ba","loopB","loopA"));
		assertTrue(CwStoryGraph.validate(g).stream().anyMatch(x->x.contains("без выхода")));
	}
	@Test void rejectsUnsafeActionCommand() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph(); g.graphId="g";
		g.nodes.put("start",node("start",CwStoryGraph.Kind.START));
		CwStoryGraph.Node action=node("action",CwStoryGraph.Kind.ACTION); action.data.put("command","op attacker"); g.nodes.put("action",action);
		g.nodes.put("end",node("end",CwStoryGraph.Kind.END)); g.edges.add(edge("a","start","action")); g.edges.add(edge("b","action","end"));
		assertTrue(CwStoryGraph.validate(g).stream().anyMatch(x->x.contains("allowlist")));
	}
	@Test void acceptsNestedAndOrCondition() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph(); g.graphId="g";
		g.nodes.put("start",node("start",CwStoryGraph.Kind.START));
		CwStoryGraph.Node condition=node("condition",CwStoryGraph.Kind.CONDITION);
		condition.data.put("condition","{\"op\":\"AND\",\"children\":[{\"op\":\"ITEM\",\"key\":\"a\"},{\"op\":\"OR\",\"children\":[{\"op\":\"ITEM\",\"key\":\"b\"},{\"op\":\"NOT\",\"child\":{\"op\":\"FLAG\",\"key\":\"blocked\"}}]}]}");
		g.nodes.put("condition",condition);g.nodes.put("end",node("end",CwStoryGraph.Kind.END));g.edges.add(edge("a","start","condition"));g.edges.add(edge("b","condition","end"));
		assertTrue(CwStoryGraph.validate(g).isEmpty());
	}
	@Test void rejectsMismatchedTypedPorts() {
		CwStoryGraph.Graph g = new CwStoryGraph.Graph();g.graphId="g";g.nodes.put("start",node("start",CwStoryGraph.Kind.START));g.nodes.put("end",node("end",CwStoryGraph.Kind.END));
		CwStoryGraph.Edge e=edge("e","start","end");e.outputType="dialogue";e.inputType="flow";g.edges.add(e);
		assertTrue(CwStoryGraph.validate(g).stream().anyMatch(x->x.contains("Несовместимые порты")));
	}
}
