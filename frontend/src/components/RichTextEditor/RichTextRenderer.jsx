/**
 * RichTextRenderer
 *
 * Parses custom markdown-like syntax and renders to React elements.
 * No dangerouslySetInnerHTML — all output is safe React nodes.
 *
 * Supported syntax:
 *   **text**            → <strong>
 *   *text*              → <em>
 *   ~~text~~            → <s>
 *   __text__            → <u>
 *   {color}text{/color} → <span style={{color: ...}}> (red/blue/green/orange/purple only)
 *   [label](url)        → <a> (only https:// or http:// URLs)
 *   ![alt](/api/files/) → <img> (ONLY /api/files/ prefix — security restriction)
 *   | col | col |       → <table>
 *   # Heading           → <h2/h3/h4>
 *   [gallery]...[/gallery]           → flex image row
 *   [float-left]...[/float-left]     → image left, text right
 *   [float-right]...[/float-right]   → image right, text left
 *   \n                  → <br />
 */

import { Link } from 'react-router-dom';
import './RichTextRenderer.css';

const ALLOWED_COLORS = {
  red:    '#e74c3c',
  blue:   '#3498db',
  green:  '#27ae60',
  orange: '#e67e22',
  purple: '#9b59b6',
};

/**
 * Split an array of string segments by a regex, applying renderFn to each match.
 * Non-matched parts remain as strings.
 */
function applyRule(segments, regex, renderFn) {
  const result = [];
  for (const seg of segments) {
    if (typeof seg !== 'string') {
      result.push(seg);
      continue;
    }
    let last = 0;
    let match;
    regex.lastIndex = 0;
    const re = new RegExp(regex.source, regex.flags.replace('g', '') + 'g');
    while ((match = re.exec(seg)) !== null) {
      if (match.index > last) result.push(seg.slice(last, match.index));
      result.push(renderFn(match));
      last = match.index + match[0].length;
    }
    if (last < seg.length) result.push(seg.slice(last));
  }
  return result;
}

let _keyCounter = 0;
function key() { return `rt-${++_keyCounter}`; }

/** Apply all inline formatting rules to an array of string/React segments. */
function applyInlineRules(segs) {
  // Images — only URLs that contain /api/files/ (our own storage)
  segs = applyRule(segs, /!\[([^\]]*)\]\((https?:\/\/[^)]*\/api\/files\/[^)]+)\)/, m => (
    <img key={key()} src={m[2]} alt={m[1] || 'image'} className="rt-img" />
  ));

  // Links — only http(s) URLs (must come AFTER image rule)
  segs = applyRule(segs, /!?\[([^\]]+)\]\((https?:\/\/[^)]+)\)/, m => (
    <a key={key()} href={m[2]} target="_blank" rel="noopener noreferrer" className="rt-link">
      {m[1]}
    </a>
  ));

  // Internal links — relative paths starting with / (SPA navigation, no full reload)
  segs = applyRule(segs, /\[([^\]]+)\]\((\/[^)]+)\)/, m => (
    <Link key={key()} to={m[2]} className="rt-link">
      {m[1]}
    </Link>
  ));

  // Bold (before italic so ** consumed first)
  segs = applyRule(segs, /\*\*(.+?)\*\*/, m => (
    <strong key={key()}>{m[1]}</strong>
  ));

  // Italic
  segs = applyRule(segs, /\*(.+?)\*/, m => (
    <em key={key()}>{m[1]}</em>
  ));

  // Strikethrough
  segs = applyRule(segs, /~~(.+?)~~/, m => (
    <s key={key()}>{m[1]}</s>
  ));

  // Underline
  segs = applyRule(segs, /__(.+?)__/, m => (
    <u key={key()}>{m[1]}</u>
  ));

  // Colors — whitelist only
  segs = applyRule(segs, /\{(red|blue|green|orange|purple)\}([\s\S]+?)\{\/\1\}/, m => {
    const color = ALLOWED_COLORS[m[1]];
    return <span key={key()} style={{ color }}>{m[2]}</span>;
  });

  return segs;
}

