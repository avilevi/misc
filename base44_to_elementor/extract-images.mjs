import { readFileSync } from 'fs';

const data = JSON.parse(readFileSync('./page-data.json', 'utf8'));

function walk(node, depth = 0) {
  if (!node) return;

  if (node.tag === 'img') {
    console.log(`IMG src="${node.src}" alt="${node.alt}" ${node.rect.width}x${node.rect.height} top=${node.rect.top}`);
  }

  // Check for background images
  if (node.styles?.backgroundImage && node.styles.backgroundImage !== 'none') {
    const urls = node.styles.backgroundImage.match(/url\("?([^"')]+)"?\)/g);
    if (urls) console.log(`BG [${node.tag}.${node.classes?.join('.')}] ${urls.join(', ')} top=${node.rect?.top}`);
  }

  if (node.children) {
    for (const child of node.children) walk(child, depth + 1);
  }
}

walk(data);
