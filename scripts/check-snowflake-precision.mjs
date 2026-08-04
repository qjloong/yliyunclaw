import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../mateclaw-ui/src/', import.meta.url))
const sourceExtensions = new Set(['.js', '.jsx', '.ts', '.tsx', '.vue'])
const idConversion = /\b(?:Number|parseInt)\s*\([^)]*(?:\b(?:id|ID)\b|(?:Id|ID)\b)/
const idAssignment = /\b[A-Za-z_$][\w$]*(?:Id|ID|id)\s*=\s*(?:Number|parseInt)\s*\(/
const allowMarker = 'snowflake-precision-ok'

async function collectSourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = `${directory}/${entry.name}`
    if (entry.isDirectory()) return collectSourceFiles(path)
    const dot = entry.name.lastIndexOf('.')
    return dot >= 0 && sourceExtensions.has(entry.name.slice(dot)) ? [path] : []
  }))
  return nested.flat()
}

const violations = []
for (const file of await collectSourceFiles(sourceRoot)) {
  const lines = (await readFile(file, 'utf8')).split(/\r?\n/)
  for (const [index, line] of lines.entries()) {
    if (line.includes(allowMarker)) continue
    if (idConversion.test(line) || idAssignment.test(line)) {
      violations.push(`${file}:${index + 1}: ${line.trim()}`)
    }
  }
}

if (violations.length > 0) {
  console.error('Snowflake ID precision check failed. Keep backend Long IDs as strings:')
  for (const violation of violations) console.error(`  ${violation}`)
  console.error(`Use "${allowMarker}: <reason>" only for values proven not to be entity IDs.`)
  process.exitCode = 1
} else {
  console.log('Snowflake ID precision check passed.')
}
