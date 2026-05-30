import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { deleteAccount, setRecovery, deleteRecovery, getRecovery } from '../api/auth';
import { getAllEntries } from '../db/entries';
import { getAttachmentIv, putAttachmentIv } from '../db/attachments';
import { syncEntries, batchGetEntries } from '../api/entries';
import { downloadAttachment } from '../api/attachments';
import { deriveKey, generateRandomBytes, deriveAuthKeyBytes } from '../crypto/cryptoService';
import { arrayBufferToBase64, base64ToArrayBuffer } from '../crypto/utils';
import { toBeijingISOString } from '../utils/timeUtils';
import PasswordStrength from '../components/ui/PasswordStrength';
import { ArrowLeft, Download, Shield, Trash2, Key } from 'lucide-react';

type Tab = 'password' | 'recovery' | 'export' | 'delete';

export default function SettingsPage() {
  const navigate = useNavigate();
  const { user, dek, logout, changePassword } = useAuth();
  const [activeTab, setActiveTab] = useState<Tab>('password');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Change password
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Recovery
  const [recoveryPhrase, setRecoveryPhrase] = useState('');
  const [recoverySet, setRecoverySet] = useState<boolean | null>(null);
  const [recoveryPwdForDelete, setRecoveryPwdForDelete] = useState('');

  // Delete account
  const [deleteConfirm, setDeleteConfirm] = useState('');
  const [deletePassword, setDeletePassword] = useState('');

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: 'password', label: '修改密码', icon: <Key className="w-4 h-4" /> },
    { key: 'recovery', label: '恢复管理', icon: <Shield className="w-4 h-4" /> },
    { key: 'export', label: '数据导出', icon: <Download className="w-4 h-4" /> },
    { key: 'delete', label: '注销账户', icon: <Trash2 className="w-4 h-4" /> },
  ];

  function validatePassword(pw: string): string | null {
    if (pw.length < 8) return '密码至少 8 位';
    if (!/[A-Z]/.test(pw)) return '密码需包含大写字母';
    if (!/[a-z]/.test(pw)) return '密码需包含小写字母';
    if (!/[0-9]/.test(pw)) return '密码需包含数字';
    return null;
  }

  const handleChangePassword = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setMessage('');

    if (!oldPassword) { setError('请输入旧密码'); return; }
    const pwErr = validatePassword(newPassword);
    if (pwErr) { setError(pwErr); return; }
    if (newPassword !== confirmPassword) { setError('两次输入的新密码不一致'); return; }

    setLoading(true);
    try {
      await changePassword(oldPassword, newPassword);
      setMessage('密码已修改，请使用新密码重新登录');
      setTimeout(() => {
        logout().catch(() => {});
        navigate('/login', { replace: true });
      }, 2000);
    } catch (err: unknown) {
      setError((err as Error).message || '密码修改失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCheckRecovery = async () => {
    if (!user) return;
    setError('');
    try {
      const result = await getRecovery(user.username);
      const hasRecovery = !!(result.data?.recovery_data);
      setRecoverySet(hasRecovery);
    } catch (err: unknown) {
      setError((err as Error).message || '查询恢复状态失败');
    }
  };

  const handleSetRecovery = async (e: FormEvent) => {
    e.preventDefault();
    if (!dek || !user) return;
    setError('');
    setMessage('');
    setLoading(true);

    try {
      const recoverySalt = generateRandomBytes(16);
      const kek = await deriveKey(recoveryPhrase, recoverySalt, 600000, 'encrypt');
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const exportedDek = await crypto.subtle.exportKey('raw', dek);
      const encrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        kek,
        exportedDek,
      );
      const recoveryData = `${arrayBufferToBase64(new Uint8Array(encrypted).buffer)}:${arrayBufferToBase64(iv.buffer)}`;
      const recoverySaltB64 = arrayBufferToBase64(recoverySalt.buffer);

      const challengeBytes = generateRandomBytes(32);
      const challengeIv = crypto.getRandomValues(new Uint8Array(12));
      const encryptedChallenge = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: challengeIv },
        kek,
        challengeBytes,
      );
      const challengeB64 = arrayBufferToBase64(challengeBytes.buffer);
      const encryptedChallengeB64 = `${arrayBufferToBase64(new Uint8Array(encryptedChallenge).buffer)}:${arrayBufferToBase64(challengeIv.buffer)}`;

      await setRecovery(recoveryData, recoverySaltB64, challengeB64, encryptedChallengeB64);
      setRecoverySet(true);
      setMessage('恢复口令托管已设置');
    } catch (err: unknown) {
      setError((err as Error).message || '设置恢复口令失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteRecovery = async () => {
    if (!user) return;
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const recoveryResult = await getRecovery(user.username);
      const saltEncB64 = recoveryResult.data?.salt_enc;
      if (!saltEncB64) {
        setError('无法获取加密参数，请重试');
        return;
      }
      const saltEnc = new Uint8Array(base64ToArrayBuffer(saltEncB64));
      const authKeyBytes = await deriveAuthKeyBytes(recoveryPwdForDelete, saltEnc, 600000);
      await deleteRecovery(arrayBufferToBase64(authKeyBytes));
      setRecoverySet(false);
      setMessage('托管信息已删除');
    } catch (err: unknown) {
      setError((err as Error).message || '删除失败，请确认密码正确');
    } finally {
      setLoading(false);
    }
  };

  const handlePlaintextExport = async () => {
    if (!dek) return;
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const entries = await getAllEntries();
      const attachmentRefRegex = /!\[([^\]]*)\]\(attachment:([^)]+)\)/g;
      const exportData = [];

      for (const e of entries) {
        let content = e.content;
        const matches = Array.from(content.matchAll(attachmentRefRegex));
        for (const match of matches) {
          const [fullMatch, alt, attId] = match;
          try {
            const { data, iv: headerIv, contentType } = await downloadAttachment(attId);
            let ivB64 = await getAttachmentIv(attId);
            if (!ivB64 && headerIv) { ivB64 = headerIv; await putAttachmentIv(attId, headerIv); }
            if (ivB64) {
              const iv = new Uint8Array(base64ToArrayBuffer(ivB64));
              const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, dek, data);
              const blob = new Blob([plaintext], { type: contentType });
              const dataUrl = await new Promise<string>((resolve) => {
                const reader = new FileReader();
                reader.onload = () => resolve(reader.result as string);
                reader.readAsDataURL(blob);
              });
              content = content.replace(fullMatch, `![${alt}](${dataUrl})`);
            }
          } catch {
            // keep original reference on failure
          }
        }
        exportData.push({
          date: e.diaryDate,
          title: e.title,
          content,
          tags: e.tags,
          mood: e.mood,
          weather: e.weather,
        });
      }

      const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `diary-export-${toBeijingISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      setMessage('明文导出完成（含图片）');
    } catch (err: unknown) {
      setError((err as Error).message || '导出失败');
    } finally {
      setLoading(false);
    }
  };

  const handleEncryptedExport = async () => {
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const syncResp = await syncEntries();
      const allIds = syncResp.data.entries.map((e: { id: string }) => e.id);

      const batchSize = 50;
      const allDetails: unknown[] = [];
      for (let i = 0; i < allIds.length; i += batchSize) {
        const batch = allIds.slice(i, i + batchSize);
        const batchResp = await batchGetEntries(batch);
        allDetails.push(...batchResp.data.entries);
      }

      const localEntries = await getAllEntries();
      const attachmentIdsByEntry: Map<string, string[]> = new Map();
      for (const e of localEntries) {
        if (e.attachmentIds && e.attachmentIds.length > 0) {
          attachmentIdsByEntry.set(e.diaryId, e.attachmentIds);
        }
      }

      const allAttachments: Array<{ id: string; ivB64: string; contentType: string; dataBase64: string }> = [];
      const seenAttIds = new Set<string>();
      for (const attIds of attachmentIdsByEntry.values()) {
        for (const attId of attIds) {
          if (seenAttIds.has(attId)) continue;
          seenAttIds.add(attId);
          try {
            const { data, iv: headerIv, contentType } = await downloadAttachment(attId);
            let ivB64 = await getAttachmentIv(attId);
            if (!ivB64 && headerIv) { ivB64 = headerIv; await putAttachmentIv(attId, headerIv); }
            if (ivB64) {
              allAttachments.push({
                id: attId,
                ivB64,
                contentType,
                dataBase64: arrayBufferToBase64(data),
              });
            }
          } catch {
            // skip failed downloads
          }
        }
      }

      const exportData = {
        exportedAt: toBeijingISOString(),
        entryCount: allDetails.length,
        attachmentCount: allAttachments.length,
        entries: allDetails,
        attachments: allAttachments,
      };

      const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `diary-encrypted-export-${toBeijingISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      setMessage('密文导出完成（含附件，包含所有加密数据，可用于冷存储恢复）');
    } catch (err: unknown) {
      setError((err as Error).message || '导出失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAccount = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (deleteConfirm !== '确认注销') {
      setError('请输入"确认注销"');
      return;
    }
    if (!deletePassword) {
      setError('请输入密码');
      return;
    }
    setLoading(true);
    try {
      const recoveryResult = await getRecovery(user!.username);
      const saltEncB64 = recoveryResult.data?.salt_enc || '';
      const saltEnc = saltEncB64 ? new Uint8Array(base64ToArrayBuffer(saltEncB64)) : new Uint8Array(16);
      const authKeyBytes = await deriveAuthKeyBytes(deletePassword, saltEnc, 600000);
      await deleteAccount(arrayBufferToBase64(authKeyBytes));
      await logout();
      navigate('/login', { replace: true });
    } catch (err: unknown) {
      setError((err as Error).message || '注销失败，请确认密码正确');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto px-4 py-6">
        <div className="flex items-center gap-4 mb-6">
          <button onClick={() => navigate(-1)} className="p-1.5 hover:bg-gray-100 rounded-lg transition-colors">
            <ArrowLeft className="w-5 h-5 text-gray-500" />
          </button>
          <h1 className="text-xl font-bold text-gray-800">设置</h1>
        </div>

        {/* Tabs */}
        <div className="flex gap-1 mb-6 bg-gray-100 rounded-lg p-1">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => { setActiveTab(tab.key); setError(''); setMessage(''); }}
              className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-md text-sm transition-colors
                ${activeTab === tab.key ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>

        {message && <p className="text-green-600 text-sm text-center mb-4">{message}</p>}
        {error && <p className="text-red-500 text-sm text-center mb-4" role="alert">{error}</p>}

        {/* Change Password */}
        {activeTab === 'password' && (
          <form onSubmit={handleChangePassword} className="card space-y-4">
            <h2 className="text-lg font-semibold text-gray-800">修改密码</h2>
            <div>
              <label className="block text-sm font-medium text-gray-600 mb-1">旧密码</label>
              <input type="password" className="input-field" value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
                placeholder="请输入旧密码" autoComplete="current-password" disabled={loading} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-600 mb-1">新密码</label>
              <input type="password" className="input-field" value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="至少 8 位，含大小写字母和数字" autoComplete="new-password" disabled={loading} />
              <PasswordStrength password={newPassword} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-600 mb-1">确认新密码</label>
              <input type="password" className="input-field" value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="再次输入新密码" autoComplete="new-password" disabled={loading} />
            </div>
            <button type="submit" className="btn-primary w-full" disabled={loading}>
              {loading ? '修改中...' : '修改密码'}
            </button>
          </form>
        )}

        {/* Recovery Management */}
        {activeTab === 'recovery' && (
          <div className="card space-y-4">
            <h2 className="text-lg font-semibold text-gray-800">恢复口令托管</h2>
            <p className="text-sm text-gray-500">
              设置恢复口令后，忘记密码时可通过在线恢复流程找回数据。此功能默认关闭。
            </p>

            <button onClick={handleCheckRecovery} className="btn-ghost text-sm">
              查询当前托管状态
            </button>

            {recoverySet !== null && (
              <div className={`p-3 rounded-lg text-sm ${recoverySet ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                {recoverySet ? '已设置托管' : '未设置托管'}
              </div>
            )}

            {recoverySet === false && (
              <form onSubmit={handleSetRecovery} className="space-y-3">
                <div>
                  <label className="block text-sm font-medium text-gray-600 mb-1">恢复口令</label>
                  <input type="password" className="input-field" value={recoveryPhrase}
                    onChange={(e) => setRecoveryPhrase(e.target.value)}
                    placeholder="设置与登录密码不同的口令" disabled={loading} />
                </div>
                <button type="submit" className="btn-primary w-full" disabled={loading || !recoveryPhrase}>
                  {loading ? '设置中...' : '开启托管'}
                </button>
              </form>
            )}

            {recoverySet === true && (
              <div className="space-y-3">
                <div>
                  <label className="block text-sm font-medium text-gray-600 mb-1">输入密码以确认删除托管</label>
                  <input type="password" className="input-field" value={recoveryPwdForDelete}
                    onChange={(e) => setRecoveryPwdForDelete(e.target.value)}
                    placeholder="输入登录密码" disabled={loading} />
                </div>
                <button onClick={handleDeleteRecovery} className="btn-ghost w-full text-red-500 hover:text-red-600"
                  disabled={loading || !recoveryPwdForDelete}>
                  {loading ? '删除中...' : '取消托管'}
                </button>
              </div>
            )}
          </div>
        )}

        {/* Data Export */}
        {activeTab === 'export' && (
          <div className="card space-y-4">
            <h2 className="text-lg font-semibold text-gray-800">数据导出</h2>
            <p className="text-sm text-gray-500">导出操作为在浏览器端完成，数据不会离开你的设备。</p>
            <div className="flex gap-3">
              <button onClick={handlePlaintextExport} className="btn-primary flex-1" disabled={loading}>
                <Download className="w-4 h-4 inline mr-1" />
                明文导出
              </button>
              <button onClick={handleEncryptedExport} className="btn-ghost flex-1" disabled={loading}>
                <Shield className="w-4 h-4 inline mr-1" />
                密文导出
              </button>
            </div>
          </div>
        )}

        {/* Delete Account */}
        {activeTab === 'delete' && (
          <form onSubmit={handleDeleteAccount} className="card space-y-4 border border-red-100">
            <h2 className="text-lg font-semibold text-red-600">注销账户</h2>
            <p className="text-sm text-gray-500">
              此操作不可逆。你的所有日记、附件和账户信息将被永久删除。
            </p>
            <div>
              <label className="block text-sm font-medium text-gray-600 mb-1">输入"确认注销"以继续</label>
              <input type="text" className="input-field" value={deleteConfirm}
                onChange={(e) => setDeleteConfirm(e.target.value)}
                placeholder="确认注销" disabled={loading} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-600 mb-1">输入密码以确认</label>
              <input type="password" className="input-field" value={deletePassword}
                onChange={(e) => setDeletePassword(e.target.value)}
                placeholder="输入登录密码" disabled={loading} />
            </div>
            <button type="submit" className="w-full py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors"
              disabled={loading}>
              {loading ? '注销中...' : '确认注销'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
