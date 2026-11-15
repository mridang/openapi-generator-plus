export const meta = {
  name: 'parity-fix',
  description: 'Fix ledger items as harmonised change sets: plan, pre-flight, shared, one agent per language, parity-guard approval, green granular commit, ledger update',
  whenToUse: 'Started by /parity-fix with the change sets to work; never edit SDK code outside it',
  phases: [
    { title: 'Plan', detail: 'parity-planner decides the canonical behaviour (opus)' },
    { title: 'Pre-flight', detail: 'parity-preflight compares the area across the twelve SDKs' },
    { title: 'Shared', detail: 'sdk-shared changes shared code first when needed' },
    { title: 'Implement', detail: 'one sdk-<lang> agent per language, in parallel' },
    { title: 'Guard', detail: 'idiom-<lang> reviewers and the parity-guard (opus) review the staged change' },
    { title: 'Integrate', detail: 'parity-integrator commits, runs the local gate, records the ledger' },
  ],
}

/* args: { pr: <absolute path of the PR worktree>, base: <PR branch head sha>,
 *         sets: [{ slug, ids: ['F016', ...] }, ...], retries?: 2, width?: 5, inflight?: 3 } */
const PR = args.pr
const RETRIES = args.retries ?? 2
/* Language agents running at once, across all sets. Each runs its own Docker specs, so
 * this bounds load even in a session whose Docker hook is not active. */
const WIDTH = args.width ?? 6
const LANGS = ['java', 'kotlin', 'csharp', 'php', 'python', 'ruby', 'node', 'dart', 'go', 'rust', 'swift', 'elixir']

const MODEL = { 'parity-guard': 'opus' }

/* Runs an agent as its custom type (.claude/agents/<type>.md). A session that started
 * before the agent files existed has not loaded them; the agent then runs as the default
 * type on the same model and reads its definition first. */
async function role(prompt, opts) {
  const type = opts.agentType
  try {
    return await agent(prompt, opts)
  } catch (e) {
    if (!/agent type .* not found/.test(String(e))) throw e
    return agent(`Your instructions are the body of ${PR}/.claude/agents/${type}.md (below its front matter): read that file first and follow it exactly; you are the ${type} agent.\n\n${prompt}`,
      { ...opts, agentType: undefined, model: MODEL[type] || 'sonnet' })
  }
}

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
    partial_ok: { type: 'boolean', description: 'only when approved is false: the staged tree is harmonised and may land as it is, with the items in open_ids left open' },
    open_ids: { type: 'array', items: { type: 'string' }, description: 'ledger ids that are not complete in every SDK and stay open if the set lands partially' },
    failures: { type: 'array', items: { type: 'object', properties: {
      language: { type: 'string' }, problem: { type: 'string' }, fix: { type: 'string' } }, required: ['language', 'problem', 'fix'] } },
    new_findings: { type: 'array', items: { type: 'object', properties: {
      title: { type: 'string' }, severity: { type: 'string' }, languages: { type: 'array', items: { type: 'string' } },
      description: { type: 'string' } }, required: ['title', 'severity', 'languages', 'description'] } },
  },
  required: ['approved', 'summary', 'failures'],
}
const IDIOM = {
  type: 'object',
  properties: {
    idiomatic: { type: 'boolean' },
    rejections: { type: 'array', items: { type: 'object', properties: { problem: { type: 'string' }, fix: { type: 'string' } }, required: ['problem', 'fix'] } },
    notes: { type: 'string', description: 'conflicts between idiom and harmonisation, for the parity-guard' },
  },
  required: ['idiomatic', 'rejections', 'notes'],
}
const STEP = {
  type: 'object',
  properties: { ok: { type: 'boolean' }, commit: { type: 'string', description: 'git rev-parse HEAD of the PR branch when you finish' }, notes: { type: 'string' } },
  required: ['ok', 'notes'],
}

/* Change sets run concurrently (up to INFLIGHT at once) so one set's slow review never
 * leaves the machine idle, under three rules:
 *   one language, one agent: a lock per language, so two agents never work on the same
 *     language at the same time, whichever sets they belong to;
 *   bounded load: at most WIDTH language or shared agents run at once;
 *   one integration at a time: staging, review and commit happen in the single PR
 *     worktree, so they hold the PR lock. */
