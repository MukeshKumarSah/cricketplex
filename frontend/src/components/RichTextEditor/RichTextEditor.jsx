import { useRef, useState } from 'react';
import { uploadForumImage } from '../../api/auth';
import './RichTextEditor.css';

const COLORS = [
  { name: 'red',    hex: '#e74c3c' },
  { name: 'blue',   hex: '#3498db' },
  { name: 'green',  hex: '#27ae60' },
  { name: 'orange', hex: '#e67e22' },
  { name: 'purple', hex: '#9b59b6' },
];

/**
 * RichTextEditor
 * Props:
 *  value        string   controlled value
 *  onChange     fn(str)  value setter
 *  placeholder  string
 *  minHeight    string   CSS min-height for textarea (default '90px')
 */
export default function RichTextEditor({ value, onChange, placeholder, minHeight }) {
  const taRef = useRef(null);
  const [showColors, setShowColors] = useState(false);
  const [uploading, setUploading] = useState(false);

  // Wrap selected text with a prefix and suffix
  function wrap(prefix, suffix) {
    const ta = taRef.current;
    if (!ta) return;
    const start = ta.selectionStart;
    const end   = ta.selectionEnd;
    const selected = value.slice(start, end) || 'text';
    const next = value.slice(0, start) + prefix + selected + suffix + value.slice(end);
    onChange(next);
    // Restore selection after React re-render
    requestAnimationFrame(() => {
      ta.focus();
      ta.setSelectionRange(start + prefix.length, start + prefix.length + selected.length);
    });
  }

  function handleBold()          { wrap('**', '**'); }
  function handleItalic()        { wrap('*', '*'); }
  function handleStrike()        { wrap('~~', '~~'); }
  function handleUnderline()     { wrap('__', '__'); }

  function handleInsertTable() {
    const ta = taRef.current;
    const pos = ta ? ta.selectionStart : value.length;
    const template = '\n| Header 1 | Header 2 | Header 3 |\n| --- | --- | --- |\n| Cell | Cell | Cell |\n';
    const next = value.slice(0, pos) + template + value.slice(pos);
    onChange(next);
  }

  function handleLink() {
    const ta = taRef.current;
    const url = window.prompt('Enter URL:', 'https://');
    if (!url || url === 'https://') return;
    const start = ta ? ta.selectionStart : value.length;
    const end   = ta ? ta.selectionEnd   : value.length;
    const selected = value.slice(start, end) || 'link text';
    const next = value.slice(0, start) + `[${selected}](${url})` + value.slice(end);
    onChange(next);
  }

  function handleColor(colorName) {
    setShowColors(false);
    wrap(`{${colorName}}`, `{/${colorName}}`);
  }

  function insertHeading(prefix) {
    const ta = taRef.current;
    if (!ta) return;
    const pos = ta.selectionStart;
    const before = value.slice(0, pos);
    const lineStart = before.lastIndexOf('\n') + 1;
    const lineEndIdx = value.indexOf('\n', pos);
    const lineEnd = lineEndIdx === -1 ? value.length : lineEndIdx;
    const lineContent = value.slice(lineStart, lineEnd);
    const stripped = lineContent.replace(/^#{1,3} /, '');
    const next = value.slice(0, lineStart) + prefix + stripped + value.slice(lineEnd);
    onChange(next);
    requestAnimationFrame(() => {
      ta.focus();
      ta.setSelectionRange(lineStart + prefix.length, lineStart + prefix.length + stripped.length);
    });
  }

  function insertEmoji(emoji) {
    const ta = taRef.current;
    const pos = ta ? ta.selectionStart : value.length;
    const next = value.slice(0, pos) + emoji + value.slice(pos);
    onChange(next);
    requestAnimationFrame(() => {
      ta?.focus();
      ta?.setSelectionRange(pos + emoji.length, pos + emoji.length);
    });
  }

  async function handleImageUpload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';
    setUploading(true);
    try {
      const res = await uploadForumImage(file);
      const url = res.data.url;
      const pos = taRef.current ? taRef.current.selectionStart : value.length;
      const next = value.slice(0, pos) + `![image](${url})` + value.slice(pos);
      onChange(next);
    } catch {
      alert('Image upload failed.');
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="rt-editor">
      <div className="rt-toolbar" onMouseDown={e => e.preventDefault()}>
        <button type="button" title="Bold" onClick={handleBold}><b>B</b></button>
        <button type="button" title="Italic" onClick={handleItalic}><i>I</i></button>
        <button type="button" title="Strikethrough" onClick={handleStrike}><s>S</s></button>
        <button type="button" title="Underline" onClick={handleUnderline}><u>U</u></button>
        <div className="sep" />
        <button type="button" title="Link" onClick={handleLink}>🔗</button>
        <div className="sep" />
        <div className="rt-color-btn" style={{ position: 'relative' }}>
          <button
            type="button"
            title="Color"
            onClick={() => setShowColors(v => !v)}
            style={{ fontSize: '0.85rem' }}
          >A</button>
          {showColors && (
            <div className="rt-color-dropdown">
              {COLORS.map(c => (
                <div
                  key={c.name}
                  className="rt-color-dot"
                  style={{ background: c.hex }}
                  title={c.name}
                  onClick={() => handleColor(c.name)}
                />
              ))}
            </div>
          )}
        </div>
        <div className="sep" />
        <label title={uploading ? 'Uploading...' : 'Upload image'}>
          {uploading ? '…' : '🖼'}
          <input
            type="file"
            accept="image/*"
            className="rt-upload-input"
            onChange={handleImageUpload}
            disabled={uploading}
          />
        </label>
        <button type="button" title="Insert table" className="rt-heading-btn" onClick={handleInsertTable}>⊠</button>
        <div className="sep" />
        <button type="button" title="Heading 1" className="rt-heading-btn" onClick={() => insertHeading('# ')}>H1</button>
        <button type="button" title="Heading 2" className="rt-heading-btn" onClick={() => insertHeading('## ')}>H2</button>
        <button type="button" title="Heading 3" className="rt-heading-btn" onClick={() => insertHeading('### ')}>H3</button>
        <div className="sep" />
        {['👍','👎','💯','🔥','😂','💀','✅','❌','🙏','👀','💪','🎉','🤝','😭','✨','❓','❔'].map(emoji => (
          <button key={emoji} type="button" className="rt-emoji-btn" title={emoji} onClick={() => insertEmoji(emoji)}>{emoji}</button>
        ))}
      </div>
      <textarea
        ref={taRef}
        className="rt-textarea"
        style={minHeight ? { minHeight } : {}}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder || 'Write something…'}
      />
    </div>
  );
}
