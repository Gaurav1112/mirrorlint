export interface CliOption {
  maxWorkers: number;
  hookTimeout: number;
  teardownTimeout: number;
  tagsFilter: number;
}

export const PROJECT_CLI_OVERRIDES = [
  'maxWorkers',
  'teardownTimeout',
  'tagsFilter',
] as const;
