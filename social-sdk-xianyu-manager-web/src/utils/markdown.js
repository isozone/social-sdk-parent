// 轻量 Markdown → HTML 渲染(支持帖子/公告常见的:图片、标题、粗体、斜体、行内代码、代码块、列表、链接、引用、段落)
// 仅用于社区富文本展示;输出需再经 safeHtml 过滤防 XSS。
function escapeHtml(text) {
  return String(text == null ? '' : text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function inlineMd(text) {
  let s = String(text == null ? '' : text);
  // 行内代码
  s = s.replace(/`([^`]+)`/g, (_m, c) => `<code>${escapeHtml(c)}</code>`);
  // 图片 ![alt](url)
  s = s.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_m, alt, url) => `<img src="${escapeHtml(url)}" alt="${escapeHtml(alt || '')}" style="max-width:100%;border-radius:6px;margin:6px 0;" />`);
  // 链接 [text](url)
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_m, t, url) => `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(t)}</a>`);
  // 粗体
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  // 斜体
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>');
  return s;
}

// 将 markdown 文本转为 HTML(逐行处理)
export function mdToHtml(md) {
  if (!md) return '';
  const raw = String(md);
  // 已是 HTML(含标签)则不再转换,直接返回
  if (/<(h[1-6]|p|div|img|pre|code|ul|ol|li|blockquote|table)[\s>]/i.test(raw) && !/!\[.*\]\(/.test(raw)) {
    return raw;
  }
  const lines = raw.split(/\r?\n/);
  const out = [];
  let inCode = false;
  let codeBuf = [];
  let inList = false;
  let listType = '';
  let inQuote = false;

  const flushList = () => { if (inList) { out.push(`</${listType}>`); inList = false; listType = ''; } };
  const flushQuote = () => { if (inQuote) { out.push('</blockquote>'); inQuote = false; } };

  for (const line of lines) {
    // 代码块 ``` 或 ~~~ 
    if (/^\s*(```|~~~)/.test(line)) {
      if (inCode) { out.push(`<pre><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`); codeBuf = []; inCode = false; }
      else { flushList(); flushQuote(); inCode = true; }
      continue;
    }
    if (inCode) { codeBuf.push(line); continue; }

    flushList();
    flushQuote();

    const t = line.trim();
    if (!t) { out.push('<p><br/></p>'); continue; }
    // 标题
    const h = t.match(/^(#{1,6})\s+(.*)$/);
    if (h) { const lvl = h[1].length; out.push(`<h${lvl}>${inlineMd(h[2])}</h${lvl}>`); continue; }
    // 引用
    if (/^>\s?/.test(t)) { if (!inQuote) { out.push('<blockquote>'); inQuote = true; } out.push(`<p>${inlineMd(t.replace(/^>\s?/, ''))}</p>`); continue; }
    // 无序列表
    const ul = t.match(/^[-*+]\s+(.*)$/);
    if (ul) { if (!inList) { out.push('<ul>'); inList = true; listType = 'ul'; } out.push(`<li>${inlineMd(ul[1])}</li>`); continue; }
    // 有序列表
    const ol = t.match(/^\d+[.)]\s+(.*)$/);
    if (ol) { if (!inList) { out.push('<ol>'); inList = true; listType = 'ol'; } out.push(`<li>${inlineMd(ol[1])}</li>`); continue; }
    // 分隔线
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(t)) { out.push('<hr/>'); continue; }
    // 普通段落
    out.push(`<p>${inlineMd(t)}</p>`);
  }
  flushList();
  flushQuote();
  if (inCode) { out.push(`<pre><code>${escapeHtml(codeBuf.join('\n'))}</code></pre>`); }
  return out.join('\n');
}

// 提取纯文本摘要
export function mdPlainText(md, max = 120) {
  const html = mdToHtml(md || '');
  const text = String(html).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  return text.length > max ? text.slice(0, max) + '…' : text;
}
