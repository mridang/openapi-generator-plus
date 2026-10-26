const vm = require('vm');

// Comprehensive candidate list from ECMAScript spec + TypeScript
const candidates = [
  'abstract', 'arguments', 'as', 'async', 'await',
  'boolean', 'break', 'byte',
  'case', 'catch', 'char', 'class', 'const', 'constructor', 'continue',
  'debugger', 'declare', 'default', 'delete', 'do', 'double',
  'else', 'enum', 'export', 'extends',
  'false', 'final', 'finally', 'float', 'for', 'from', 'function',
  'get', 'goto',
  'if', 'implements', 'import', 'in', 'instanceof', 'int', 'interface', 'is',
  'let', 'long',
  'module', 'native', 'new', 'null', 'number',
  'of', 'package', 'private', 'protected', 'public',
  'return',
  'set', 'short', 'static', 'string', 'super', 'switch', 'symbol', 'synchronized',
  'this', 'throw', 'throws', 'transient', 'true', 'try', 'type', 'typeof',
  'undefined', 'var', 'void', 'volatile',
  'while', 'with',
  'yield',
];

// Detect strict-mode reserved words via the runtime
const reserved = new Set();
for (const word of candidates) {
  try {
    new vm.Script(`'use strict'; var ${word} = 1;`);
  } catch {
    reserved.add(word);
  }
}

// TypeScript contextual keywords that should also be treated as reserved
// (not reserved by JS runtime but reserved in TypeScript context)
const tsContextual = [
  'abstract', 'any', 'as', 'async', 'await', 'boolean',
  'constructor', 'declare', 'from', 'get', 'is',
  'module', 'number', 'of', 'set', 'string', 'symbol', 'type',
];
for (const kw of tsContextual) {
  reserved.add(kw);
}

const sorted = Array.from(reserved).sort();
for (const kw of sorted) {
  console.log(kw);
}
