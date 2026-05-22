const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { spawnSync } = require('node:child_process')

const DEFAULT_MAX_TREE_DEPTH = 3
const DEFAULT_MAX_TREE_ENTRIES = 400
const DEFAULT_MAX_GLOB_RESULTS = 500
const DEFAULT_MAX_GREP_RESULTS = 200
const DEFAULT_MAX_FILE_BYTES = 512 * 1024
const DEFAULT_MAX_SNIPPET_LINES = 400
const DEFAULT_MAX_DIFF_BYTES = 200 * 1024
const DEFAULT_COMMAND_TIMEOUT_MS = 15000
const DEFAULT_COMMAND_MAX_OUTPUT_BYTES = 64 * 1024
const DEFAULT_MAX_PATCH_OPERATIONS = 50
const DEFAULT_APPROVAL_TTL_MS = 10 * 60 * 1000

const READONLY_COMMAND_SPECS = {
  git: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
    ],
  },
  node: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
      ['-v'],
    ],
  },
  npm: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
      ['-v'],
    ],
  },
  pnpm: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
      ['-v'],
    ],
  },
  java: {
    mode: 'exact',
    allowedArgs: [
      ['-version'],
      ['--version'],
    ],
  },
  mvn: {
    mode: 'exact',
    allowedArgs: [
      ['-v'],
      ['--version'],
    ],
  },
  python: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
      ['-V'],
    ],
  },
  python3: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
      ['-V'],
    ],
  },
  py: {
    mode: 'exact',
    allowedArgs: [
      ['--version'],
      ['-V'],
    ],
  },
}

const APPROVAL_COMMAND_SPECS = {
  pnpm: {
    mode: 'exact',
    riskLevel: 'medium',
    allowedArgs: [
      ['test'],
      ['lint'],
      ['build'],
      ['run', 'test'],
      ['run', 'lint'],
      ['run', 'build'],
    ],
  },
  npm: {
    mode: 'exact',
    riskLevel: 'medium',
    allowedArgs: [
      ['test'],
      ['run', 'test'],
      ['run', 'lint'],
      ['run', 'build'],
    ],
  },
  mvn: {
    mode: 'exact',
    riskLevel: 'medium',
    allowedArgs: [
      ['test'],
      ['verify'],
      ['-q', 'test'],
      ['-q', 'verify'],
    ],
  },
  python: {
    mode: 'exact',
    riskLevel: 'medium',
    allowedArgs: [
      ['-m', 'pytest'],
    ],
  },
  python3: {
    mode: 'exact',
    riskLevel: 'medium',
    allowedArgs: [
      ['-m', 'pytest'],
    ],
  },
  node: {
    mode: 'exact',
    riskLevel: 'medium',
    allowedArgs: [
      ['--test'],
    ],
  },
}

const pendingCommandApprovals = new Map()
const approvedCommandTokens = new Map()

function isoNow() {
  return new Date().toISOString()
}

function buildSharedToolPayload(options = {}) {
  return {
    schemaVersion: 'desktop-local-tool-result.v1',
    source: 'desktop-local',
    timestamp: isoNow(),
    toolName: String(options.toolName || ''),
    mode: String(options.mode || 'desktop-only'),
    status: String(options.status || 'success'),
    riskLevel: String(options.riskLevel || 'low'),
    requiresApproval: options.requiresApproval === true,
    workspaceRoot: options.workspaceRoot || null,
    cwd: options.cwd || null,
    summary: String(options.summary || ''),
    evidence: options.evidence || {},
    approval: options.approval || null,
    truncation: options.truncation || null,
  }
}

function normalizeRel(filePath) {
  return String(filePath || '').replace(/\\/g, '/').replace(/^\.\//, '')
}

function ensureDirectoryExists(dirPath) {
  if (!dirPath) {
    throw new Error('workspace root is required')
  }
  const absolute = path.resolve(String(dirPath))
  if (!fs.existsSync(absolute)) {
    throw new Error(`workspace root does not exist: ${absolute}`)
  }
  const stat = fs.statSync(absolute)
  if (!stat.isDirectory()) {
    throw new Error(`workspace root is not a directory: ${absolute}`)
  }
  return absolute
}

function ensureInsideRoot(rootPath, targetPath) {
  const absoluteRoot = ensureDirectoryExists(rootPath)
  const absoluteTarget = path.resolve(targetPath)
  const relative = path.relative(absoluteRoot, absoluteTarget)
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`path is outside workspace root: ${absoluteTarget}`)
  }
  return absoluteTarget
}

function resolveWorkspacePath(rootPath, targetPath = '') {
  const absoluteRoot = ensureDirectoryExists(rootPath)
  const candidate = path.isAbsolute(String(targetPath || ''))
    ? String(targetPath)
    : path.join(absoluteRoot, String(targetPath || ''))
  return ensureInsideRoot(absoluteRoot, candidate)
}

