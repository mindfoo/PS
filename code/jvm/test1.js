#!/usr/bin/env node
// test1.js — simple hello-world script to verify SCRIPT task execution
'use strict'

console.log('Hello from test1.js')
console.log('Arguments:', process.argv.slice(2))
console.log('Working directory:', process.cwd())

process.exit(0)