const INFLIGHT = args.inflight ?? 2
const locks = {}
async function withLock(key, fn) {
  const prev = locks[key] || Promise.resolve()
  let release
  const mine = new Promise(r => { release = r })
  locks[key] = prev.then(() => mine)
  await prev
  try { return await fn() } finally { release() }
}
let free = WIDTH
const waiting = []
async function withSlot(fn) {
  if (free > 0) free--
  else await new Promise(r => waiting.push(r))
  try { return await fn() } finally { const next = waiting.shift(); if (next) next(); else free++ }
}

function implement(set, plan, pre, lang, base, feedback, attempt = 0) {
  const task = plan.tasks.find(t => t.language === lang)
  const state = pre.find(p => p.language === lang)
  const body = `\n\nCANONICAL BEHAVIOUR (all twelve SDKs):\n${plan.canonical}\n\nYOUR TASK (${lang}):\n${task ? task.task : 'Bring ' + lang + ' to the canonical behaviour.'}` +
    `\n\nPRE-FLIGHT for ${lang}: ${state ? state.state + ': ' + state.notes : 'none'}`
  /* A retry continues on a new branch cut from the previous attempt's commit: the earlier
   * agent's worktree may still hold the old branch, so it cannot be checked out again. */
  const prompt = !feedback
    ? `Change set ${set.slug} (ledger ${set.ids.join(',')}). Base commit ${base}; branch parity/${set.slug}-${lang}.` + body
    : `Change set ${set.slug} (ledger ${set.ids.join(',')}). Setup: git checkout -B parity/${set.slug}-${lang}-r${attempt} ${base} (${base} is your previous attempt; keep its work and fix only what the review found).` + body +
      `\n\nTHE REVIEW REJECTED YOUR PREVIOUS ATTEMPT. Fix exactly this:\n${feedback}` +
      `\n\nIf your final validation fails only for a reason unrelated to this change (Docker or network trouble, a known flaky test, a timeout), still commit the change and say exactly which failure is unrelated and why: the reviewer decides.`
  return withLock(lang, () => withSlot(() => role(prompt,
    { label: `${set.slug}:${lang}${attempt ? ':r' + attempt : ''}`, phase: 'Implement', agentType: `sdk-${lang}`, isolation: 'worktree', schema: REPORT })))
}

const allowNote = plan => (plan.allow_rules && plan.allow_rules.trim() && plan.allow_rules.trim() !== '[]')
  ? ` Also append these idiom rules to the "allow" list of etc/api-surface-allow.json (keep its formatting), check python3 etc/api-surface.py check no longer reports them, and include the file in the commit: ${plan.allow_rules}`
  : ''
const summary = []
let head = args.base
const moved = step => { if (step && /^[0-9a-f]{7,40}$/.test(step.commit || '')) head = step.commit; return step }
const integrate = (label, prompt) => withLock('pr', () => role(prompt, { label, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP })).then(moved)
const discovered = (verdict, reports) => [...(verdict?.new_findings ?? []), ...reports.flatMap(r => (r.discovered ?? []).map(d => ({ title: d.slice(0, 200), severity: 'medium', languages: [r.language], description: d })))]

/* Large sets are planned in chunks of PLAN_CHUNK items in parallel, then merged into one
 * task per language, so each language agent works its whole list in one run. Items a
 * chunk finds not real are recorded straight away. */
const PLAN_CHUNK = 5
async function planChunk(set, ids, n) {
  return role(`Plan change set ${set.slug}${n === null ? '' : ' part ' + (n + 1)} for ledger items ${ids.join(',')}.`,
    { label: `${set.slug}:plan${n === null ? '' : n + 1}`, phase: 'Plan', agentType: 'parity-planner', schema: PLAN })
}

