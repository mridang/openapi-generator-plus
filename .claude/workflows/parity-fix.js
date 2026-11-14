export const meta = {
  name: 'parity-fix',
  description: 'Fix ledger items as harmonised change sets: plan, pre-flight, shared, one agent per language, parity-guard approval, green granular commit, ledger update',
  whenToUse: 'Started by /parity-fix with the change sets to work; never edit SDK code outside it',
  phases: [
    { title: 'Plan', detail: 'parity-planner decides the canonical behaviour (opus)' },
    { title: 'Pre-flight', detail: 'parity-preflight compares the area across the twelve SDKs' },
    { title: 'Shared', detail: 'sdk-shared changes shared code first when needed' },
    { title: 'Implement', detail: 'one sdk-<lang> agent per language, in parallel' },
    { title: 'Guard', detail: 'parity-guard reviews all twelve together (opus)' },
    { title: 'Integrate', detail: 'parity-integrator commits, runs the local gate, records the ledger' },
  ],
}

/* args: { pr: <absolute path of the PR worktree>, base: <PR branch head sha>,
 *         sets: [{ slug, ids: ['F016', ...] }, ...], retries?: 2 } */
const PR = args.pr
const RETRIES = args.retries ?? 2
const LANGS = ['java', 'kotlin', 'csharp', 'php', 'python', 'ruby', 'node', 'dart', 'go', 'rust', 'swift', 'elixir']

const PLAN = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['work', 'false-positive', 'duplicate', 'already-fixed'] },
    reason: { type: 'string' },
    area: { type: 'string' },
    canonical: { type: 'string' },
    commit_subject: { type: 'string' },
    shared_task: { type: 'string' },
    allow_rules: { type: 'string', description: 'JSON list of rules to append to etc/api-surface-allow.json for differences that are genuine language idiom (each with element, aspect, languages and the language feature as reason), or empty' },
    tasks: { type: 'array', items: { type: 'object', properties: {
      language: { type: 'string', enum: LANGS }, action: { type: 'string', enum: ['change', 'skip'] },
      task: { type: 'string' } }, required: ['language', 'action', 'task'] } },
  },
  required: ['verdict', 'reason', 'area', 'canonical', 'commit_subject', 'shared_task', 'tasks'],
}
const PREFLIGHT = {
  type: 'object',
  properties: { languages: { type: 'array', items: { type: 'object', properties: {
    language: { type: 'string', enum: LANGS }, state: { type: 'string', enum: ['already-compliant', 'needs-test', 'needs-fix'] },
    notes: { type: 'string' } }, required: ['language', 'state', 'notes'] } } },
  required: ['languages'],
}
const REPORT = {
  type: 'object',
  properties: {
    language: { type: 'string' }, branch: { type: 'string' }, commit: { type: 'string' },
    validation: { type: 'object', properties: { ran: { type: 'boolean' }, passed: { type: 'boolean' }, notes: { type: 'string' } }, required: ['ran', 'passed', 'notes'] },
    results: { type: 'array', items: { type: 'object', properties: {
      id: { type: 'string' }, outcome: { type: 'string', enum: ['fixed', 'already-done', 'false-positive', 'forced', 'needs-decision', 'needs-spec', 'needs-shared', 'blocked'] },
      note: { type: 'string' }, test: { type: 'string' } }, required: ['id', 'outcome', 'note'] } },
    discovered: { type: 'array', items: { type: 'string' } },
  },
  required: ['language', 'branch', 'commit', 'validation', 'results'],
}
const VERDICT = {
  type: 'object',
  properties: {
    approved: { type: 'boolean' },
    summary: { type: 'string' },
    failures: { type: 'array', items: { type: 'object', properties: {
      language: { type: 'string' }, problem: { type: 'string' }, fix: { type: 'string' } }, required: ['language', 'problem', 'fix'] } },
    new_findings: { type: 'array', items: { type: 'object', properties: {
      title: { type: 'string' }, severity: { type: 'string' }, languages: { type: 'array', items: { type: 'string' } },
      description: { type: 'string' } }, required: ['title', 'severity', 'languages', 'description'] } },
  },
  required: ['approved', 'summary', 'failures'],
}
const STEP = {
  type: 'object',
  properties: { ok: { type: 'boolean' }, commit: { type: 'string', description: 'git rev-parse HEAD of the PR branch when you finish' }, notes: { type: 'string' } },
  required: ['ok', 'notes'],
}