function toGlobRegExp(pattern) {
  const normalized = normalizeRel(pattern || '**')
  let source = '^'
  for (let i = 0; i < normalized.length; i += 1) {
    const char = normalized[i]
    const next = normalized[i + 1]
    if (char === '*') {
      if (next === '*') {
        source += '.*'
        i += 1
      } else {
        source += '[^/]*'
      }
      continue
    }
    if (char === '?') {
      source += '.'
      continue
    }
    if ('\\.^$+{}()|[]'.includes(char)) {
      source += `\\${char}`
      continue
    }
    source += char
  }
  source += '$'
  return new RegExp(source)
}

function matchesGlob(relPath, pattern) {
  return toGlobRegExp(pattern).test(normalizeRel(relPath))
}

function shouldIncludeName(name, includeHidden) {
  if (includeHidden) {
    return true
  }
  return !String(name || '').startsWith('.')
}

function isBinaryBuffer(buffer) {
  const length = Math.min(buffer.length, 4096)
  for (let i = 0; i < length; i += 1) {
    if (buffer[i] === 0) {
      return true
    }
  }
  return false
}

function listWorkspaceTree(options = {}) {
  const rootPath = ensureDirectoryExists(options.rootPath)
  const basePath = resolveWorkspacePath(rootPath, options.basePath || '')
  const includeHidden = options.includeHidden === true
  const maxDepth = Math.max(0, Number.parseInt(String(options.maxDepth ?? DEFAULT_MAX_TREE_DEPTH), 10) || DEFAULT_MAX_TREE_DEPTH)
  const maxEntries = Math.max(1, Number.parseInt(String(options.maxEntries ?? DEFAULT_MAX_TREE_ENTRIES), 10) || DEFAULT_MAX_TREE_ENTRIES)
  let entryCount = 0
  let truncated = false

  function walk(currentPath, depth) {
    if (entryCount >= maxEntries) {
      truncated = true
      return []
    }
    const names = fs.readdirSync(currentPath, { withFileTypes: true })
      .filter((entry) => shouldIncludeName(entry.name, includeHidden))
      .sort((a, b) => {
        if (a.isDirectory() && !b.isDirectory()) return -1
        if (!a.isDirectory() && b.isDirectory()) return 1
        return a.name.localeCompare(b.name)
      })

    const items = []
    for (const entry of names) {
      if (entryCount >= maxEntries) {
        truncated = true
        break
      }
      const absoluteChild = path.join(currentPath, entry.name)
      const relativeChild = normalizeRel(path.relative(rootPath, absoluteChild))
      const node = {
        name: entry.name,
        relativePath: relativeChild,
        type: entry.isDirectory() ? 'directory' : 'file',
      }
      entryCount += 1
      if (entry.isDirectory() && depth < maxDepth) {
        node.children = walk(absoluteChild, depth + 1)
      }
      items.push(node)
    }
    return items
  }

  const result = {
    rootPath,
    basePath: normalizeRel(path.relative(rootPath, basePath)),
    maxDepth,
    maxEntries,
    entryCount,
    truncated,
    nodes: walk(basePath, 0),
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'workspace.tree',
      workspaceRoot: rootPath,
      cwd: result.basePath || '.',
      summary: `listed workspace tree under ${result.basePath || '.'}`,
      evidence: {
        basePath: result.basePath,
        entryCount: result.entryCount,
        maxDepth: result.maxDepth,
      },
      truncation: result.truncated ? { kind: 'entry-count', limit: result.maxEntries } : null,
    }),
  }
}

function globWorkspaceFiles(options = {}) {
  const rootPath = ensureDirectoryExists(options.rootPath)
  const includePattern = String(options.includePattern || '**')
  const excludePatterns = Array.isArray(options.excludePatterns) ? options.excludePatterns.map((item) => String(item)) : []
  const includeHidden = options.includeHidden === true
  const maxResults = Math.max(1, Number.parseInt(String(options.maxResults ?? DEFAULT_MAX_GLOB_RESULTS), 10) || DEFAULT_MAX_GLOB_RESULTS)
  const matches = []
  let truncated = false

  function walk(currentPath) {
    if (matches.length >= maxResults) {
      truncated = true
      return
    }
    const entries = fs.readdirSync(currentPath, { withFileTypes: true })
    for (const entry of entries) {
      if (!shouldIncludeName(entry.name, includeHidden)) {
        continue
      }
      const absoluteChild = path.join(currentPath, entry.name)
      const relativeChild = normalizeRel(path.relative(rootPath, absoluteChild))
      if (excludePatterns.some((pattern) => matchesGlob(relativeChild, pattern))) {
        continue
      }
      if (entry.isDirectory()) {
        walk(absoluteChild)
      } else if (matchesGlob(relativeChild, includePattern)) {
        matches.push(relativeChild)
        if (matches.length >= maxResults) {
          truncated = true
          return
        }
      }
    }
  }

  walk(rootPath)

  const result = {
    rootPath,
    includePattern,
    excludePatterns,
    maxResults,
    truncated,
    matches,
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'workspace.glob',
      workspaceRoot: rootPath,
      summary: `glob matched ${matches.length} file(s) for ${includePattern}`,
      evidence: {
        includePattern,
        matchCount: matches.length,
        excludePatterns,
      },
      truncation: truncated ? { kind: 'result-count', limit: maxResults } : null,
    }),
  }
}

