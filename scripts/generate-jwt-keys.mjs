import { mkdirSync, writeFileSync } from 'node:fs'
import { generateKeyPairSync } from 'node:crypto'

const { privateKey, publicKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' }
})

mkdirSync('secrets', { recursive: true })
writeFileSync('secrets/privateKey.pem', privateKey, { encoding: 'utf8', flag: 'wx' })
writeFileSync('secrets/publicKey.pem', publicKey, { encoding: 'utf8', flag: 'wx' })

console.log('JWT keys created in ./secrets')
