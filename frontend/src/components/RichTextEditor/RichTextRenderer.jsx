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
 *   {color}text{/color} → <span style={{color: ...}}> (red/blue/green/orange/purple only)
 *   [label](url)        → <a> (only https:// or http:// URLs)
 *   ![alt](/api/files/) → <img> (ONLY /api/files/ prefix — security restriction)
 *   \n                  → <br />
 */

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

  // Bold
  segs = applyRule(segs, /\*\*(.+?)\*\*/, m => (
    <strong key={key()}>{m[1]}</strong>
  ));

  // Italic (must come after bold so ** is consumed first)
  segs = applyRule(segs, /\*(.+?)\*/, m => (
    <em key={key()}>{m[1]}</em>
  ));

  // Strikethrough
  segs = applyRule(segs, /~~(.+?)~~/, m => (
    <s key={key()}>{m[1]}</s>
  ));

  // Colors — whitelist only
  segs = applyRule(segs, /\{(red|blue|green|orange|purple)\}([\s\S]+?)\{\/\1\}/, m => {
    const color = ALLOWED_COLORS[m[1]];
    return <span key={key()} style={{ color }}>{m[2]}</span>;
  });

  return segs;
}

function parseSegments(text) {
  const lines = text.split('\n');
  const output = [];

  for (let i = 0; i < lines.length; i++) {
    if (i > 0) output.push(<br key={key()} />);

    const line = lines[i];
    const hMatch = line.match(/^(#{1,3}) (.+)/);
    if (hMatch) {
      const level = hMatch[1].length; // 1, 2, or 3
      // Map # → h2, ## → h3, ### → h4 (h1 reserved for page title)
      const Tag = `h${level + 1}`;
      const inlineContent = applyInlineRules([hMatch[2]]);
      output.push(<Tag key={key()} className="rt-heading">{inlineContent}</Tag>);
    } else {
      const inlineContent = applyInlineRules([line]);
      output.push(...inlineContent);
    }
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