function grepWorkspaceFiles(options = {}) {
  const rootPath = ensureDirectoryExists(options.rootPath)
  const query = String(options.query || '')
  if (!query) {
    throw new Error('grep query is required')
  }
  const includePattern = String(options.includePattern || '**')
  const excludePatterns = Array.isArray(options.excludePatterns) ? options.excludePatterns.map((item) => String(item)) : []
  const includeHidden = options.includeHidden === true
  const caseSensitive = options.caseSensitive === true
  const isRegexp = options.isRegexp === true
  const maxResults = Math.max(1, Number.parseInt(String(options.maxResults ?? DEFAULT_MAX_GREP_RESULTS), 10) || DEFAULT_MAX_GREP_RESULTS)
  const maxFileBytes = Math.max(1024, Number.parseInt(String(options.maxFileBytes ?? DEFAULT_MAX_FILE_BYTES), 10) || DEFAULT_MAX_FILE_BYTES)
  const flags = caseSensitive ? 'g' : 'gi'
  const matcher = isRegexp ? new RegExp(query, flags) : null
  const results = []
  let truncated = false

  function visit(currentPath) {
    if (results.length >= maxResults) {
      truncated = true
      return
    }
    const entries = fs.readdirSync(currentPath, { withFileTypes: true })
    for (const entry of entries) {
      if (!shouldIncludeName(entry.name, includeHidden)) {
        continue
      }
      const absoluteChild = path.join(currentPath, entry.name)
      const relativeChild = normalizeRel(path.relative(rootPath, absoluteChild))
      if (excludePatterns.some((pattern) => matchesGlob(relativeChild, pattern))) {
        continue
      }
      if (entry.isDirectory()) {
        visit(absoluteChild)
        if (results.length >= maxResults) {
          truncated = true
          return
        }
        continue
      }
      if (!matchesGlob(relativeChild, includePattern)) {
        continue
      }
      const stat = fs.statSync(absoluteChild)
      if (stat.size > maxFileBytes) {
        continue
      }
      const buffer = fs.readFileSync(absoluteChild)
      if (isBinaryBuffer(buffer)) {
        continue
      }
      const text = buffer.toString('utf-8')
      const lines = text.split(/\r?\n/)
      for (let index = 0; index < lines.length; index += 1) {
        const lineText = lines[index]
        if (isRegexp) {
          matcher.lastIndex = 0
          const match = matcher.exec(lineText)
          if (!match) {
            continue
          }
          results.push({
            filePath: relativeChild,
            line: index + 1,
            column: (match.index || 0) + 1,
            text: lineText,
          })
        } else {
          const haystack = caseSensitive ? lineText : lineText.toLowerCase()
          const needle = caseSensitive ? query : query.toLowerCase()
          const column = haystack.indexOf(needle)
          if (column < 0) {
            continue
          }
          results.push({
            filePath: relativeChild,
            line: index + 1,
            column: column + 1,
            text: lineText,
          })
        }
        if (results.length >= maxResults) {
          truncated = true
          return
        }
      }
    }
  }

  visit(rootPath)

  const result = {
    rootPath,
    query,
    isRegexp,
    caseSensitive,
    includePattern,
    excludePatterns,
    maxResults,
    truncated,
    results,
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'workspace.grep',
      workspaceRoot: rootPath,
      summary: `grep found ${results.length} hit(s) for ${query}`,
      evidence: {
        query,
        isRegexp,
        caseSensitive,
        hitCount: results.length,
        includePattern,
      },
      truncation: truncated ? { kind: 'result-count', limit: maxResults } : null,
    }),
  }
}

