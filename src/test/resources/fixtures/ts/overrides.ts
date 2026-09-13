export type CliOption = 'maxWorkers' | 'hookTimeout' | 'teardownTimeout' | 'tagsFilter';

export const PROJECT_CLI_OVERRIDES = [
  'maxWorkers',
  'tagsFilter',
  'fileParallelism',
] as const;

export function apply(config: any) {
  const a = config.maxWorkers;
  const b = config.hookTimeout;
  const c = config["tagsFilter"];
  return a + b + c;
}
