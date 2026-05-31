import { useState, useRef, useCallback, useEffect, type DragEvent, type ClipboardEvent } from 'react';
import { Eye, Edit3 } from 'lucide-react';
import { renderMarkdownToHtml } from '../../utils/markdown';

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  tempImages: Map<string, File>;
  onImagesChange: (images: Map<string, File>) => void;
  disabled?: boolean;
}

function TempImageThumb({ file, alt, onRemove }: { file: File; alt: string; onRemove: () => void }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    const objectUrl = URL.createObjectURL(file);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  return (
    <div className="relative group">
      <img
        src={url || ''}
        alt={alt}
        className="h-16 w-16 object-cover rounded-lg border border-gray-200"
      />
      <button
        type="button"
        onClick={onRemove}
        className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-red-500 text-white rounded-full
                   text-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
      >
        ×
      </button>
    </div>
  );
}

function generateTempId(): string {
  return `temp-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

function renderMarkdown(md: string): string {
  let html = renderMarkdownToHtml(md);

  html = html.replace(/!\[([^\]]*)\]\(attachment:([^)]+)\)/g,
    '<div class="my-2"><img src="attachment:$2" alt="$1" class="max-w-full rounded-lg" /></div>');
  html = html.replace(/!\[([^\]]*)\]\(local:([^)]+)\)/g,
    '<div class="my-2"><span class="text-sm text-gray-400">[图片: $1 (保存后上传)]</span></div>');

  return html;
}

export default function MarkdownEditor({
  value,
  onChange,
  tempImages,
  onImagesChange,
  disabled = false,
}: MarkdownEditorProps) {
  const [preview, setPreview] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const insertAtCursor = useCallback((text: string) => {
    const ta = textareaRef.current;
    if (!ta) return;
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    const newValue = value.substring(0, start) + text + value.substring(end);
    onChange(newValue);
    setTimeout(() => {
      ta.focus();
      ta.setSelectionRange(start + text.length, start + text.length);
    }, 0);
  }, [value, onChange]);

  const handlePaste = useCallback((e: ClipboardEvent) => {
    const items = e.clipboardData?.items;
    if (!items) return;

    for (const item of Array.from(items)) {
      if (item.type.startsWith('image/')) {
        e.preventDefault();
        const file = item.getAsFile();
        if (!file) continue;

        const tempId = generateTempId();
        const newMap = new Map(tempImages);
        newMap.set(tempId, file);
        onImagesChange(newMap);
        insertAtCursor(`![${file.name || 'image'}](local:${tempId})`);
        return;
      }
    }
  }, [tempImages, onImagesChange, insertAtCursor]);

  const handleDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    const files = e.dataTransfer?.files;
    if (!files) return;

    for (const file of Array.from(files)) {
      if (file.type.startsWith('image/')) {
        const tempId = generateTempId();
        const newMap = new Map(tempImages);
        newMap.set(tempId, file);
        onImagesChange(newMap);
        insertAtCursor(`![${file.name || 'image'}](local:${tempId})`);
      }
    }
  }, [tempImages, onImagesChange, insertAtCursor]);

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
  }, []);

  const handleRemoveImage = (tempId: string) => {
    const newMap = new Map(tempImages);
    newMap.delete(tempId);
    onImagesChange(newMap);
    const newValue = value.replace(new RegExp(`!\\[[^\\]]*\\]\\(local:${tempId}\\)\\s*`, 'g'), '');
    onChange(newValue);
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 border-b border-gray-100 pb-2">
        <button
          type="button"
          onClick={() => setPreview(false)}
          className={`inline-flex items-center gap-1 px-2 py-1 text-sm rounded transition-colors
            ${!preview ? 'bg-warm-100 text-warm-700' : 'text-gray-400 hover:text-gray-600'}`}
        >
          <Edit3 className="w-3.5 h-3.5" />
          编辑
        </button>
        <button
          type="button"
          onClick={() => setPreview(true)}
          className={`inline-flex items-center gap-1 px-2 py-1 text-sm rounded transition-colors
            ${preview ? 'bg-warm-100 text-warm-700' : 'text-gray-400 hover:text-gray-600'}`}
        >
          <Eye className="w-3.5 h-3.5" />
          预览
        </button>
        <span className="text-xs text-gray-300 ml-auto">支持 Markdown · 可拖拽或粘贴图片</span>
      </div>

      {preview ? (
        <div
          className="min-h-[240px] p-3 border border-gray-200 rounded-lg bg-white prose prose-sm max-w-none"
          dangerouslySetInnerHTML={{ __html: renderMarkdown(value) }}
        />
      ) : (
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onPaste={handlePaste}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          disabled={disabled}
          placeholder="写点什么...&#10;&#10;支持 Markdown 语法：&#10;# 标题&#10;**粗体** *斜体*&#10;- 列表&#10;直接粘贴或拖拽图片"
          className="input-field min-h-[240px] resize-y font-mono text-sm"
        />
      )}

      {tempImages.size > 0 && (
        <div className="flex flex-wrap gap-2">
          {Array.from(tempImages.entries()).map(([id, file]) => (
            <TempImageThumb
              key={id}
              file={file}
              alt={file.name}
              onRemove={() => handleRemoveImage(id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
