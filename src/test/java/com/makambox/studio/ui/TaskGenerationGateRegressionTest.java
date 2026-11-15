package com.makambox.studio.ui;

/** Ensures late results from a replaced audio source can never be accepted. */
public final class TaskGenerationGateRegressionTest {
    public static void main(String[] args) {
        TaskGenerationGate gate = new TaskGenerationGate();
        long first = gate.advance();
        if (!gate.accepts(first)) throw new AssertionError("current source was rejected");

        long second = gate.advance();
        if (gate.accepts(first)) throw new AssertionError("stale source result was accepted");
        if (!gate.accepts(second) || gate.current() != second) {
            throw new AssertionError("replacement source was not current");
        }

        long third = gate.advance();
        if (gate.accepts(second) || !gate.accepts(third)) {
            throw new AssertionError("rapid replacement ordering is not monotonic");
        }
        System.out.println("TASK_GENERATION_GATE_OK stale_rejected=true");
    }
}
