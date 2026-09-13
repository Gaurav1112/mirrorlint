export const ALLOWED = ['alpha', 'beta', 'gamma'] as const;

export function apply(cfg: any) {
  const a = cfg.alpha, d = cfg.delta;
  const b = cfg.beta;
  const c = cfg.gamma;
  return a + b + c + d;
}
