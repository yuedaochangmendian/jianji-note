# 简记 (Jianji)

一个完全离线的本地便签应用。**无广告、无登录、无会员、无网络权限**——数据只存在你自己的设备上。

这个项目是从零开始写的一个干净实现，参考了「极简便签」的使用体验，但不是它的代码。密码锁控件复用了原作者 Hank 开源的 [PasscodeView](https://github.com/hanks-zyh/PasscodeView)（Apache-2.0），详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 功能

| 分类 | 说明 |
| --- | --- |
| 笔记 | 新建 / 编辑 / 删除，输入自动保存（停止输入 1.2 秒落库，退出时强制保存） |
| 组织 | 文件夹（新建 / 重命名 / 删除，删除后笔记回到「未分类」）、置顶、回收站（可恢复 / 彻底删除 / 一键清空） |
| 查找 | 全文搜索（标题 + 正文），匹配不到时显示空状态 |
| 外观 | 浅色 / 深色 / 跟随系统；正文字号 12–30sp；行距 1.0–2.5 倍 |
| 安全 | 4 位数字应用密码（离开应用自动重新上锁） |
| 数据 | 导出备份 / 导入备份（JSON，走系统文件选择器），一键清空 |

刻意不做的事：不申请 `INTERNET` 权限，不集成任何统计或广告 SDK，不需要账号。

## 技术栈

- Kotlin 2.3.21，AGP 8.13.2，Gradle 8.14.5，JDK 17 目标
- compileSdk 36 / targetSdk 36 / minSdk 26
- View + XML 布局 + ViewBinding，Material 3 主题
- Room 2.7.1（KSP 注解处理）+ Coroutines Flow
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
├── App.kt                      Application：初始化 Prefs、主题、数据库、应用级作用域
├── data/
│   ├── Note.kt / Folder.kt     Room 实体
│   ├── NoteDao.kt / FolderDao.kt
│   ├── AppDatabase.kt
│   ├── Repository.kt           事务封装（删文件夹时迁移笔记等）
│   └── Prefs.kt                SharedPreferences 设置项
├── ui/
│   ├── MainActivity.kt         列表 + 抽屉 + 搜索 + 长按菜单 + 解锁
│   ├── NoteListAdapter.kt
│   ├── DrawerAdapter.kt        抽屉项（全部 / 未分类 / 文件夹 / 新建）
│   ├── EditActivity.kt         编辑器
│   ├── TrashActivity.kt        回收站
│   ├── SettingsActivity.kt     设置
│   ├── PasscodeActivity.kt     密码锁
│   └── Selection.kt            当前列表视图
├── util/
│   ├── Backup.kt               JSON 导入导出
│   └── TimeFmt.kt              时间显示（今天显示时刻，今年显示月日…）
└── widget/passcode/            内置的 PasscodeView（第三方，见声明文件）
```

## 已知限制

- 只有列表 / 编辑 / 文件夹 / 搜索 / 置顶 / 回收站 / 密码锁 / 备份。**没有** Markdown 渲染、富文本、图片附件、云同步、桌面小组件。
- 密码锁是 4 位数字，存在 SharedPreferences 里（明文）。防的是别人拿起手机翻看，不是防取证级的攻击；忘记密码只能通过系统「清除数据」找回。
- 排序固定为「置顶优先，然后按修改时间倒序」，没有提供排序设置。
- 界面只有中文。