function readWorkspaceSnippet(options = {}) {
  const rootPath = ensureDirectoryExists(options.rootPath)
  const absolutePath = resolveWorkspacePath(rootPath, options.filePath)
  const startLine = Math.max(1, Number.parseInt(String(options.startLine ?? 1), 10) || 1)
  const requestedEndLine = Number.parseInt(String(options.endLine ?? startLine), 10) || startLine
  const maxLines = Math.max(1, Number.parseInt(String(options.maxLines ?? DEFAULT_MAX_SNIPPET_LINES), 10) || DEFAULT_MAX_SNIPPET_LINES)
  const content = fs.readFileSync(absolutePath, 'utf-8')
  const lines = content.split(/\r?\n/)
  const endLine = Math.min(lines.length, Math.max(startLine, requestedEndLine), startLine + maxLines - 1)
  const snippet = lines.slice(startLine - 1, endLine).join('\n')

  const result = {
    rootPath,
    filePath: normalizeRel(path.relative(rootPath, absolutePath)),
    startLine,
    endLine,
    totalLines: lines.length,
    truncated: requestedEndLine > endLine,
    snippet,
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'workspace.read_snippet',
      workspaceRoot: rootPath,
      summary: `read ${result.filePath}:${startLine}-${endLine}`,
      evidence: {
        filePath: result.filePath,
        startLine,
        endLine,
        totalLines: result.totalLines,
      },
      truncation: result.truncated ? { kind: 'line-range', limit: maxLines } : null,
    }),
  }
}

function sha256Text(text) {
  return crypto.createHash('sha256').update(String(text || ''), 'utf-8').digest('hex')
}

function statWorkspaceFile(rootPath, filePath) {
  const absolutePath = resolveWorkspacePath(rootPath, filePath)
  if (!fs.existsSync(absolutePath)) {
    return {
      exists: false,
      absolutePath,
      relativePath: normalizeRel(path.relative(rootPath, absolutePath)),
    }
  }
  const stat = fs.statSync(absolutePath)
  if (!stat.isFile()) {
    throw new Error(`target is not a file: ${absolutePath}`)
  }
  const content = fs.readFileSync(absolutePath, 'utf-8')
  return {
    exists: true,
    absolutePath,
    relativePath: normalizeRel(path.relative(rootPath, absolutePath)),
    size: stat.size,
    sha256: sha256Text(content),
    content,
  }
}

function ensurePatchOperationAllowed(operation) {
  const type = String(operation?.type || '').trim()
  if (!['replace', 'append', 'prepend', 'set'].includes(type)) {
    throw new Error(`unsupported patch operation type: ${type}`)
  }
  return type
}

function applyPatchOperations(inputContent, operations) {
  let content = String(inputContent || '')
  const applied = []

  for (const rawOperation of operations) {
    const type = ensurePatchOperationAllowed(rawOperation)
    if (type === 'set') {
      content = String(rawOperation.content || '')
      applied.push({ type })
      continue
    }
    if (type === 'append') {
      content += String(rawOperation.text || '')
      applied.push({ type, addedLength: String(rawOperation.text || '').length })
      continue
    }
    if (type === 'prepend') {
      content = String(rawOperation.text || '') + content
      applied.push({ type, addedLength: String(rawOperation.text || '').length })
      continue
    }

    const oldText = String(rawOperation.oldText || '')
    const newText = String(rawOperation.newText || '')
    if (!oldText) {
      throw new Error('replace operation requires non-empty oldText')
    }
    const replaceAll = rawOperation.replaceAll === true
    if (replaceAll) {
      if (!content.includes(oldText)) {
        throw new Error('replaceAll operation oldText not found in file content')
      }
      const occurrences = content.split(oldText).length - 1
      content = content.split(oldText).join(newText)
      applied.push({ type, replaceAll: true, occurrences })
      continue
    }
    const firstIndex = content.indexOf(oldText)
    if (firstIndex < 0) {
      throw new Error('replace operation oldText not found in file content')
    }
    const secondIndex = content.indexOf(oldText, firstIndex + oldText.length)
    if (secondIndex >= 0) {
      throw new Error('replace operation oldText is ambiguous; use replaceAll or provide a more specific match')
    }
    content = content.slice(0, firstIndex) + newText + content.slice(firstIndex + oldText.length)
    applied.push({ type, replaceAll: false, position: firstIndex })
  }

  return {
    content,
    applied,
  }
}

