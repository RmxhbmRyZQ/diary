# 隐秘日记 (Secret Diary)

基于零知识架构的加密日记应用，由 Claude + DeepSeek 协作实现。

## 项目简介

隐秘日记是一个以隐私为核心的日记应用。核心原则是**零知识**——服务端不存储、不传输、不感知任何明文数据。用户密码、日记内容、附件图片，全部在客户端加密后再上传，服务端仅作为不可信存储层。

**任何人（包括管理员和服务器运维者）都无法查看用户的明文数据。**

## 三端架构

```
┌──────────────────────────────────────────────────────────┐
│                         MySQL                           │
│            users │ entries │ attachments │ sessions      │
│                （全部密文，无明文数据）                     │
└──────────────────────────────────────────────────────────┘
                           ▲
                           │
              ┌────────────┴────────────┐
              │   Spring Boot 3.2.5    │
              │      Java 17           │
              │   RESTful API 服务      │
              │   /api/v1/auth/*       │
              │   /api/v1/entries/*    │
              │   /api/v1/attachments/*│
              │   /admin/*             │
              └──────────┬─────────────┘
                         │
           ┌─────────────┼─────────────┐
           │             │             │
           ▼             ▼             ▼
     ┌──────────┐ ┌──────────┐ ┌──────────────┐
     │ React 18 │ │  Vite 5  │ │ Android (Kotlin)│
     │ TypeScript│ │ Tailwind │ │ Jetpack Compose │
     │ Web Crypto│ │ IndexedDB│ │ Room + Hilt    │
     │   (Web)   │ │  (Web)   │ │  (Android)     │
     └──────────┘ └──────────┘ └──────────────┘
```

| 端 | 技术栈 |
|---|--------|
| **后端** | Spring Boot 3.2.5, Java 17, MySQL 8.0, JPA/Hibernate, Maven |
| **Web 前端** | React 18, TypeScript, Vite 5, Tailwind CSS, Axios, IndexedDB, Web Crypto API |
| **Android** | Kotlin, Jetpack Compose, Room, Retrofit, OkHttp, Hilt, Coil, Biometric |

## 核心流程

### 加密体系

```
用户密码 ──PBKDF2(salt, 60万轮)──► authKey ──bcrypt──► 服务端验证登录
    │
    └──PBKDF2(salt, 60万轮)──► KEK ──AES-GCM──► 加密/解密 DEK

DEK (256位随机密钥) ──AES-GCM──► 加密日记正文
                    ──AES-GCM──► 加密附件图片
```

| 密钥 | 生成方式 | 存储位置 | 说明 |
|------|---------|---------|------|
| DEK | 注册时客户端随机生成 | 客户端内存 + 用 KEK 加密后存服务端 | 真正的数据加解密密钥 |
| KEK | PBKDF2(password, salt, 600000轮) | 客户端 | 用于包装/解包 DEK |
| authKey | PBKDF2(password, salt, 600000轮) | 服务端 bcrypt 存储 | 用于登录验证 |

### 注册

1. 客户端获取服务端 KDF 配置
2. 生成随机 salt，用 PBKDF2 派生 authKey 和 KEK
3. 生成随机 DEK，用 KEK 加密后得到 encryptedDek
4. 发送 username + authKey + encryptedDek + salt 到服务端
5. 服务端对 authKey 做 bcrypt 存入数据库

### 登录

1. 客户端从服务端获取 salt
2. 用 PBKDF2(password, salt) 派生 authKey，发送给服务端
3. 服务端 bcrypt 验证，返回 encryptedDek
4. 客户端用 KEK 解密得到 DEK，缓存到本地

### 日记同步

1. `GET /entries/sync` — 获取摘要列表（id + 日期 + 更新时间）
2. 客户端比对本地 IndexedDB/Room，找出需要更新的条目
3. `GET /entries/batch` — 批量拉取完整密文
4. 客户端用 DEK 解密呈现

### 密码找回

1. 用户设置恢复口令时，DEK 用恢复口令 KEK 加密托管到服务端
2. 同时生成随机质询（challenge），用恢复口令 KEK 加密后一并存储
3. 找回时，用户需正确加密质询以证明持有恢复口令
4. 验证通过后可重置密码

## 项目结构

```
diary/
├── diary-backend/          # Spring Boot 后端
│   ├── src/main/java/com/diary/
│   │   ├── controller/     # REST 控制器
│   │   ├── service/        # 业务逻辑
│   │   ├── repository/     # JPA 数据访问
│   │   ├── model/entity/   # 实体类
│   │   ├── model/dto/      # 请求/响应 DTO
│   │   ├── security/       # 过滤器、限流
│   │   └── config/         # 配置类
│   ├── sql/                # 建表脚本 + 种子数据
│   └── pom.xml
│
├── diary-web/              # React Web 前端
│   ├── src/
│   │   ├── pages/          # 页面组件
│   │   ├── components/     # UI 组件
│   │   ├── context/        # AuthContext
│   │   ├── api/            # API 调用
│   │   ├── crypto/         # Web Crypto 加密
│   │   ├── db/             # IndexedDB 缓存
│   │   └── services/       # 同步服务
│   └── package.json
│
└── diary-android/          # Android 应用
│   ├── app/src/main/java/com/secretdiary/app/
│   │   ├── ui/             # Compose UI + ViewModel
│   │   ├── data/remote/    # Retrofit API + DTO
│   │   ├── data/local/     # Room 数据库
│   │   ├── security/       # CryptoManager + Session
│   │   └── sync/           # 同步管理
│   └── build.gradle.kts
│
├── 接口文档.md              # API 规范
├── 需求文档.md              # 产品需求
└── 交互过程.md              # 端到端交互流程
```

## License

MIT
