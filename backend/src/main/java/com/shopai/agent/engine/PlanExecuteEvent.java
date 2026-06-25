package com.shopai.agent.engine;

/**
 * Sealed event hierarchy emitted by PlanExecuteEngine.
 * Each event maps to a corresponding SSE event in ChatController.
 */
public sealed interface PlanExecuteEvent
    permits PlanExecuteEvent.PlanStart, PlanExecuteEvent.PlanReady,
            PlanExecuteEvent.StepStart, PlanExecuteEvent.StepDone,
            PlanExecuteEvent.SynthesisStart, PlanExecuteEvent.SynthesisToken,
            PlanExecuteEvent.SynthesisDone, PlanExecuteEvent.PlanError {

    /** Planning phase has begun. */
    record PlanStart() implements PlanExecuteEvent {}

    /** The LLM has generated a plan — sent to frontend for display. */
    record PlanReady(ExecutionPlan plan) implements PlanExecuteEvent {}

    /** A single step is about to be executed. */
    record StepStart(int stepIndex, PlanStep step) implements PlanExecuteEvent {}

    /** A single step has completed (success or failure). */
    record StepDone(int stepIndex, StepResult result) implements PlanExecuteEvent {}

    /** Synthesis phase has begun — tokens follow. */
    record SynthesisStart() implements PlanExecuteEvent {}

    /** A single token emitted during synthesis streaming. */
    record SynthesisToken(String token) implements PlanExecuteEvent {}

    /** Synthesis streaming complete. */
    record SynthesisDone(String content, int inputTokens, int outputTokens) implements PlanExecuteEvent {}

    /** An error occurred during planning or execution. */
    record PlanError(String phase, String message) implements PlanExecuteEvent {}
}