function writeWorkspacePatch(options = {}) {
  const rootPath = ensureDirectoryExists(options.rootPath)
  const filePath = String(options.filePath || '').trim()
  if (!filePath) {
    throw new Error('filePath is required')
  }
  const operations = Array.isArray(options.operations) ? options.operations : []
  if (!operations.length) {
    throw new Error('operations are required')
  }
  if (operations.length > DEFAULT_MAX_PATCH_OPERATIONS) {
    throw new Error(`too many patch operations: ${operations.length}`)
  }

  const before = statWorkspaceFile(rootPath, filePath)
  const allowCreate = options.allowCreate === true
  if (!before.exists && !allowCreate) {
    throw new Error(`file does not exist and allowCreate is false: ${filePath}`)
  }
  const originalContent = before.exists ? before.content : ''
  const expectedSha256 = String(options.expectedSha256 || '').trim()
  if (expectedSha256) {
    const actualSha256 = before.exists ? before.sha256 : sha256Text('')
    if (expectedSha256 !== actualSha256) {
      throw new Error(`expectedSha256 mismatch for ${filePath}`)
    }
  }

  const patched = applyPatchOperations(originalContent, operations)
  const nextContent = patched.content
  const absolutePath = before.absolutePath
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true })
  fs.writeFileSync(absolutePath, nextContent, 'utf-8')

  const result = {
    rootPath,
    filePath: before.relativePath,
    created: !before.exists,
    changed: nextContent !== originalContent,
    beforeSha256: before.exists ? before.sha256 : sha256Text(''),
    afterSha256: sha256Text(nextContent),
    beforeLength: originalContent.length,
    afterLength: nextContent.length,
    operationsApplied: patched.applied,
    mode: 'workspace-write',
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'workspace.write_patch',
      mode: 'workspace-write',
      riskLevel: 'medium',
      requiresApproval: false,
      workspaceRoot: rootPath,
      summary: `${result.created ? 'created' : 'patched'} ${result.filePath}`,
      evidence: {
        filePath: result.filePath,
        created: result.created,
        changed: result.changed,
        operationsApplied: result.operationsApplied,
        beforeLength: result.beforeLength,
        afterLength: result.afterLength,
      },
    }),
  }
}

function runGit(rootPath, args, options = {}) {
  const cwd = ensureDirectoryExists(rootPath)
  const result = spawnSync('git', ['-C', cwd, ...args], {
    cwd,
    encoding: 'utf-8',
    timeout: Math.max(1000, Number.parseInt(String(options.timeout ?? 15000), 10) || 15000),
    windowsHide: true,
    maxBuffer: Math.max(1024 * 1024, Number.parseInt(String(options.maxBuffer ?? 2 * 1024 * 1024), 10) || 2 * 1024 * 1024),
  })

  if (result.error) {
    throw new Error(`git execution failed: ${result.error.message}`)
  }
  if (result.status !== 0) {
    const message = String(result.stderr || result.stdout || '').trim() || `git exited with code ${result.status}`
    throw new Error(message)
  }
  return String(result.stdout || '')
}

function resolveGitRepo(rootPath) {
  const workspaceRoot = ensureDirectoryExists(rootPath)
  const repoRoot = runGit(workspaceRoot, ['rev-parse', '--show-toplevel']).trim()
  return {
    workspaceRoot,
    repoRoot: path.resolve(repoRoot),
  }
}

function parseGitStatusLine(line) {
  const trimmed = String(line || '')
  if (!trimmed) {
    return null
  }
  if (trimmed.startsWith('## ')) {
    return {
      type: 'branch',
      value: trimmed.slice(3),
    }
  }
  const indexStatus = trimmed[0] || ' '
  const workTreeStatus = trimmed[1] || ' '
  const rawPath = trimmed.slice(3).trim()
  const renameParts = rawPath.split(' -> ')
  return {
    type: 'entry',
    indexStatus,
    workTreeStatus,
    path: normalizeRel(renameParts[renameParts.length - 1] || rawPath),
    originalPath: renameParts.length > 1 ? normalizeRel(renameParts[0]) : null,
    rawPath,
  }
}

function getGitStatus(options = {}) {
  const { workspaceRoot, repoRoot } = resolveGitRepo(options.rootPath)
  const output = runGit(workspaceRoot, ['status', '--short', '--branch', '--untracked-files=all'])
  const lines = output.split(/\r?\n/).filter(Boolean)
  let branch = null
  const entries = []

  for (const line of lines) {
    const parsed = parseGitStatusLine(line)
    if (!parsed) {
      continue
    }
    if (parsed.type === 'branch') {
      branch = parsed.value
      continue
    }
    entries.push(parsed)
  }

  const result = {
    workspaceRoot,
    repoRoot,
    branch,
    clean: entries.length === 0,
    entries,
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'git.status',
      workspaceRoot,
      summary: result.clean ? 'git working tree is clean' : `git status has ${entries.length} change(s)`,
      evidence: {
        repoRoot,
        branch,
        clean: result.clean,
        changeCount: entries.length,
      },
    }),
  }
}

