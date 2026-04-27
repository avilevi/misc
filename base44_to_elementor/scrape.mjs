import { chromium } from 'playwright';
import { writeFileSync } from 'fs';

const browser = await chromium.launch();
const page = await browser.newPage();

await page.goto('https://novalife-egg-quality.base44.app', { waitUntil: 'networkidle', timeout: 30000 });

// Scroll through the page to trigger lazy loading
const pageHeight = await page.evaluate(() => document.body.scrollHeight);
const step = 600;
for (let y = 0; y < pageHeight; y += step) {
  await page.evaluate((y) => window.scrollTo(0, y), y);
  await page.waitForTimeout(400);
}
await page.evaluate(() => window.scrollTo(0, 0));
await page.waitForTimeout(2000);

// Capture full rendered HTML
const html = await page.content();
writeFileSync('/home/avil/git/misc/base44_to_elementor/rendered.html', html);

// Capture a screenshot for visual reference
await page.screenshot({ path: '/home/avil/git/misc/base44_to_elementor/screenshot.png', fullPage: true });

// Extract structured page data
const pageData = await page.evaluate(() => {
  function getStyles(el) {
    const cs = window.getComputedStyle(el);
    return {
      color: cs.color,
      backgroundColor: cs.backgroundColor,
      fontSize: cs.fontSize,
      fontWeight: cs.fontWeight,
      fontFamily: cs.fontFamily,
      textAlign: cs.textAlign,
      lineHeight: cs.lineHeight,
      letterSpacing: cs.letterSpacing,
      padding: cs.padding,
      paddingTop: cs.paddingTop,
      paddingBottom: cs.paddingBottom,
      paddingLeft: cs.paddingLeft,
      paddingRight: cs.paddingRight,
      margin: cs.margin,
      marginTop: cs.marginTop,
      marginBottom: cs.marginBottom,
      display: cs.display,
      flexDirection: cs.flexDirection,
      alignItems: cs.alignItems,
      justifyContent: cs.justifyContent,
      gap: cs.gap,
      width: cs.width,
      maxWidth: cs.maxWidth,
      backgroundImage: cs.backgroundImage,
      borderRadius: cs.borderRadius,
      border: cs.border,
      position: cs.position,
    };
  }

  function extractElement(el, depth = 0) {
    if (depth > 8) return null;
    const tag = el.tagName.toLowerCase();
    const skipTags = ['script', 'style', 'noscript', 'svg', 'path', 'meta', 'link'];
    if (skipTags.includes(tag)) return null;

    const rect = el.getBoundingClientRect();
    const styles = getStyles(el);

    const children = [];
    for (const child of el.children) {
      const extracted = extractElement(child, depth + 1);
      if (extracted) children.push(extracted);
    }

    return {
      tag,
      id: el.id || null,
      classes: [...el.classList],
      text: el.children.length === 0 ? el.textContent.trim() : null,
      href: el.href || null,
      src: el.src || el.getAttribute('src') || null,
      alt: el.alt || null,
      placeholder: el.placeholder || null,
      type: el.type || null,
      rect: { top: Math.round(rect.top + window.scrollY), left: Math.round(rect.left), width: Math.round(rect.width), height: Math.round(rect.height) },
      styles,
      children,
    };
  }

  const body = document.body;
  // Remove the Base44 badge from extraction
  const badge = document.getElementById('base44-edit-badge');
  if (badge) badge.remove();
  const modal = document.getElementById('base44-modal-overlay');
  if (modal) modal.remove();

  return extractElement(body);
});

writeFileSync('/home/avil/git/misc/base44_to_elementor/page-data.json', JSON.stringify(pageData, null, 2));

console.log('Done. Files saved: rendered.html, screenshot.png, page-data.json');
await browser.close();