async function prepare(set) {
  let plan
  if (set.ids.length <= PLAN_CHUNK + 1) {
    plan = await planChunk(set, set.ids, null)
  } else {
    const chunks = []
    for (let i = 0; i < set.ids.length; i += PLAN_CHUNK) chunks.push(set.ids.slice(i, i + PLAN_CHUNK))
    const plans = await parallel(chunks.map((ids, n) => () => planChunk(set, ids, n).then(p => p && { ...p, ids })))
    const work = plans.filter(p => p && p.verdict === 'work')
    for (const p of plans.filter(p => p && p.verdict !== 'work')) {
      await integrate(`${set.slug}:record-part`, `In ${PR}, follow your Record instructions: record ledger items ${p.ids.join(',')} as ${p.verdict === 'already-fixed' ? 'fixed' : p.verdict} with note "${p.reason.replace(/"/g, "'").slice(0, 300)}" (python3 etc/parity-findings.py set <ids> <status> "<note>", then render), and commit the ledger as "docs: record ${set.slug} in the findings ledger".${allowNote(p)}`)
    }
    if (!work.length) return { plan: plans.find(Boolean) || null, pre: [], ids: [] }
    plan = {
      verdict: 'work', reason: work.map(p => p.reason).join('\n'), area: work.map(p => p.area).join('; '),
      canonical: work.map(p => `[${p.ids.join(',')}] ${p.canonical}`).join('\n\n'),
      commit_subject: work.length === 1 ? work[0].commit_subject : `fix: harmonise ${set.slug.replace(/-/g, ' ')} across the SDKs`,
      shared_task: work.map(p => p.shared_task).filter(t => t && t.trim() && t.trim().toLowerCase() !== 'none').join('\n\n') || 'none',
      allow_rules: JSON.stringify(work.flatMap(p => { try { return JSON.parse(p.allow_rules || '[]') } catch (e) { return [] } })),
      tasks: LANGS.map(l => {
        const mine = work.map(p => ({ ids: p.ids, t: p.tasks.find(t => t.language === l) })).filter(x => x.t && x.t.action === 'change')
        return { language: l, action: mine.length ? 'change' : 'skip', task: mine.map(x => `[${x.ids.join(',')}] ${x.t.task}`).join('\n\n') || 'already compliant' }
      }),
    }
    set = { ...set, ids: work.flatMap(p => p.ids) }
  }
  if (!plan || plan.verdict !== 'work') return { plan, pre: [], ids: set.ids }
  const pre = (await role(`Pre-flight for change set ${set.slug}, area "${plan.area}". Canonical behaviour:\n${plan.canonical}\n\nPlanned tasks:\n${JSON.stringify(plan.tasks)}`,
    { label: `${set.slug}:preflight`, phase: 'Pre-flight', agentType: 'parity-preflight', schema: PREFLIGHT }))?.languages ?? []
  return { plan, pre, ids: set.ids }
}

