import { useState, useEffect, useCallback, type FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getEntryById, getEntryByDiaryDate, putEntry, deleteEntry as deleteCachedEntry, buildEntryFromDecrypted, type CachedEntry } from '../db/entries';
import { putAttachmentIv } from '../db/attachments';
import { createEntry, updateEntry, deleteEntry } from '../api/entries';
import { uploadAttachment, deleteAttachment } from '../api/attachments';
import { encryptPayload } from '../crypto/cryptoService';
import { arrayBufferToBase64 } from '../crypto/utils';
import { compressImage } from '../utils/imageCompress';
import { toBeijingISOString } from '../utils/timeUtils';
import { renderMarkdownToHtml } from '../utils/markdown';
import MarkdownEditor from '../components/diary/MarkdownEditor';
import AttachmentImage from '../components/diary/AttachmentImage';
import MoodPicker from '../components/diary/MoodPicker';
import WeatherPicker from '../components/diary/WeatherPicker';
import { ArrowLeft, Star, Save, Trash2, Edit3, Eye } from 'lucide-react';

const PLACEHOLDER_DIARY_ID = '00000000-0000-0000-0000-000000000000';

export default function EditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { dek } = useAuth();
  const isEdit = id && id !== 'new';

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState('');
  const [mood, setMood] = useState<string | null>(null);
  const [weather, setWeather] = useState<string | null>(null);
  const [favorite, setFavorite] = useState(false);
  const [diaryDate, setDiaryDate] = useState(toBeijingISOString().slice(0, 10));
  const [version, setVersion] = useState(0);
  const [tempImages, setTempImages] = useState<Map<string, File>>(new Map());
  const [existingAttachmentIds, setExistingAttachmentIds] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(isEdit);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [isViewMode, setIsViewMode] = useState(isEdit);
  const [editTargetId, setEditTargetId] = useState<string | null>(null);

  useEffect(() => {
    if (isEdit || !dek) return;
    (async () => {
      const existing = await getEntryByDiaryDate(diaryDate);
      if (existing) {
        setTitle(existing.title);
        setContent(existing.content);
        setTags(existing.tags.join(', '));
        setMood(existing.mood);
        setWeather(existing.weather);
        setFavorite(existing.favorite);
        setVersion(existing.serverVersion);
        setExistingAttachmentIds(existing.attachmentIds);
        setEditTargetId(existing.diaryId);
      }
    })();
  }, [isEdit, dek]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!isEdit || !dek) return;
    (async () => {
      try {
        const cached = await getEntryById(id!);
        if (cached) {
          setTitle(cached.title);
          setContent(cached.content);
          setTags(cached.tags.join(', '));
          setMood(cached.mood);
          setWeather(cached.weather);
          setFavorite(cached.favorite);
          setDiaryDate(cached.diaryDate);
          setVersion(cached.serverVersion);
          setExistingAttachmentIds(cached.attachmentIds);
        }
      } catch {
        setError('加载日记失败');
      } finally {
        setInitialLoading(false);
      }
    })();
  }, [isEdit, id, dek]);

  const handleDateChange = useCallback(async (newDate: string) => {
    setDiaryDate(newDate);
    if (!isEdit) {
      const existing = await getEntryByDiaryDate(newDate);
      if (existing) {
        setTitle(existing.title);
        setContent(existing.content);
        setTags(existing.tags.join(', '));
        setMood(existing.mood);
        setWeather(existing.weather);
        setFavorite(existing.favorite);
        setVersion(existing.serverVersion);
        setExistingAttachmentIds(existing.attachmentIds);
        setEditTargetId(existing.diaryId);
      } else {
        setTitle('');
        setContent('');
        setTags('');
        setMood(null);
        setWeather(null);
        setFavorite(false);
        setVersion(0);
        setExistingAttachmentIds([]);
        setTempImages(new Map());
        setEditTargetId(null);
      }
    }
  }, [isEdit]);

  const handleSave = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    if (!dek) {
      setError('加密密钥未就绪，请重新登录');
      return;
    }

    if (!title.trim() && !content.trim()) {
      setError('请输入标题或内容');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // 1. Upload temp images
      const tempIdToRealId = new Map<string, string>();
      const newAttachmentIds: string[] = [];

      for (const [tempId, file] of tempImages.entries()) {
        const compressed = await compressImage(file);
        const fileBuffer = await compressed.arrayBuffer();
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const encrypted = await crypto.subtle.encrypt(
          { name: 'AES-GCM', iv },
          dek,
          fileBuffer,
        );
        const ciphertext = new Uint8Array(encrypted);
        const sha256Hash = await crypto.subtle.digest('SHA-256', ciphertext);
        const sha256Hex = Array.from(new Uint8Array(sha256Hash))
          .map((b) => b.toString(16).padStart(2, '0'))
          .join('');
        const ivB64 = arrayBufferToBase64(iv.buffer);

        const blob = new Blob([ciphertext], { type: compressed.type || 'application/octet-stream' });
        const uploadResult = await uploadAttachment(PLACEHOLDER_DIARY_ID, blob, ivB64, sha256Hex);
        const realId = uploadResult.data.id;
        tempIdToRealId.set(tempId, realId);
        newAttachmentIds.push(realId);
        await putAttachmentIv(realId, ivB64);
      }

      // 2. Replace temp refs with real refs
      let markdown = content;
      for (const [tempId, realId] of tempIdToRealId.entries()) {
        markdown = markdown.replace(
          new RegExp(`!\\[([^\\]]*)\\]\\(local:${tempId}\\)`, 'g'),
          `![$1](attachment:${realId})`,
        );
      }

      // 3. Detect removed attachments and delete from server
      const referencedIds = new Set<string>();
      const attachmentRegex = /!\[[^\]]*\]\(attachment:([^)]+)\)/g;
      let m: RegExpExecArray | null;
      while ((m = attachmentRegex.exec(markdown)) !== null) {
        referencedIds.add(m[1]);
      }

      const removedIds = existingAttachmentIds.filter((id) => !referencedIds.has(id));
      const deleteResults = await Promise.allSettled(
        removedIds.map((id) => deleteAttachment(id)),
      );
      for (const r of deleteResults) {
        if (r.status === 'rejected') {
          console.warn('Failed to delete orphaned attachment:', r.reason);
        }
      }

      // 4. Build payload
      const tagList = tags
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean);
      const keptExistingIds = existingAttachmentIds.filter((id) => referencedIds.has(id));
      const allAttachmentIds = [
        ...keptExistingIds,
        ...newAttachmentIds,
      ];
      const payload = {
        title: title.trim(),
        content: markdown,
        tags: tagList,
        attachmentIds: allAttachmentIds,
      };

      // 5. Encrypt
      const { encryptedPayload, iv: payloadIv } = await encryptPayload(payload, dek);

      // 6. Save
      let entryId: string;
      let newVersion: number;
      let serverUpdatedAt: string;

      const effectiveId = editTargetId || id;
      const shouldUpdate = isEdit || editTargetId !== null;

      if (shouldUpdate && effectiveId) {
        const resp = await updateEntry(effectiveId, {
          diaryDate,
          mood: mood || undefined,
          weather: weather || undefined,
          favorite,
          encryptedPayload,
          iv: payloadIv,
          version,
          attachmentIds: allAttachmentIds.length > 0 ? allAttachmentIds : undefined,
        });
        entryId = resp.data.id;
        newVersion = resp.data.version;
        serverUpdatedAt = resp.data.updatedAt;
      } else {
        const resp = await createEntry({
          diaryDate,
          mood: mood || undefined,
          weather: weather || undefined,
          favorite,
          encryptedPayload,
          iv: payloadIv,
          attachmentIds: allAttachmentIds.length > 0 ? allAttachmentIds : undefined,
        });
        entryId = resp.data.id;
        newVersion = resp.data.version;
        serverUpdatedAt = resp.data.updatedAt;
      }

      // 7. Update cache
      const cachedEntry: CachedEntry = buildEntryFromDecrypted(
        entryId,
        diaryDate,
        mood,
        weather,
        favorite,
        newVersion,
        serverUpdatedAt,
        { title: title.trim(), content: markdown, tags: tagList, attachmentIds: allAttachmentIds },
      );
      await putEntry(cachedEntry);

      navigate('/', { replace: true });
    } catch (err: unknown) {
      const e = err as { code?: number; message?: string };
      if (e.code === 409) {
        setError('内容已被其他设备修改，请刷新后重试');
      } else {
        setError(e.message || '保存失败，请重试');
      }
    } finally {
      setLoading(false);
    }
  }, [dek, title, content, tags, mood, weather, favorite, diaryDate, version,
      tempImages, existingAttachmentIds, isEdit, id, navigate]);

  const handleDelete = useCallback(async () => {
    const effectiveId = editTargetId || id;
    if (!effectiveId) return;
    setDeleting(true);
    setError('');
    try {
      await deleteEntry(effectiveId);
      await deleteCachedEntry(effectiveId);
      navigate('/', { replace: true });
    } catch (err: unknown) {
      setError((err as Error).message || '删除失败');
    } finally {
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  }, [editTargetId, id, navigate]);

  const hasExistingEntry = isEdit || editTargetId !== null;

  if (initialLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50">
        <p className="text-gray-400">加载中...</p>
      </div>
    );
  }

  type ContentSegment =
    | { type: 'html'; html: string }
    | { type: 'image'; id: string; alt: string };

  function renderMarkdownHtml(md: string): string {
    return renderMarkdownToHtml(md);
  }

  function parseContent(md: string): ContentSegment[] {
    const segments: ContentSegment[] = [];
    const regex = /!\[([^\]]*)\]\(attachment:([^)]+)\)/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = regex.exec(md)) !== null) {
      if (match.index > lastIndex) {
        segments.push({ type: 'html', html: renderMarkdownHtml(md.slice(lastIndex, match.index)) });
      }
      segments.push({ type: 'image', id: match[2], alt: match[1] });
      lastIndex = match.index + match[0].length;
    }

    if (lastIndex < md.length) {
      segments.push({ type: 'html', html: renderMarkdownHtml(md.slice(lastIndex)) });
    }

    if (segments.length === 0) {
      segments.push({ type: 'html', html: renderMarkdownHtml(md) });
    }

    return segments;
  }

  const contentSegments = parseContent(content);

  return (
    <div className="min-h-screen bg-warm-50">
      <div className="max-w-2xl mx-auto px-4 py-6">
        {/* Header */}
        <div className="flex items-center gap-4 mb-6">
          <button onClick={() => navigate(-1)} className="p-1.5 hover:bg-warm-100 rounded-lg transition-colors">
            <ArrowLeft className="w-5 h-5 text-gray-500" />
          </button>
          <h1 className="text-lg font-bold text-gray-800 flex-1">
            {isViewMode ? '日记' : (isEdit || editTargetId) ? '编辑日记' : '写日记'}
          </h1>
          {isViewMode && (
            <span className="text-xs text-gray-300 bg-gray-100 px-1.5 py-0.5 rounded flex items-center gap-1">
              <Eye className="w-3 h-3" /> 浏览
            </span>
          )}
          <button
            onClick={() => isViewMode ? null : setFavorite(!favorite)}
            className={`p-1.5 rounded-lg transition-colors ${
              isViewMode ? '' : favorite ? 'text-yellow-500' : 'text-gray-300 hover:text-yellow-400'
            }`}
            disabled={isViewMode}
          >
            <Star className={`w-5 h-5 ${favorite ? 'fill-yellow-400' : ''}`} />
          </button>
          {isViewMode && (
            <button
              type="button"
              onClick={() => setIsViewMode(false)}
              className="p-1.5 text-gray-400 hover:text-warm-600 rounded-lg transition-colors"
              title="编辑"
            >
              <Edit3 className="w-5 h-5" />
            </button>
          )}
          {hasExistingEntry && (
            <button
              type="button"
              onClick={() => setShowDeleteConfirm(true)}
              className="p-1.5 text-gray-300 hover:text-red-500 rounded-lg transition-colors"
              title="删除日记"
            >
              <Trash2 className="w-5 h-5" />
            </button>
          )}
        </div>

        {isViewMode ? (
          /* ===== VIEW MODE ===== */
          <div className="space-y-5">
            {/* Metadata chips */}
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm text-gray-500">{diaryDate}</span>
              {mood && (
                <span className="text-xs bg-warm-100 text-warm-700 px-2 py-0.5 rounded-full">
                  {mood}
                </span>
              )}
              {weather && (
                <span className="text-xs bg-blue-50 text-blue-600 px-2 py-0.5 rounded-full">
                  {weather}
                </span>
              )}
              {favorite && (
                <Star className="w-4 h-4 fill-yellow-400 text-yellow-400" />
              )}
            </div>

            {/* Title */}
            <h2 className="text-xl font-bold text-gray-800">
              {title || diaryDate}
            </h2>

            {/* Content */}
            <div className="prose prose-sm max-w-none text-gray-700 leading-relaxed">
              {contentSegments.map((seg, i) => {
                if (seg.type === 'html') {
                  return <span key={i} dangerouslySetInnerHTML={{ __html: seg.html }} />;
                }
                return <AttachmentImage key={i} attachmentId={seg.id} alt={seg.alt} />;
              })}
            </div>

            {/* Tags */}
            {tags && (
              <div className="flex flex-wrap gap-1.5">
                {tags.split(/[,，]/).filter(Boolean).map((t) => (
                  <span key={t} className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full">
                    #{t.trim()}
                  </span>
                ))}
              </div>
            )}

            {/* Action buttons */}
            <div className="flex gap-3">
              <button
                onClick={() => setIsViewMode(false)}
                className="btn-primary flex-1 flex items-center justify-center gap-2"
              >
                <Edit3 className="w-4 h-4" />
                编辑
              </button>
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(true)}
                className="px-4 py-2.5 text-sm text-red-500 border border-red-200 hover:bg-red-50 rounded-lg transition-colors flex items-center gap-2"
              >
                <Trash2 className="w-4 h-4" />
                删除
              </button>
            </div>
          </div>
        ) : (
          /* ===== EDIT MODE ===== */
          <form onSubmit={handleSave} className="space-y-5">
            {/* Metadata row */}
            <div className="flex flex-wrap gap-3">
              <input
                type="date"
                value={diaryDate}
                onChange={(e) => handleDateChange(e.target.value)}
                className="input-field w-auto"
                disabled={loading}
              />
            </div>

            {/* Title */}
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="日记标题（可选）"
              className="input-field text-lg font-medium"
              disabled={loading}
            />

            {/* Markdown editor */}
            <MarkdownEditor
              value={content}
              onChange={setContent}
              tempImages={tempImages}
              onImagesChange={setTempImages}
              disabled={loading}
            />

            {/* Tags */}
            <input
              type="text"
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="标签，用逗号分隔（如：生活, 反思, 旅行）"
              className="input-field"
              disabled={loading}
            />

            {/* Mood & Weather */}
            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-500 mb-1.5">心情</p>
                <MoodPicker value={mood} onChange={setMood} />
              </div>
              <div>
                <p className="text-sm text-gray-500 mb-1.5">天气</p>
                <WeatherPicker value={weather} onChange={setWeather} />
              </div>
            </div>

            {error && <p className="text-red-500 text-sm text-center" role="alert">{error}</p>}

            {/* Save button */}
            <button type="submit" className="btn-primary w-full flex items-center justify-center gap-2" disabled={loading}>
              <Save className="w-4 h-4" />
              {loading ? '保存中...' : '保存日记'}
            </button>
          </form>
        )}

        {/* Delete confirmation modal */}
        {showDeleteConfirm && (
          <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
              <h3 className="text-lg font-bold text-gray-800 mb-2">确认删除</h3>
              <p className="text-sm text-gray-500 mb-4">
                此操作不可逆，日记及关联附件将被永久删除。确定继续？
              </p>
              <div className="flex gap-3 justify-end">
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                  disabled={deleting}
                >
                  取消
                </button>
                <button
                  onClick={handleDelete}
                  className="px-4 py-2 text-sm bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors"
                  disabled={deleting}
                >
                  {deleting ? '删除中...' : '确认删除'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
