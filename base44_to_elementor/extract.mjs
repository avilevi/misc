import { readFileSync } from 'fs';

const data = JSON.parse(readFileSync('./page-data.json', 'utf8'));

const texts = [];
const images = [];
const buttons = [];
const sections = [];

function walk(node, depth = 0, sectionIdx = -1) {
  if (!node) return;

  const isSection = node.tag === 'section' ||
    (node.tag === 'div' && node.rect.height > 200 && node.rect.width > 900 && depth <= 4);

  if (node.tag === 'section') {
    sectionIdx = sections.length;
    sections.push({
      idx: sectionIdx,
      bg: node.styles.backgroundColor,
      height: node.rect.height,
      top: node.rect.top,
      classes: node.classes.join(' '),
    });
  }

  if (node.tag === 'img' && node.src && !node.src.includes('base44')) {
    images.push({ src: node.src, alt: node.alt, section: sectionIdx, top: node.rect.top, width: node.rect.width, height: node.rect.height });
  }

  if ((node.tag === 'button' || (node.tag === 'a' && node.href)) && node.text) {
    buttons.push({ text: node.text, href: node.href, section: sectionIdx, top: node.rect.top, bg: node.styles.backgroundColor, color: node.styles.color });
  }

  if (node.text && node.text.trim().length > 1) {
    const fs = parseFloat(node.styles.fontSize);
    texts.push({
      tag: node.tag,
      text: node.text.trim(),
      section: sectionIdx,
      top: node.rect.top,
      fontSize: node.styles.fontSize,
      fontWeight: node.styles.fontWeight,
      color: node.styles.color,
      textAlign: node.styles.textAlign,
      isHeading: fs >= 24 || node.tag.match(/^h[1-6]$/),
    });
  }

  if (node.children) {
    for (const child of node.children) {
      walk(child, depth + 1, sectionIdx);
    }
  }
}

walk(data);

console.log('=== SECTIONS ===');
sections.forEach((s, i) => console.log(`[${i}] top:${s.top} h:${s.height} bg:${s.bg}`));

console.log('\n=== IMAGES ===');
images.forEach(img => console.log(`[s${img.section}] ${img.width}x${img.height} ${img.src} alt="${img.alt}"`));

console.log('\n=== BUTTONS/LINKS ===');
buttons.forEach(b => console.log(`[s${b.section}] "${b.text}" href=${b.href} bg=${b.bg}`));

console.log('\n=== TEXT CONTENT ===');
texts.forEach(t => console.log(`[s${t.section}] ${t.tag} ${t.fontSize} w${t.fontWeight} "${t.text}"`));
