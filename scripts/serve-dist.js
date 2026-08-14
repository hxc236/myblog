// 本地集成冒烟用静态服务器：模拟 Render Static Site 的 SPA 重写行为
// （非静态资源路径全部重写到应用入口 index.html）。
const http = require('http')
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', 'myblogweb', 'dist')
const PORT = Number(process.env.PORT || 8080)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.map': 'application/json',
}

http
  .createServer((req, res) => {
    let urlPath
    try {
      urlPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
    } catch (e) {
      res.writeHead(400)
      return res.end('bad request')
    }
    if (urlPath === '/') urlPath = '/index.html'

    let file = path.normalize(path.join(ROOT, urlPath))
    if (!file.startsWith(ROOT)) {
      res.writeHead(403)
      return res.end('forbidden')
    }

    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      file = path.join(ROOT, 'index.html') // SPA 重写
    }
    const ext = path.extname(file).toLowerCase()
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' })
    fs.createReadStream(file).pipe(res)
  })
  .listen(PORT, () => console.log(`SPA static server on :${PORT} (root ${ROOT})`))
