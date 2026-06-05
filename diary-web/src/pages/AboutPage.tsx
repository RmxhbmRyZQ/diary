import { Link } from 'react-router-dom';
import { Shield, Lock, EyeOff, Github } from 'lucide-react';

export default function AboutPage() {
  return (
    <div className="min-h-screen bg-warm-50">
      <div className="max-w-2xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-warm-100 rounded-2xl mb-4">
            <Shield className="w-8 h-8 text-warm-600" />
          </div>
          <h1 className="text-2xl font-bold text-gray-800 mb-2">隐秘日记</h1>
          <p className="text-gray-400">你的秘密，只有你知道</p>
        </div>

        <div className="space-y-6">
          <section className="card p-6">
            <div className="flex items-center gap-3 mb-3">
              <Lock className="w-5 h-5 text-warm-600" />
              <h2 className="text-lg font-semibold text-gray-800">什么是零知识加密？</h2>
            </div>
            <p className="text-sm text-gray-600 leading-relaxed">
              简单来说，<strong>连服务器都不知道你写了什么</strong>。
              你的日记在离开浏览器之前就已经被加密，服务端只存储一堆看不懂的乱码。
              即使服务器被攻击，黑客能拿到的也只是一堆加密数据，没有你的密码谁也解不开。
            </p>
          </section>

          <section className="card p-6">
            <div className="flex items-center gap-3 mb-3">
              <EyeOff className="w-5 h-5 text-warm-600" />
              <h2 className="text-lg font-semibold text-gray-800">隐私保护怎么做到的？</h2>
            </div>
            <div className="text-sm text-gray-600 leading-relaxed space-y-2">
              <p><strong>注册时</strong>：浏览器生成一把只有你知道的"钥匙"（密钥），用你的密码锁好，存到服务器。</p>
              <p><strong>写日记时</strong>：内容在浏览器里用这把钥匙加密，服务器收到的是密文。</p>
              <p><strong>看日记时</strong>：服务器把密文发回来，浏览器用你的钥匙解密，只有你看到原文。</p>
              <p><strong>换设备时</strong>：用同一个密码登录，就能拿到同一把钥匙，手机和电脑都能看。</p>
            </div>
          </section>

          <section className="card p-6">
            <h2 className="text-lg font-semibold text-gray-800 mb-3">功能一览</h2>
            <div className="text-sm text-gray-600 leading-relaxed space-y-1.5">
              <p>写日记，Markdown 编辑器，支持粘贴图片</p>
              <p>按年 / 月 / 日浏览和筛选</p>
              <p>心情和天气标记</p>
              <p>搜索标题、内容、标签</p>
              <p>写作统计（热力图、心情分布、标签云）</p>
              <p>设置恢复口令，忘记密码也能找回</p>
              <p>Android 端数据互通</p>
            </div>
          </section>

          <section className="card p-6">
            <h2 className="text-lg font-semibold text-gray-800 mb-3">技术实现</h2>
            <div className="text-sm text-gray-600 leading-relaxed space-y-1.5">
              <p>前端：React + TypeScript + Tailwind CSS</p>
              <p>后端：Spring Boot + MySQL</p>
              <p>加密：AES-256-GCM + PBKDF2-SHA256（浏览器原生 Web Crypto API）</p>
              <p>所有加解密均在浏览器端完成，服务端零知识</p>
            </div>
          </section>

          <section className="text-center pt-4">
            <a
              href="https://github.com/RmxhbmRyZQ/diary"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-6 py-3 bg-gray-800 text-white rounded-xl hover:bg-gray-700 transition-colors"
            >
              <Github className="w-5 h-5" />
              在 GitHub 上查看源码
            </a>
            <p className="text-xs text-gray-400 mt-3">
              欢迎 Star 和贡献代码
            </p>
          </section>

          <div className="text-center pt-2 pb-4">
            <Link to="/" className="text-sm text-warm-600 hover:text-warm-700">
              返回首页
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