async function review(set, plan, reports, work, sharedBranch, last) {
  /* Every language that committed is staged, green or not; the reviewer reads each
   * validation report and decides whether a red run is the change or the environment. */
  const branches = reports.filter(r => /^[0-9a-f]{7,40}$/.test(r.commit || '')).map(r => r.branch)
  const broken = work.filter(l => !reports.find(r => r.language === l && r.validation.passed))
  const staged = await role(`In ${PR} follow your Stage instructions for change set ${set.slug} (integration branch parity/integrate-${set.slug}): merge ${sharedBranch ? sharedBranch + ' ' : ''}${branches.join(' ')}.`,
    { label: `${set.slug}:stage`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP })
  const landed = reports.filter(r => branches.includes(r.branch)).map(r => r.language)
  const idioms = (await parallel(landed.map(l => () => role(`Review the ${l} part of change set ${set.slug}, staged in ${PR}. Canonical behaviour (all twelve SDKs):\n${plan.canonical}`,
    { label: `${set.slug}:idiom-${l}`, phase: 'Guard', agentType: `idiom-${l}`, schema: IDIOM }).then(v => v && { language: l, ...v })))).filter(Boolean)
  const verdict = await role(`Review change set ${set.slug} (ledger ${set.ids.join(',')}), staged in ${PR}.\n\nCANONICAL BEHAVIOUR:\n${plan.canonical}\n\nPLAN:\n${JSON.stringify(plan.tasks)}\n\nLANGUAGES THAT DID NOT DELIVER A GREEN BRANCH: ${broken.join(', ') || 'none'}\n\nAGENT REPORTS:\n${JSON.stringify(reports.map(r => ({ language: r.language, validation: r.validation, results: r.results })))}\n\nIDIOM REVIEWS (one per language; rejected ones block the change set regardless of your verdict, notes may flag idiom-versus-parity conflicts for you to rule on):\n${JSON.stringify(idioms)}\n\nSTAGING: ${staged ? staged.notes : 'failed'}`,
    { label: `${set.slug}:guard`, phase: 'Guard', agentType: 'parity-guard', schema: VERDICT })
  if (!verdict) return { verdict: null, broken }
  const unidiomatic = idioms.filter(v => !v.idiomatic)
  if (unidiomatic.length) {
    verdict.approved = false
    verdict.failures = [...verdict.failures, ...unidiomatic.flatMap(v => v.rejections.map(r => ({ language: v.language, problem: 'not idiomatic: ' + r.problem, fix: r.fix })))]
    verdict.summary = `${verdict.summary} Idiom review rejected ${unidiomatic.map(v => v.language).join(', ')}.`
  }
  if (!verdict.approved && last && verdict.partial_ok && !unidiomatic.length) {
    verdict.approved = true
    verdict.partial = (verdict.open_ids || []).filter(id => set.ids.includes(id))
  }
  if (verdict.approved) {
    const doneIds = set.ids.filter(id => !(verdict.partial || []).includes(id))
    const done = moved(await role(`In ${PR}, the parity-guard approved the staged change set ${set.slug} on parity/integrate-${set.slug}${verdict.partial && verdict.partial.length ? ' (partially: ' + verdict.partial.join(',') + ' stay open)' : ''}. Follow your Commit instructions: commit it with subject "${plan.commit_subject}" and a one-line body naming the ledger items ${doneIds.join(',')}, run the quick local gate, and on success mark ${doneIds.join(',')} done in the ledger with the commit sha and add these new findings (write them as a JSON list to a temp file first): ${JSON.stringify(discovered(verdict, reports))}.${allowNote(plan)}`,
      { label: `${set.slug}:commit`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP }))
    verdict.landed = done && done.ok ? `landed ${doneIds.length}/${set.ids.length} ${done.commit || ''}` : 'gate failed: ' + (done ? done.notes.slice(0, 160) : 'no report')
  } else {
    await role(`In ${PR}, if a merge is in progress run git merge --abort, then git checkout -f feat/better-generated-clients (the review rejected the staged attempt; the language branches are kept for the retry).`,
      { label: `${set.slug}:unstage`, phase: 'Integrate', agentType: 'parity-integrator', schema: STEP })
  }
  return { verdict, broken }
}

/* A set may carry `existing` ({lang: {branch, commit}}, committed work from an earlier
 * run) and `canonicalFile`: planning and implementation are skipped and the branches go
 * straight to staging, idiom review and the guard. */