function getGitDiff(options = {}) {
  const { workspaceRoot, repoRoot } = resolveGitRepo(options.rootPath)
  const staged = options.staged === true
  const contextLines = Math.max(0, Number.parseInt(String(options.contextLines ?? 3), 10) || 3)
  const maxBytes = Math.max(4096, Number.parseInt(String(options.maxBytes ?? DEFAULT_MAX_DIFF_BYTES), 10) || DEFAULT_MAX_DIFF_BYTES)
  const pathspecs = Array.isArray(options.pathspecs)
    ? options.pathspecs.map((item) => normalizeRel(item)).filter(Boolean)
    : []

  const statArgs = ['diff', '--stat']
  const diffArgs = ['diff', `--unified=${contextLines}`, '--no-ext-diff']
  if (staged) {
    statArgs.push('--cached')
    diffArgs.push('--cached')
  }
  if (pathspecs.length) {
    statArgs.push('--', ...pathspecs)
    diffArgs.push('--', ...pathspecs)
  }

  const stat = runGit(workspaceRoot, statArgs).trim()
  const rawDiff = runGit(workspaceRoot, diffArgs)
  const truncated = Buffer.byteLength(rawDiff, 'utf-8') > maxBytes
  const diff = truncated ? Buffer.from(rawDiff, 'utf-8').subarray(0, maxBytes).toString('utf-8') : rawDiff

  const result = {
    workspaceRoot,
    repoRoot,
    staged,
    pathspecs,
    contextLines,
    truncated,
    stat,
    diff,
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'git.diff',
      workspaceRoot,
      summary: `${staged ? 'staged' : 'working-tree'} git diff${pathspecs.length ? ` for ${pathspecs.join(', ')}` : ''}`,
      evidence: {
        repoRoot,
        staged,
        pathspecs,
        contextLines,
        stat,
      },
      truncation: truncated ? { kind: 'byte-size', limit: maxBytes } : null,
    }),
  }
}

function arraysEqual(left, right) {
  if (left.length !== right.length) {
    return false
  }
  for (let i = 0; i < left.length; i += 1) {
    if (left[i] !== right[i]) {
      return false
    }
  }
  return true
}

function normalizeCommandArgs(args) {
  if (!Array.isArray(args)) {
    return []
  }
  return args.map((item) => String(item)).filter((item) => item.length > 0)
}

function cleanupExpiredCommandApprovals(now = Date.now()) {
  for (const [requestId, entry] of pendingCommandApprovals.entries()) {
    if (entry.expiresAt <= now) {
      pendingCommandApprovals.delete(requestId)
    }
  }
  for (const [token, entry] of approvedCommandTokens.entries()) {
    if (entry.expiresAt <= now) {
      approvedCommandTokens.delete(token)
    }
  }
}

function ensureReadonlyCommandAllowed(command, args) {
  const normalizedCommand = String(command || '').trim().toLowerCase()
  const spec = READONLY_COMMAND_SPECS[normalizedCommand]
  if (!spec) {
    throw new Error(`command is not allowed in readonly desktop mode: ${command}`)
  }
  if (spec.mode === 'exact') {
    const matched = spec.allowedArgs.some((allowed) => arraysEqual(args, allowed))
    if (!matched) {
      throw new Error(`arguments are not allowed for readonly desktop command: ${command} ${args.join(' ')}`.trim())
    }
  }
  return normalizedCommand
}

function classifyApprovalCommand(command, args) {
  const normalizedCommand = String(command || '').trim().toLowerCase()
  const spec = APPROVAL_COMMAND_SPECS[normalizedCommand]
  if (!spec) {
    return null
  }
  if (spec.mode === 'exact') {
    const matched = spec.allowedArgs.some((allowed) => arraysEqual(args, allowed))
    if (!matched) {
      return null
    }
  }
  return {
    command: normalizedCommand,
    riskLevel: spec.riskLevel || 'medium',
  }
}

function truncateTextByBytes(text, maxBytes) {
  const raw = Buffer.from(String(text || ''), 'utf-8')
  if (raw.byteLength <= maxBytes) {
    return {
      text: raw.toString('utf-8'),
      truncated: false,
    }
  }
  return {
    text: raw.subarray(0, maxBytes).toString('utf-8'),
    truncated: true,
  }
}

