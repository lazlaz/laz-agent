import type { StepRecord } from '../types';

interface Props {
  steps: StepRecord[];
  isStreaming: boolean;
}

export default function AgentSteps({ steps, isStreaming }: Props) {
  if (steps.length === 0) return null;

  return (
    <div className="mt-2 p-3 bg-gray-50 rounded-lg border border-gray-200 text-sm">
      <p className="font-medium text-gray-600 mb-2">Agent 执行过程</p>
      <div className="space-y-1.5">
        {steps.map((step, i) => (
          <div key={i} className="flex items-start gap-2 text-gray-600">
            <span className="text-xs bg-gray-300 text-gray-700 px-1.5 py-0.5 rounded font-mono">
              #{step.iteration}
            </span>
            <span className="text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">
              {step.type}
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
        ))}
        {isStreaming && steps[steps.length - 1]?.type !== 'FINAL' && (
          <div className="flex items-center gap-2 text-gray-400 text-xs">
            <span className="animate-spin">loading...</span>
            执行中...
          </div>
        )}
      </div>
    </div>
  );
}
