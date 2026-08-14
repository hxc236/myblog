// 浏览器集成冒烟（#5 前端与视觉接缝）：
// 常规宽屏桌面 + 390 像素移动端，首页四区、作品行、博客路由、404、
// 直接打开/刷新、横向溢出、控制台错误、键盘焦点可见性。
const puppeteer = require('puppeteer-core')

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://localhost:8080'

const results = []
function check(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`)
}

async function waitForText(page, text, timeout = 30000) {
  await page.waitForFunction(
    (t) => document.body && document.body.innerText.includes(t),
    { timeout },
    text,
  )
}

async function consoleErrors(page) {
  const errors = []
  page.on('console', (msg) => {
    if (msg.type() === 'error' && !msg.text().includes('status of 404')) {
      errors.push(msg.text())
    }
  })
  page.on('pageerror', (err) => errors.push(String(err)))
  return errors
}

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu'],
  })

  try {
    // ---- 桌面端首页 ----
    const page = await browser.newPage()
    await page.setViewport({ width: 1440, height: 900 })
    const errors = await consoleErrors(page)

    await page.goto(BASE + '/', { waitUntil: 'domcontentloaded' })
    await waitForText(page, '查看精选作品')

    check('首页渲染中文价值主张', await page.evaluate(() => document.body.innerText.includes('用 Spring Boot 与 Vue 构建可靠的全栈应用')))
    check('首页恰好四个一级内容区', (await page.evaluate(() => document.querySelectorAll('section[id]').length)) === 4)
    check('页面只有一个 h1', (await page.evaluate(() => document.querySelectorAll('h1').length)) === 1)
    check('三个作品档案行', (await page.evaluate(() => document.querySelectorAll('.project-row').length)) === 3)
    const firstRow = await page.evaluate(() => {
      const row = document.querySelector('.project-row')
      return {
        hasNumber: !!row.querySelector('.row-number'),
        hasTitle: !!row.querySelector('.row-title'),
        hasMeta: !!row.querySelector('.row-meta'),
        hasAction: !!row.querySelector('.action-link'),
      }
    })
    check('作品行含编号/标题/职责/技术栈/外链', firstRow.hasNumber && firstRow.hasTitle && firstRow.hasMeta && firstRow.hasAction)
    check('首页博客区最多三篇', (await page.evaluate(() => document.querySelectorAll('.post-row').length)) <= 3)
    check('技能面板按后端/前端/交付分组', await page.evaluate(() => {
      const names = [...document.querySelectorAll('.group-name')].map((el) => el.textContent)
      return JSON.stringify(names) === JSON.stringify(['后端', '前端', '交付'])
    }))
    check('联系区邮箱与 GitHub 均可点击', await page.evaluate(() => {
      const links = [...document.querySelectorAll('#contact a')]
      return links.some((a) => a.href.startsWith('mailto:')) && links.some((a) => a.href.includes('github.com'))
    }))
    check('导航无旧功能入口', await page.evaluate(() => {
      const nav = document.querySelector('nav').innerText
      return !/登录|注册|聊天|编程|计划/.test(nav)
    }))
    check('首屏 GitHub 可见', await page.evaluate(() => {
      const nav = document.querySelector('nav')
      return !!nav.querySelector('a[aria-label*="GitHub"]')
    }))
    check('无个人资料卡/位置/肖像', await page.evaluate(() => {
      const text = document.body.innerText
      return !/肖像|位置|时区|机会状态|简历/.test(text)
    }))

    // 键盘焦点可见性：Tab 到导航链接时应有可见焦点样式
    await page.focus('body')
    for (let i = 0; i < 3; i++) await page.keyboard.press('Tab')
    const focusVisible = await page.evaluate(() => {
      const el = document.activeElement
      return !!el && el.tagName === 'A'
    })
    check('键盘可操作导航（Tab 聚焦到链接）', focusVisible)

    // ---- 博客列表 / 详情 / 404 ----
    await page.goto(BASE + '/blog', { waitUntil: 'domcontentloaded' })
    await waitForText(page, '全部博客')
    check('博客列表页渲染', (await page.evaluate(() => document.querySelectorAll('.post-row').length)) >= 1)

    await page.goto(BASE + '/blog/mvp-launch-notes', { waitUntil: 'domcontentloaded' })
    await waitForText(page, '为什么不需要数据库')
    check('博客详情渲染 Markdown（标题/引用/代码块）', await page.evaluate(() => {
      const body = document.querySelector('.markdown-body')
      return body.querySelector('h2') && body.querySelector('blockquote') && body.querySelector('pre code')
    }))
    check('Markdown 原始 HTML 被禁用', await page.evaluate(() => {
      const body = document.querySelector('.markdown-body')
      return body.querySelector('script, iframe, style') === null
    }))

    // 直接刷新（SPA 托管重写）后仍渲染
    await page.reload({ waitUntil: 'domcontentloaded' })
    await waitForText(page, '为什么不需要数据库')
    check('博客详情直接刷新后仍渲染（SPA 重写）', true)

    await page.goto(BASE + '/blog/no-such-post', { waitUntil: 'domcontentloaded' })
    await waitForText(page, '文章不存在')
    check('未知博客 slug → 站内“文章不存在”', true)

    await page.goto(BASE + '/some/unknown/route', { waitUntil: 'domcontentloaded' })
    await waitForText(page, '页面不存在')
    check('未知路由 → 站内 404（保留 URL）', await page.evaluate(() => location.pathname === '/some/unknown/route'))

    // 旧入口重定向
    await page.goto(BASE + '/chatroom/', { waitUntil: 'domcontentloaded' })
    await waitForText(page, '查看精选作品')
    check('旧入口 /chatroom/ 重定向到首页', await page.evaluate(() => location.pathname === '/'))

    // ---- 390 像素移动端 ----
    const mobile = await browser.newPage()
    await mobile.setViewport({ width: 390, height: 844 })
    const mobileErrors = await consoleErrors(mobile)
    await mobile.goto(BASE + '/', { waitUntil: 'domcontentloaded' })
    await waitForText(mobile, '查看精选作品')
    const overflow = await mobile.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    check('390px 无横向溢出', overflow <= 1, `scrollWidth-clientWidth=${overflow}px`)
    check('移动端作品职责与技术栈可见', await mobile.evaluate(() => {
      const row = document.querySelector('.project-row')
      return !!row.querySelector('.row-meta')
    }))
    check('移动端导航保留标识/GitHub/联系', await mobile.evaluate(() => {
      const nav = document.querySelector('nav')
      return !!nav.querySelector('.nav-brand') && !!nav.querySelector('a[aria-label*="GitHub"]') && !!nav.querySelector('.nav-contact')
    }))

    // ---- 控制台错误 ----
    await new Promise((r) => setTimeout(r, 1500))
    check('桌面端控制台无错误', errors.length === 0, errors.join(' | ').slice(0, 300))
    check('移动端控制台无错误', mobileErrors.length === 0, mobileErrors.join(' | ').slice(0, 300))

    await page.close()
    await mobile.close()
  } finally {
    await browser.close()
  }

  const failed = results.filter((r) => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} 项通过`)
  process.exit(failed.length === 0 ? 0 : 1)
})().catch((err) => {
  console.error('SMOKE FAILED:', err)
  process.exit(1)
})