function runReadonlyCommand(options = {}) {
  const workspaceRoot = ensureDirectoryExists(options.rootPath)
  const cwd = options.cwd ? resolveWorkspacePath(workspaceRoot, options.cwd) : workspaceRoot
  const command = String(options.command || '').trim()
  if (!command) {
    throw new Error('command is required')
  }
  const args = normalizeCommandArgs(options.args)
  ensureReadonlyCommandAllowed(command, args)

  const timeout = Math.max(1000, Number.parseInt(String(options.timeoutMs ?? DEFAULT_COMMAND_TIMEOUT_MS), 10) || DEFAULT_COMMAND_TIMEOUT_MS)
  const maxOutputBytes = Math.max(4096, Number.parseInt(String(options.maxOutputBytes ?? DEFAULT_COMMAND_MAX_OUTPUT_BYTES), 10) || DEFAULT_COMMAND_MAX_OUTPUT_BYTES)

  const procResult = spawnSync(command, args, {
    cwd,
    encoding: 'utf-8',
    timeout,
    windowsHide: true,
    shell: false,
    maxBuffer: Math.max(maxOutputBytes * 2, 1024 * 1024),
  })

  if (procResult.error) {
    throw new Error(`readonly command failed: ${procResult.error.message}`)
  }

  const stdout = truncateTextByBytes(procResult.stdout || '', maxOutputBytes)
  const stderr = truncateTextByBytes(procResult.stderr || '', maxOutputBytes)

  const result = {
    workspaceRoot,
    cwd: normalizeRel(path.relative(workspaceRoot, cwd)),
    command,
    args,
    exitCode: typeof procResult.status === 'number' ? procResult.status : null,
    signal: procResult.signal || null,
    timedOut: procResult.signal === 'SIGTERM' && typeof procResult.status !== 'number',
    stdout: stdout.text,
    stderr: stderr.text,
    truncated: stdout.truncated || stderr.truncated,
    mode: 'readonly',
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'command.run.readonly',
      mode: 'readonly',
      workspaceRoot,
      cwd: result.cwd,
      summary: `ran readonly command: ${command} ${args.join(' ')}`.trim(),
      evidence: {
        command,
        args,
        exitCode: result.exitCode,
        signal: result.signal,
      },
      truncation: result.truncated ? { kind: 'byte-size', limit: maxOutputBytes } : null,
    }),
  }
}

function executeCommandProcess(command, args, executionOptions = {}) {
  const timeout = Math.max(1000, Number.parseInt(String(executionOptions.timeoutMs ?? DEFAULT_COMMAND_TIMEOUT_MS), 10) || DEFAULT_COMMAND_TIMEOUT_MS)
  const maxOutputBytes = Math.max(4096, Number.parseInt(String(executionOptions.maxOutputBytes ?? DEFAULT_COMMAND_MAX_OUTPUT_BYTES), 10) || DEFAULT_COMMAND_MAX_OUTPUT_BYTES)
  const result = spawnSync(command, args, {
    cwd: executionOptions.cwd,
    encoding: 'utf-8',
    timeout,
    windowsHide: true,
    shell: false,
    maxBuffer: Math.max(maxOutputBytes * 2, 1024 * 1024),
  })
  if (result.error) {
    throw new Error(`command failed: ${result.error.message}`)
  }
  const stdout = truncateTextByBytes(result.stdout || '', maxOutputBytes)
  const stderr = truncateTextByBytes(result.stderr || '', maxOutputBytes)
  return {
    exitCode: typeof result.status === 'number' ? result.status : null,
    signal: result.signal || null,
    timedOut: result.signal === 'SIGTERM' && typeof result.status !== 'number',
    stdout: stdout.text,
    stderr: stderr.text,
    truncated: stdout.truncated || stderr.truncated,
  }
}

function prepareApprovalCommand(options = {}) {
  cleanupExpiredCommandApprovals()
  const workspaceRoot = ensureDirectoryExists(options.rootPath)
  const cwd = options.cwd ? resolveWorkspacePath(workspaceRoot, options.cwd) : workspaceRoot
  const command = String(options.command || '').trim()
  if (!command) {
    throw new Error('command is required')
  }
  const args = normalizeCommandArgs(options.args)
  const classification = classifyApprovalCommand(command, args)
  if (!classification) {
    throw new Error(`command is not eligible for approval-aware execution: ${command} ${args.join(' ')}`.trim())
  }

  const requestId = crypto.randomUUID()
  const expiresAt = Date.now() + Math.max(1000, Number.parseInt(String(options.approvalTtlMs ?? DEFAULT_APPROVAL_TTL_MS), 10) || DEFAULT_APPROVAL_TTL_MS)
  const payload = {
    requestId,
    workspaceRoot,
    cwd,
    command: classification.command,
    args,
    riskLevel: classification.riskLevel,
    requiresApproval: true,
    expiresAt,
    summary: `${classification.command} ${args.join(' ')}`.trim(),
  }
  pendingCommandApprovals.set(requestId, payload)
  const result = {
    requestId,
    workspaceRoot,
    cwd: normalizeRel(path.relative(workspaceRoot, cwd)),
    command: classification.command,
    args,
    riskLevel: classification.riskLevel,
    requiresApproval: true,
    expiresAt,
    summary: payload.summary,
    mode: 'approval-required',
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'command.run.approval.prepare',
      mode: 'approval-required',
      riskLevel: classification.riskLevel,
      requiresApproval: true,
      workspaceRoot,
      cwd: result.cwd,
      summary: result.summary,
      evidence: {
        command: classification.command,
        args,
        requestId,
      },
      approval: {
        requestId,
        expiresAt,
        state: 'pending',
      },
    }),
  }
}