function implement(set, plan, pre, lang, base, feedback) {
  const task = plan.tasks.find(t => t.language === lang)
  const state = pre.find(p => p.language === lang)
  return agent(
    `Change set ${set.slug} (ledger ${set.ids.join(',')}). Base commit ${base}; branch parity/${set.slug}-${lang}` +
    (feedback ? ' (it already exists: check it out and continue on it; do not reset it).' : '.') +
    `\n\nCANONICAL BEHAVIOUR (all twelve SDKs):\n${plan.canonical}\n\nYOUR TASK (${lang}):\n${task ? task.task : 'Bring ' + lang + ' to the canonical behaviour.'}` +
    `\n\nPRE-FLIGHT for ${lang}: ${state ? state.state + ': ' + state.notes : 'none'}` +
    (feedback ? `\n\nTHE PARITY GUARD REJECTED YOUR PREVIOUS ATTEMPT. Fix exactly this:\n${feedback}` : ''),
    { label: `${set.slug}:${lang}`, phase: 'Implement', agentType: `sdk-${lang}`, isolation: 'worktree', schema: REPORT })
}

const allowNote = plan => (plan.allow_rules && plan.allow_rules.trim() && plan.allow_rules.trim() !== '[]')
  ? ` Also append these idiom rules to the "allow" list of etc/api-surface-allow.json (keep its formatting), check python3 etc/api-surface.py check no longer reports them, and include the file in the commit: ${plan.allow_rules}`
  : ''
