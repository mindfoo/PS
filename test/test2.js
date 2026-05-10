#!/usr/bin/env node
// test2.js — compute fibonacci to verify script tasks can do real work
'use strict'

function fibonacci(n) {
  if (n <= 1) return n
  let a = 0, b = 1
  for (let i = 2; i <= n; i++) {
    const tmp = a + b
    a = b
    b = tmp
  }
  return b
}

const limit = parseInt(process.argv[2] ?? '10', 10)

console.log(`Computing Fibonacci numbers up to index ${limit}:`)
for (let i = 0; i <= limit; i++) {
  console.log(`  fib(${i}) = ${fibonacci(i)}`)
}
console.log('Done.')

process.exit(0)