/** Render a block of pipe-separated lines as a <table> */
function renderTable(lines) {
  const isSep = (row) => row.every(c => /^[-: ]+$/.test(c));
  const parsed = lines.map(l =>
    l.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim())
  );
  const rows = parsed.filter(r => !isSep(r));
  const [headerRow, ...bodyRows] = rows;
  if (!headerRow) return null;

  return (
    <table key={key()} className="rt-table">
      <thead>
        <tr>
          {headerRow.map((cell, ci) => (
            <th key={ci}>{applyInlineRules([cell])}</th>
          ))}
        </tr>
      </thead>
      {bodyRows.length > 0 && (
        <tbody>
          {bodyRows.map((row, ri) => (
            <tr key={ri}>
              {row.map((cell, ci) => (
                <td key={ci}>{applyInlineRules([cell])}</td>
              ))}
            </tr>
          ))}
        </tbody>
      )}
    </table>
  );
}

/** Extract all safe image references from a string */
function extractImages(text) {
  const re = /!\[([^\]]*)\]\((https?:\/\/[^)]*\/api\/files\/[^)]+)\)/g;
  const imgs = [];
  let m;
  while ((m = re.exec(text)) !== null) {
    imgs.push({ alt: m[1] || 'image', url: m[2] });
  }
  return imgs;
}

function parseSegments(text) {
  const lines = text.split('\n');
  const output = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // ── [gallery] block ──────────────────────────────────────────────────────
    if (line.trim() === '[gallery]') {
      i++;
      const inner = [];
      while (i < lines.length && lines[i].trim() !== '[/gallery]') {
        inner.push(lines[i]);
        i++;
      }
      i++; // skip [/gallery]
      const imgs = extractImages(inner.join('\n'));
      if (imgs.length > 0) {
        output.push(
          <div key={key()} className="rt-gallery">
            {imgs.map((src, idx) => (
              <img key={idx} src={src.url} alt={src.alt} className="rt-gallery-img" />
            ))}
          </div>
        );
      }
      continue;
    }

    // ── [float-left] / [float-right] block ───────────────────────────────────
    const floatMatch = line.trim().match(/^\[(float-left|float-right)\]$/);
    if (floatMatch) {
      const dir = floatMatch[1]; // 'float-left' or 'float-right'
      i++;
      const inner = [];
      const closeTag = `[/${dir}]`;
      while (i < lines.length && lines[i].trim() !== closeTag) {
        inner.push(lines[i]);
        i++;
      }
      i++; // skip closing tag

      const imgs = extractImages(inner.join('\n'));
      const firstImg = imgs[0] || null;
      // Text = all inner lines except the first image line
      const textLines = inner.filter(l => {
        const m = l.match(/!\[([^\]]*)\]\((https?:\/\/[^)]*\/api\/files\/[^)]+)\)/);
        return !m;
      });
      const textContent = parseSegments(textLines.join('\n'));

      output.push(
        <div key={key()} className={dir === 'float-left' ? 'rt-float-left' : 'rt-float-right'}>
          {firstImg && <img src={firstImg.url} alt={firstImg.alt} className="rt-float-img" />}
          <div className="rt-float-text">{textContent}</div>
        </div>
      );
      continue;
    }

    // Table block: collect all consecutive lines starting with |
    if (line.trim().startsWith('|')) {
      if (output.length > 0) output.push(<br key={key()} />);
      const tableLines = [];
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        tableLines.push(lines[i]);
        i++;
      }
      const tbl = renderTable(tableLines);
      if (tbl) output.push(tbl);
      continue;
    }

    if (output.length > 0) output.push(<br key={key()} />);

    // Heading
    const hMatch = line.match(/^(#{1,3}) (.+)/);
    if (hMatch) {
      const level = hMatch[1].length;
      const Tag = `h${level + 1}`; // # → h2, ## → h3, ### → h4
      output.push(<Tag key={key()} className="rt-heading">{applyInlineRules([hMatch[2]])}</Tag>);
    } else {
      output.push(...applyInlineRules([line]));
    }

    i++;
  }

  return output;
}

export default function RichTextRenderer({ text, className }) {
  if (!text) return null;
  const segments = parseSegments(text);
  return (
    <div className={`rt-content${className ? ' ' + className : ''}`}>
      {segments}
    </div>
  );
}