const summary = []
let head = args.base
const moved = step => { if (step && /^[0-9a-f]{7,40}$/.test(step.commit || '')) head = step.commit; return step }
for (const set of args.sets) {
  phase('Plan')
  const plan = await agent(`Plan change set ${set.slug} for ledger items ${set.ids.join(',')}.`, { label: `${set.slug}:plan`, phase: 'Plan', agentType: 'parity-planner', schema: PLAN })
  if (!plan) { summary.push(`${set.slug}: planner failed`); continue }
  if (plan.verdict !== 'work') {
    moved(await agent(`In ${PR}, record ledger items ${set.ids.join(',')} as ${plan.verdict === 'already-fixed' ? 'fixed' : plan.verdict} with note "${plan.reason.replace(/"/g, "'").slice(0, 300)}" (python3 etc/parity-findings.py set <ids> <status> "<note>", then render), and commit the ledger as "docs: record ${set.slug} in the findings ledger".${allowNote(plan)}`,
      { label: `${set.slug}:record`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP }))
    summary.push(`${set.slug}: ${plan.verdict} (${plan.reason.slice(0, 120)})`)
    continue
  }

  phase('Pre-flight')
  const pre = (await agent(`Pre-flight for change set ${set.slug}, area "${plan.area}". Canonical behaviour:\n${plan.canonical}\n\nPlanned tasks:\n${JSON.stringify(plan.tasks)}`,
    { label: `${set.slug}:preflight`, phase: 'Pre-flight', agentType: 'parity-preflight', schema: PREFLIGHT }))?.languages ?? []
  const work = LANGS.filter(l => {
    const t = plan.tasks.find(x => x.language === l)
    const p = pre.find(x => x.language === l)
    return !(t && t.action === 'skip' && (!p || p.state === 'already-compliant'))
  })

  let base = head
  if (plan.shared_task && plan.shared_task.trim() && plan.shared_task.trim().toLowerCase() !== 'none') {
    phase('Shared')
    const shared = await agent(`Change set ${set.slug} (ledger ${set.ids.join(',')}). Base commit ${base}; branch parity/${set.slug}-shared.\n\nCANONICAL BEHAVIOUR:\n${plan.canonical}\n\nSHARED TASK:\n${plan.shared_task}`,
      { label: `${set.slug}:shared`, phase: 'Shared', agentType: 'sdk-shared', isolation: 'worktree', schema: REPORT })
    if (!shared || !shared.validation.passed || !/^[0-9a-f]{7,40}$/.test(shared.commit)) {
      summary.push(`${set.slug}: parked, shared step failed (${shared ? shared.validation.notes.slice(0, 120) : 'no report'})`)
      moved(await agent(`In ${PR}, mark ledger items ${set.ids.join(',')} parked with note "shared step failed in ${set.slug}" (python3 etc/parity-findings.py set <ids> parked "<note>", render) and commit the ledger as "docs: record ${set.slug} in the findings ledger".`,
        { label: `${set.slug}:record`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP }))
      continue
    }
    base = shared.commit
  }

  let reports = (await parallel(work.map(l => () => implement(set, plan, pre, l, base, null)))).filter(Boolean)
  let verdict = null
  for (let attempt = 0; attempt <= RETRIES; attempt++) {
    const branches = reports.filter(r => r.validation.passed && /^[0-9a-f]{7,40}$/.test(r.commit)).map(r => r.branch)
    const broken = work.filter(l => !reports.find(r => r.language === l && r.validation.passed))
    phase('Integrate')
    const staged = await agent(`In ${PR} (branch feat/better-generated-clients, if a previous attempt left a merge in progress run git merge --abort first), stage change set ${set.slug}: merge ${base !== head ? 'parity/' + set.slug + '-shared ' : ''}${branches.join(' ')} with --no-commit.`,
      { label: `${set.slug}:stage${attempt}`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP })
    phase('Guard')
    verdict = await agent(`Review change set ${set.slug} (ledger ${set.ids.join(',')}), staged in ${PR}.\n\nCANONICAL BEHAVIOUR:\n${plan.canonical}\n\nPLAN:\n${JSON.stringify(plan.tasks)}\n\nLANGUAGES THAT DID NOT DELIVER A GREEN BRANCH: ${broken.join(', ') || 'none'}\n\nAGENT REPORTS:\n${JSON.stringify(reports.map(r => ({ language: r.language, validation: r.validation, results: r.results })))}\n\nSTAGING: ${staged ? staged.notes : 'failed'}`,
      { label: `${set.slug}:guard${attempt}`, phase: 'Guard', agentType: 'parity-guard', schema: VERDICT })
    if (verdict && verdict.approved) break
    if (attempt === RETRIES || !verdict) break
    const failing = [...new Set([...broken, ...verdict.failures.map(f => f.language).filter(l => LANGS.includes(l))])]
    log(`${set.slug}: guard rejected attempt ${attempt + 1}; retrying ${failing.join(', ')}`)
    const again = (await parallel(failing.map(l => () => implement(set, plan, pre, l, base,
      verdict.failures.filter(f => f.language === l || !LANGS.includes(f.language)).map(f => `- ${f.problem} -> ${f.fix}`).join('\n') || 'Deliver a green branch.')))).filter(Boolean)
    reports = [...reports.filter(r => !failing.includes(r.language)), ...again]
  }

  phase('Integrate')
  const found = [...(verdict?.new_findings ?? []), ...reports.flatMap(r => (r.discovered ?? []).map(d => ({ title: d.slice(0, 200), severity: 'medium', languages: [r.language], description: d })))]
  if (verdict && verdict.approved) {
    const done = await agent(`In ${PR}, the parity-guard approved the staged change set ${set.slug}. Commit it with subject "${plan.commit_subject}" and a one-line body naming the ledger items ${set.ids.join(',')}, run the quick local gate, and on success mark ${set.ids.join(',')} done in the ledger with the commit sha and add these new findings (write them as a JSON list to a temp file first): ${JSON.stringify(found)}.${allowNote(plan)}`,
      { label: `${set.slug}:commit`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP })
    summary.push(`${set.slug}: ${done && done.ok ? 'landed ' + (done.commit || '') : 'gate failed: ' + (done ? done.notes.slice(0, 160) : 'no report')}`)
    moved(done)
  } else {
    moved(await agent(`In ${PR}, run git merge --abort if a merge is in progress. Mark ledger items ${set.ids.join(',')} parked with note "parity-guard rejected ${set.slug}: ${(verdict ? verdict.summary : 'no verdict').replace(/"/g, "'").slice(0, 300)}", add these new findings (JSON list in a temp file): ${JSON.stringify(found)}, render, and commit the ledger as "docs: record ${set.slug} in the findings ledger".`,
      { label: `${set.slug}:park`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP }))
    summary.push(`${set.slug}: parked (${verdict ? verdict.summary.slice(0, 160) : 'no verdict'})`)
  }
  log(summary[summary.length - 1])
}
return { summary, head }
