# 简记 (Jianji)

一个本地优先的便签应用。**无广告、无登录、无会员**——数据默认只存在你自己的设备上，想同步可以自己接 WebDAV。

这个项目是从零开始写的一个干净实现，参考了「极简便签」的使用体验，但不是它的代码。密码锁控件复用了原作者 Hank 开源的 [PasscodeView](https://github.com/hanks-zyh/PasscodeView)（Apache-2.0），详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 功能

| 分类 | 说明 |
| --- | --- |
| 笔记 | 新建 / 编辑 / 删除，输入自动保存（停止输入 1.2 秒落库，退出时强制保存） |
| 组织 | 文件夹（新建 / 重命名 / 删除，删除后笔记回到「未分类」）、置顶、回收站（可恢复 / 彻底删除 / 一键清空） |
| 查找 | 全文搜索（标题 + 正文），匹配不到时显示空状态 |
| 外观 | 浅色 / 深色 / 跟随系统；正文字号 12–30sp；行距 1.0–2.5 倍 |
| 列表 | 六种列表样式（普通 / 卡片 / 网格 / 摘要 / 日历 / 精简）+ 三种排序，都保持置顶优先 |
| 编辑 | 撤销 / 重做（最多 60 步）、编辑器工具栏（缩进、列表、待办、引用、分隔线、插入时间日期） |
| Markdown | 可切换 Markdown 模式，工具栏变 H1/H2/H3、加粗、列表、待办、引用、代码、代码块、链接；预览页渲染成富文本 |
| 安全 | 4 位数字应用密码（离开应用自动重新上锁）；可对单条笔记单独加锁 |
| 数据 | 导出备份 / 导入备份（JSON，走系统文件选择器），一键清空 |
| 同步 | WebDAV（坚果云、Nextcloud、自建 NAS 都行）：测试连接、上传快照、从云端选备份恢复、启动时自动备份 |

刻意不做的事：不集成任何统计或广告 SDK，不需要账号，没有厂商服务器。`INTERNET` 权限只用于你自己配置的 WebDAV 地址，没配置就不会发任何请求。

## 技术栈

- Kotlin 2.3.21，AGP 8.13.2，Gradle 8.14.5，JDK 17 目标
- compileSdk 36 / targetSdk 36 / minSdk 26
- View + XML 布局 + ViewBinding，Material 3 主题
- Room 2.7.1（KSP 注解处理）+ Coroutines Flow
- OkHttp 4.12.0（只用于 WebDAV 的 PROPFIND / PUT / GET / MKCOL）
- `org.json` 手写备份格式，不引入序列化框架

## 构建

需要 Android SDK（platform 36、build-tools 36.0.0）和 JDK 17+。先在 `local.properties` 里指向 SDK：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

然后：

```bash
./gradlew :app:assembleDebug     # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease   # 需要签名配置，见下
```

### 发布签名

在项目根目录创建 `keystore.properties`（已被 .gitignore 忽略，不会进仓库）：

```properties
storeFile=jianji-release.jks
storePassword=你的密码
keyAlias=jianji
keyPassword=你的密码
```

`app/build.gradle.kts` 会自动读取它；不提供时 `assembleRelease` 产出未签名包。

## 目录结构

```
app/src/main/java/com/jianji/note/
├── App.kt                      Application：初始化 Prefs、主题、数据库、应用级作用域、自动备份
├── data/
│   ├── Note.kt / Folder.kt     Room 实体
│   ├── NoteDao.kt / FolderDao.kt
│   ├── AppDatabase.kt
│   ├── Repository.kt           事务封装（删文件夹时迁移笔记、云恢复时整体替换等）
│   ├── NoteSort.kt             列表排序（纯函数，可测）
│   └── Prefs.kt                SharedPreferences 设置项
├── net/
│   ├── WebDavClient.kt         PROPFIND / PUT / GET / MKCOL
│   ├── WebDavParser.kt         解析 207 Multi-Status
│   ├── WebDavSync.kt           把客户端、备份序列化、仓库串起来
│   └── WebDavResult.kt         成功 / 失败结果类型
├── ui/
│   ├── BaseActivity.kt         统一关闭系统自动避让，由各页面自己处理 insets
│   ├── MainActivity.kt         列表 + 抽屉 + 搜索 + 长按菜单 + 解锁
│   ├── NoteListAdapter.kt      六种列表样式各自一个 item 布局
│   ├── DrawerAdapter.kt        抽屉项（全部 / 未分类 / 文件夹 / 新建）
│   ├── EditActivity.kt         编辑器（撤销重做、锁定、Markdown 切换、工具栏）
│   ├── MarkdownPreviewActivity.kt
│   ├── MarkdownRender.kt       把解析结果渲染成 Spannable
│   ├── TrashActivity.kt        回收站
│   ├── SettingsActivity.kt     设置
│   ├── WebDavActivity.kt       云同步配置与操作
│   ├── PasscodeActivity.kt     密码锁
│   └── Selection.kt            当前列表视图
├── util/
│   ├── Backup.kt               JSON 导入导出
│   ├── TimeFmt.kt              时间显示（今天显示时刻，今年显示月日…）
│   ├── MarkdownParser.kt       Markdown 解析（纯函数，可测）
│   ├── EditorTools.kt          工具栏在光标处的文本操作（纯函数，可测）
│   └── WindowInsetsExt.kt      系统栏 insets 处理
└── widget/passcode/            内置的 PasscodeView（第三方，见声明文件）
```

## 已知限制

- **没有**图片附件、富文本、自定义字体、自定义背景、桌面小组件、快捷方式、导出 TXT/PDF、分享为图片、语言切换。列表样式还差原版的 4 种（Material Design / 小米便签 / 锤子 / 熊掌记），那些主要是视觉差异。
- Markdown 渲染是自己写的，支持 H1–H3、加粗、斜体、删除线、行内代码、代码块、无序/有序列表、待办、引用、链接、分割线；**不支持**表格、脚注、嵌套列表。
- 云同步是快照式覆盖，不是双向合并：上传会整体替换云端文件，恢复会先清空本机再导入（有二次确认）。多设备同时改会产生覆盖，不会智能合并。
- 密码锁是 4 位数字，存在 SharedPreferences 里（明文）。防的是别人拿起手机翻看，不是防取证级的攻击；忘记密码只能通过系统「清除数据」找回。
- WebDAV 密码同样明文存在 SharedPreferences 里。
- 为兼容内网 http WebDAV 服务，`usesCleartextTraffic` 是开着的；公网地址请用 https。
- 排序固定为「置顶优先，然后按修改时间倒序」，没有提供排序设置。
- 界面只有中文。
