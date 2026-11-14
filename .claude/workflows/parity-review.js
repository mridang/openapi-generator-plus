export const meta = {
  name: 'parity-review',
  description: 'Hunt the known parity bug classes (and new ones) across the twelve SDKs, verify each finding, return ledger-ready findings',
  whenToUse: 'Started by /parity-review; results go into the ledger, never into another file',
  phases: [
    { title: 'Find', detail: 'one Sonnet finder per bug class' },
    { title: 'Verify', detail: 'each finding checked against the code by Opus' },
  ],
}

/* args: { pr: <absolute path of the PR worktree>, scope: 'all' | '<lang>' | '<area>', lenses: ['L01-value-serializer', ...] } */
const FINDINGS = {
  type: 'object',
  properties: { findings: { type: 'array', items: { type: 'object', properties: {
    title: { type: 'string' }, severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] },
    languages: { type: 'array', items: { type: 'string' } }, description: { type: 'string' },
    evidence: { type: 'array', items: { type: 'object', properties: { file: { type: 'string' }, line: { type: 'number' }, quote: { type: 'string' } }, required: ['file'] } },
  }, required: ['title', 'severity', 'languages', 'description', 'evidence'] } } },
  required: ['findings'],
}
const VERDICT = {
  type: 'object',
  properties: { real: { type: 'boolean' }, already_in_ledger: { type: 'string' }, reason: { type: 'string' }, severity: { type: 'string' } },
  required: ['real', 'already_in_ledger', 'reason'],
}

const found = await pipeline(
  args.lenses,
  lens => agent(
    `Read-only parity review of the generator in ${args.pr} (twelve SDKs: templates under src/main/resources/templates/<lang>/, goldens under src/spec/resources/generated/<lang>/). Scope: ${args.scope}. Bug class: ${lens}. ` +
    `Run python3 etc/parity-findings.py classes and read the examples for ${lens}: those are defects already found. Hunt for MORE instances of that class, in every language the scope covers, and for new defects of the same kind: any place where the twelve SDKs differ in public API, wire bytes, accepted/rejected input, errors or tests beyond genuine language idiom. ` +
    `Check each candidate against today's code (cite file:line and quote it). Skip anything already listed in the ledger (python3 etc/parity-findings.py next 500). Return at most 12 findings, each specific and actionable.`,
    { label: `find:${lens}`, phase: 'Find', model: 'sonnet', schema: FINDINGS }),
  (res, lens) => parallel((res?.findings ?? []).map(f => () =>
    agent(
      `Verify this parity finding against the code in ${args.pr}; default to real=false if the evidence does not hold. Check it is not already in the ledger (python3 etc/parity-findings.py next 500, and grep PARITY_FINDINGS.json for its key terms): if it is, give that id in already_in_ledger.\n\n${JSON.stringify(f)}`,
      { label: `verify:${lens}`, phase: 'Verify', model: 'opus', schema: VERDICT })
      .then(v => ({ ...f, lens: lens, category: 'review', verdict: v })))),
)
const all = found.flat().filter(Boolean)
const real = all.filter(f => f.verdict && f.verdict.real && !f.verdict.already_in_ledger)
return {
  counts: { found: all.length, real: real.length, duplicate: all.filter(f => f.verdict && f.verdict.already_in_ledger).length, rejected: all.filter(f => f.verdict && !f.verdict.real).length },
  findings: real.map(f => ({ title: f.title, severity: f.verdict.severity || f.severity, lens: f.lens, category: f.category, languages: f.languages, description: f.description + '\n\nVerified: ' + f.verdict.reason, evidence: f.evidence })),
}