function approvePreparedCommand(options = {}) {
  cleanupExpiredCommandApprovals()
  const requestId = String(options.requestId || '').trim()
  if (!requestId) {
    throw new Error('requestId is required')
  }
  const pending = pendingCommandApprovals.get(requestId)
  if (!pending) {
    throw new Error(`pending command approval not found: ${requestId}`)
  }
  pendingCommandApprovals.delete(requestId)
  const approvalToken = crypto.randomUUID()
  const expiresAt = Date.now() + Math.max(1000, Number.parseInt(String(options.executionTtlMs ?? DEFAULT_APPROVAL_TTL_MS), 10) || DEFAULT_APPROVAL_TTL_MS)
  approvedCommandTokens.set(approvalToken, {
    ...pending,
    approvalToken,
    expiresAt,
  })
  const result = {
    requestId,
    approvalToken,
    expiresAt,
    riskLevel: pending.riskLevel,
    summary: pending.summary,
    mode: 'approved',
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'command.run.approval.approve',
      mode: 'approved',
      riskLevel: pending.riskLevel,
      requiresApproval: true,
      workspaceRoot: pending.workspaceRoot,
      cwd: normalizeRel(path.relative(pending.workspaceRoot, pending.cwd)),
      summary: pending.summary,
      evidence: {
        requestId,
        command: pending.command,
        args: pending.args,
      },
      approval: {
        requestId,
        approvalToken,
        expiresAt,
        state: 'approved',
      },
    }),
  }
}

function denyPreparedCommand(options = {}) {
  cleanupExpiredCommandApprovals()
  const requestId = String(options.requestId || '').trim()
  if (!requestId) {
    throw new Error('requestId is required')
  }
  const pending = pendingCommandApprovals.get(requestId)
  if (!pending) {
    throw new Error(`pending command approval not found: ${requestId}`)
  }
  pendingCommandApprovals.delete(requestId)
  const result = {
    requestId,
    riskLevel: pending.riskLevel,
    summary: pending.summary,
    mode: 'denied',
    status: 'denied',
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'command.run.approval.deny',
      mode: 'denied',
      status: 'denied',
      riskLevel: pending.riskLevel,
      requiresApproval: true,
      workspaceRoot: pending.workspaceRoot,
      cwd: normalizeRel(path.relative(pending.workspaceRoot, pending.cwd)),
      summary: pending.summary,
      evidence: {
        requestId,
        command: pending.command,
        args: pending.args,
      },
      approval: {
        requestId,
        expiresAt: pending.expiresAt,
        state: 'denied',
      },
    }),
  }
}

function executeApprovedCommand(options = {}) {
  cleanupExpiredCommandApprovals()
  const approvalToken = String(options.approvalToken || '').trim()
  if (!approvalToken) {
    throw new Error('approvalToken is required')
  }
  const approved = approvedCommandTokens.get(approvalToken)
  if (!approved) {
    throw new Error(`approved command token not found or expired: ${approvalToken}`)
  }
  approvedCommandTokens.delete(approvalToken)
  const executed = executeCommandProcess(approved.command, approved.args, {
    cwd: approved.cwd,
    timeoutMs: options.timeoutMs,
    maxOutputBytes: options.maxOutputBytes,
  })
  const result = {
    workspaceRoot: approved.workspaceRoot,
    cwd: normalizeRel(path.relative(approved.workspaceRoot, approved.cwd)),
    command: approved.command,
    args: approved.args,
    riskLevel: approved.riskLevel,
    requiresApproval: true,
    approvalToken,
    mode: 'approval-executed',
    ...executed,
  }
  return {
    ...result,
    sharedPayload: buildSharedToolPayload({
      toolName: 'command.run.approval.execute',
      mode: 'approval-executed',
      riskLevel: approved.riskLevel,
      requiresApproval: true,
      workspaceRoot: approved.workspaceRoot,
      cwd: result.cwd,
      summary: `executed approved command: ${approved.command} ${approved.args.join(' ')}`.trim(),
      evidence: {
        command: approved.command,
        args: approved.args,
        exitCode: result.exitCode,
        signal: result.signal,
      },
      approval: {
        requestId: approved.requestId,
        approvalToken,
        state: 'executed',
      },
      truncation: result.truncated ? { kind: 'byte-size', limit: Number.parseInt(String(options.maxOutputBytes ?? DEFAULT_COMMAND_MAX_OUTPUT_BYTES), 10) || DEFAULT_COMMAND_MAX_OUTPUT_BYTES } : null,
    }),
  }
}

module.exports = {
  listWorkspaceTree,
  globWorkspaceFiles,
  grepWorkspaceFiles,
  readWorkspaceSnippet,
  writeWorkspacePatch,
  getGitStatus,
  getGitDiff,
  runReadonlyCommand,
  prepareApprovalCommand,
  approvePreparedCommand,
  denyPreparedCommand,
  executeApprovedCommand,
}
