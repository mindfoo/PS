export enum TaskType {
  HTTP   = 'HTTP',
  SCRIPT = 'SCRIPT',
  CUSTOM = 'CUSTOM',
}

export function configSummary(type: string, config: Record<string, unknown>): string {
  const cfg = config as Record<string, string>
  switch (type as TaskType) {
    case TaskType.HTTP:
      return `${cfg.method ?? 'GET'} ${cfg.url ?? ''}`
    case TaskType.SCRIPT: {
      const dir  = cfg.directory ? `(in ${cfg.directory}) ` : ''
      const args = cfg.args ? ` ${cfg.args}` : ''
      return `${dir}${cfg.command ?? ''} ${cfg.fileName ?? ''}${args}`.trim()
    }
    default:
      return JSON.stringify(config)
  }
}