async function runSet(set) {
  const { plan, pre, ids } = set.existing
    ? { plan: { verdict: 'work', reason: 'resumed', area: set.slug, canonical: `Read ${set.canonicalFile}: it holds the canonical behaviour for this change set.`, commit_subject: set.subject, shared_task: 'none', allow_rules: '[]', tasks: [] }, pre: [], ids: set.ids }
    : await prepare(set)
  if (ids && ids.length && ids.length !== set.ids.length) set = { ...set, ids }
  if (!plan) { summary.push(`${set.slug}: planner failed`); return }
  if (plan.verdict !== 'work') {
    await integrate(`${set.slug}:record`, `In ${PR}, follow your Record instructions: record ledger items ${set.ids.join(',')} as ${plan.verdict === 'already-fixed' ? 'fixed' : plan.verdict} with note "${plan.reason.replace(/"/g, "'").slice(0, 300)}" (python3 etc/parity-findings.py set <ids> <status> "<note>", then render), and commit the ledger as "docs: record ${set.slug} in the findings ledger".${allowNote(plan)}`)
    summary.push(`${set.slug}: ${plan.verdict} (${plan.reason.slice(0, 120)})`)
    return
  }
  const work = set.existing ? Object.keys(set.existing) : LANGS.filter(l => {
    const t = plan.tasks.find(x => x.language === l)
    const p = pre.find(x => x.language === l)
    return !(t && t.action === 'skip' && (!p || p.state === 'already-compliant'))
  })

  let base = head
  let sharedBranch = null
  if (plan.shared_task && plan.shared_task.trim() && plan.shared_task.trim().toLowerCase() !== 'none') {
    const shared = await withLock('shared', () => withSlot(() => role(`Change set ${set.slug} (ledger ${set.ids.join(',')}). Base commit ${base}; branch parity/${set.slug}-shared.\n\nCANONICAL BEHAVIOUR:\n${plan.canonical}\n\nSHARED TASK:\n${plan.shared_task}`,
      { label: `${set.slug}:shared`, phase: 'Shared', agentType: 'sdk-shared', isolation: 'worktree', schema: REPORT })))
    if (!shared || !shared.validation.passed || !/^[0-9a-f]{7,40}$/.test(shared.commit)) {
      await integrate(`${set.slug}:record`, `In ${PR}, follow your Record instructions: mark ledger items ${set.ids.join(',')} parked with note "shared step failed in ${set.slug}" (python3 etc/parity-findings.py set <ids> parked "<note>", render) and commit the ledger as "docs: record ${set.slug} in the findings ledger".`)
      summary.push(`${set.slug}: parked, shared step failed (${shared ? shared.validation.notes.slice(0, 120) : 'no report'})`)
      return
    }
    base = shared.commit
    sharedBranch = `parity/${set.slug}-shared`
  }

  let reports = set.existing
    ? Object.entries(set.existing).map(([language, x]) => ({ language, branch: x.branch, commit: x.commit, validation: { ran: true, passed: true, notes: 'committed in an earlier run' }, results: [] }))
    : (await parallel(work.map(l => () => implement(set, plan, pre, l, base, null)))).filter(Boolean)
  let result = null
  for (let attempt = 0; attempt <= RETRIES; attempt++) {
    result = await withLock('pr', () => review(set, plan, reports, work, sharedBranch, attempt === RETRIES))
    const verdict = result.verdict
    if (!verdict || verdict.approved || attempt === RETRIES) break
    const failing = [...new Set([...result.broken, ...verdict.failures.map(f => f.language).filter(l => LANGS.includes(l))])]
    log(`${set.slug}: review rejected attempt ${attempt + 1}; retrying ${failing.join(', ')}`)
    const again = (await parallel(failing.map(l => () => {
      const prev = reports.find(r => r.language === l && /^[0-9a-f]{7,40}$/.test(r.commit || ''))
      return implement(set, plan, pre, l, prev ? prev.commit : base,
        verdict.failures.filter(f => f.language === l || !LANGS.includes(f.language)).map(f => `- ${f.problem} -> ${f.fix}`).join('\n') || 'Deliver a working branch.', attempt + 1)
    }))).filter(Boolean)
    reports = [...reports.filter(r => !failing.includes(r.language)), ...again]
  }
  const verdict = result && result.verdict
  if (verdict && verdict.approved) {
    summary.push(`${set.slug}: ${verdict.landed}`)
  } else {
    await integrate(`${set.slug}:park`, `In ${PR}, follow your Record instructions: mark ledger items ${set.ids.join(',')} parked with note "review rejected ${set.slug}: ${(verdict ? verdict.summary : 'no verdict').replace(/"/g, "'").slice(0, 300)}", add these new findings (JSON list in a temp file): ${JSON.stringify(discovered(verdict, reports))}, render, and commit the ledger as "docs: record ${set.slug} in the findings ledger".`)
    summary.push(`${set.slug}: parked (${verdict ? verdict.summary.slice(0, 160) : 'no verdict'})`)
  }
  log(summary[summary.length - 1])
}

let next = 0
await parallel(Array.from({ length: Math.min(INFLIGHT, args.sets.length) }, () => async () => {
  while (next < args.sets.length) await runSet(args.sets[next++])
}))
return { summary, head }
