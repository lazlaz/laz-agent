import type { StepRecord, PlanStepData } from '../types';

interface Props {
  steps: StepRecord[];
  isStreaming: boolean;
  executionMode?: 'react' | 'plan-execute';
  planSteps?: PlanStepData[];
  planPhase?: 'idle' | 'planning' | 'executing' | 'synthesizing';
  activeStepIndex?: number;
}

/** Maps a step type to a Chinese label badge. */
function typeLabel(type: string): { text: string; color: string } {
  switch (type) {
    case 'THOUGHT': return { text: '思考', color: 'bg-yellow-100 text-yellow-700' };
    case 'TOOL_CALL': return { text: '调用工具', color: 'bg-purple-100 text-purple-700' };
    case 'TOOL_RESULT': return { text: '工具结果', color: 'bg-green-100 text-green-700' };
    case 'FINAL': return { text: '完成', color: 'bg-blue-100 text-blue-700' };
    default: return { text: type, color: 'bg-gray-100 text-gray-700' };
  }
}

export default function AgentSteps({
  steps,
  isStreaming,
  executionMode = 'react',
  planSteps = [],
  planPhase = 'idle',
  activeStepIndex = -1,
}: Props) {
  const hasSteps = steps.length > 0;
  const hasPlan = planSteps.length > 0;
  if (!hasSteps && !hasPlan) return null;

  // ── Plan-Execute mode: show plan progress ─────────────────────────
  if (executionMode === 'plan-execute' && hasPlan) {
    return (
      <div className="mt-2 p-3 bg-gray-50 rounded-lg border border-gray-200 text-sm">
        <p className="font-medium text-gray-600 mb-2">
          {planPhase === 'planning' && '⏳ 正在生成执行计划...'}
          {planPhase === 'executing' && '🔧 执行计划中...'}
          {planPhase === 'synthesizing' && '📝 正在汇总回答...'}
        </p>

        {/* Plan step checklist */}
        <div className="space-y-1">
          {planSteps.map((ps, i) => {
            const isActive = i === activeStepIndex && planPhase === 'executing';
            const done = steps.some((s) => s.iteration === i && s.type === 'TOOL_RESULT');
            const failed = steps.some(
              (s) => s.iteration === i && s.result && !s.result.success
            );
            const isFuture = i > activeStepIndex || (i === activeStepIndex && planPhase === 'planning');

            let bg = 'bg-white text-gray-400';
            let icon = '○';
            if (failed) { bg = 'bg-red-50 text-red-600 border-red-200'; icon = '✗'; }
            else if (done) { bg = 'bg-green-50 text-green-700 border-green-200'; icon = '✓'; }
            else if (isActive) { bg = 'bg-blue-50 text-blue-700 border-blue-200'; icon = '⋯'; }

            return (
              <div
                key={i}
                className={`flex items-center gap-2 px-2 py-1 rounded border text-xs ${bg} ${
                  isActive ? 'ring-1 ring-blue-300' : ''
                } ${isFuture ? '' : ''}`}
              >
                <span className="font-mono w-4 text-center">{icon}</span>
                <span className="font-mono text-gray-400 text-[10px]">#{i}</span>
                <span
                  className={`px-1 py-0.5 rounded text-[10px] font-medium ${
                    typeLabel(ps.type === 'tool_call' ? 'TOOL_CALL' : 'THOUGHT').color
                  }`}
                >
                  {ps.type === 'tool_call' ? '工具' : '推理'}
                </span>
                <span className="truncate flex-1">{ps.description}</span>
                {ps.tool && (
                  <span className="text-purple-500 font-mono text-[10px]">{ps.tool}</span>
                )}
              </div>
            );
          })}
        </div>

        {/* Streaming indicator during synthesis */}
        {planPhase === 'synthesizing' && isStreaming && (
          <div className="flex items-center gap-2 text-gray-400 text-xs mt-2">
            <span className="animate-pulse">⏳</span>
            正在生成回答...
          </div>
        )}
      </div>
    );
  }

  // ── ReAct mode: original step list ─────────────────────────────────
  return (
    <div className="mt-2 p-3 bg-gray-50 rounded-lg border border-gray-200 text-sm">
      <p className="font-medium text-gray-600 mb-2">Agent 执行过程</p>
      <div className="space-y-1.5">
        {steps.map((step, i) => {
          const label = typeLabel(step.type);
          return (
            <div key={i} className="flex items-start gap-2 text-gray-600">
              <span className="text-xs bg-gray-300 text-gray-700 px-1.5 py-0.5 rounded font-mono">
                #{step.iteration}
              </span>
              <span className={`text-xs px-1.5 py-0.5 rounded ${label.color}`}>
                {label.text}
              </span>
              {step.thought && (
                <span className="text-gray-500 flex-1 truncate">{step.thought}</span>
              )}
              {step.toolName && (
                <span className="text-purple-600">
                  调用工具: {step.toolName}
                </span>
              )}
              {step.result && (
                <span className={step.result.success ? 'text-green-600' : 'text-red-600'}>
                  {step.result.success ? '成功' : '失败'}: {step.result.content.substring(0, 60)}
                </span>
              )}
            </div>
          );
        })}
        {isStreaming && steps[steps.length - 1]?.type !== 'FINAL' && (
          <div className="flex items-center gap-2 text-gray-400 text-xs">
            <span className="animate-spin">⏳</span>
            执行中...
          </div>
        )}
      </div>
    </div>
  );
}
