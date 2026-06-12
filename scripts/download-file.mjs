import { createWriteStream } from 'node:fs'
import { get as httpsGet } from 'node:https'
import { get as httpGet } from 'node:http'

const [url, output] = process.argv.slice(2)

if (!url || !output) {
  console.error('Usage: node scripts/download-file.mjs <url> <output>')
  process.exit(2)
}

const download = (currentUrl, redirects = 0) => new Promise((resolve, reject) => {
  if (redirects > 10) {
    reject(new Error('Too many redirects'))
    return
  }

  const getter = currentUrl.startsWith('https:') ? httpsGet : httpGet
  const request = getter(currentUrl, (response) => {
    if ([301, 302, 303, 307, 308].includes(response.statusCode ?? 0)) {
      response.resume()
      const location = response.headers.location
      if (!location) {
        reject(new Error('Redirect without location header'))
        return
      }
      const nextUrl = new URL(location, currentUrl).toString()
      resolve(download(nextUrl, redirects + 1))
      return
    }

    if ((response.statusCode ?? 500) >= 400) {
      response.resume()
      reject(new Error(`Download failed with HTTP ${response.statusCode}`))
      return
    }

    const file = createWriteStream(output)
    response.pipe(file)
    file.on('finish', () => file.close(resolve))
    file.on('error', reject)
  })

  request.on('error', reject)
})

await download(url)
