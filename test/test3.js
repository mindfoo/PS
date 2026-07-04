// Usage: node test3.js --a 6 --b 7
const args = process.argv.slice(2)

function getArg(name) {
    const i = args.indexOf(`--${name}`)
    return i !== -1 ? Number(args[i + 1]) : null
}

const a = getArg('a')
const b = getArg('b')

if (a === null || b === null) {
    console.error('Usage: node multiply.js --a <num> --b <num>')
    process.exit(1)
}

const result = a * b
console.log(JSON.stringify({ a, b, result }))
process.exit(0)