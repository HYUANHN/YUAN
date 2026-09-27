# 影视中心（VideoBox）

一个安卓原生"空壳"影视播放器。**不含任何片源与数据**，通过用户配置的采集站 / mac-cms 接口拉取影视数据播放。

- 包名：`com.videobox.movie`
- minSdk 21 / targetSdk 34 / Java 17
- 技术栈：Java + ExoPlayer(media3) + OkHttp + Gson + Glide + RecyclerView

## 功能

- 兼容两类影视接口：
  - **mac-cms 采集站**（`?ac=list/type/detail&t=/ids=/wd=`）
  - **聚合源**（`?ac=videolist&type=/detail`）
  - 播放串兼容 `$$$` 多线路、单个 `$` 线路、混合与单地址
- 分类动态绑定：从播放源读取主分类 + 子分类，默认首个分类加载，空数据自动回退全部
- 搜索（顶部搜索入口）
- 海报补取：mac-cms 列表无海报时后台从详情接口补取 + 片名文字占位
- 播放设置：声音 / 亮度 / 倍速 / 画面尺寸（含全屏、小屏）/ 长按加速 / 跳过片头片尾 / 硬件加速开关
- 广告过滤：HLS(m3u8) 分片级过滤（`FilteringDataSource` + 关键字黑名单）
- 播放源管理：
  - 输入节点地址（如 `https://node.mac-cms.com/`）拉取节点列表并保存
  - 已保存的源列表（点一下填入输入框）
  - 导入本地 JSON（识别 `sources[]` / `list[]` / `data[]` / 数组 / 单个 `{name,url,api}`）
  - 设置页"源地址列表"
- 收藏、播放历史
- 返回策略：非首页 tab 先回首页，首页退到后台（不强制关闭）

## 构建

### 方式一：使用 Gradle Wrapper（推荐）

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 方式二：使用系统 Gradle

需要 JDK 17 + Android SDK（platform 34 / build-tools 34.0.0）+ Gradle 8.7。

```bash
# 配置环境变量（按本机路径）
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME:$PATH

# 写入 SDK 路径（gradle 需要，不提交到 git）
echo "sdk.dir=/path/to/android-sdk" > local.properties

gradle :app:assembleDebug --no-daemon
```

> `local.properties`、`build/`、`.gradle/` 已在 `.gitignore` 中，不会提交。

## 播放源接口约定

- **mac-cms 源**（如暴风/量子/非凡/快车）：
  - 列表：`<api>?ac=list&t=<分类id>&pg=<页>`
  - 详情：`<api>?ac=detail&ids=<数字id>`
  - 搜索：`<api>?ac=detail&wd=<关键词>`
  - 分类：`<api>?ac=type`
- **聚合源**（如 7na7 / vodfa）：
  - 列表：`<api>?ac=videolist&type=<分类id>&pg=<页>`
  - 详情/搜索：`<api>?ac=detail&ids=<id>&wd=<关键词>`
  - 分类：站根返回 `class[]`

## 目录结构

```
app/src/main/java/com/videobox/movie/
├── MainActivity.java         # 主页（三 tab：首页/收藏/设置）+ 返回策略
├── App.java                  # 全局崩溃日志落盘
├── data/                     # PlaySource / VodItem / VodDetail / Category / Prefs
├── net/                      # ApiClient / MacCmsParser / AdFilter（接口解析+广告过滤）
├── player/                   # PlayerActivity / FilteringDataSource（播放器+HLS广告过滤）
└── ui/                       # HomeFragment / SearchActivity / DetailActivity /
                              # SourceManagerActivity / SourceListActivity / PosterFetcher 等
```

## 免责声明

本应用为"空壳"播放器，不提供、不存储任何影视数据，所有内容均来自用户自行配置的第三方采集站接口，仅供学习与技术研究。请遵守当地法律法规。

## 打包发布（不依赖应用商店）

```bash
# debug 包（可直接安装，覆盖升级）
gradle :app:assembleDebug

# release 包（需在 app/build.gradle 开启签名或使用 debug 签名）
gradle :app:assembleRelease
```

## Release 签名（可选）

默认未启用签名，`assembleRelease` 生成未签名包。如需正式签名（用于长期更新的正式安装包，需保持同一 keystore）：

1. 生成 keystore（仅一次，请妥善保管，丢失则无法覆盖更新已签名包）：
   ```bash
   keytool -genkey -v -keystore videobox.keystore -alias videobox -keyalg RSA -keysize 2048 -validity 10000
   ```
2. 把 keystore 放到项目根（**不要提交到 git**，或加入 `.gitignore`）。
3. 配置签名参数（任选一种）：
   - 环境变量：`KEYSTORE_FILE` `KEYSTORE_PASSWORD` `KEY_ALIAS` `KEY_PASSWORD`
   - 或写入 `gradle.properties`：`keystoreFile=...` `keystorePassword=...` `keyAlias=...` `keyPassword=...`
   - 或写入 `local.properties`（不入库）
4. 构建：`./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/*.apk`（已签名）

> `app/build.gradle` 已内置"检测到 keystore 就自动签名、否则不签名"的逻辑，未配置时构建也不报错。

## GitHub Actions 自动打包

仓库已带 `.github/workflows/build.yml`：推送到 `main`/`master`（或打 PR、手动触发）时自动在 GitHub 云端构建 **Debug + Release** APK，并作为 `Artifacts` 附件出现在 Actions 运行页，可直接下载安装。

- 构建使用的 Gradle Wrapper：`./gradlew`（仓库内已含 wrapper，无需手动装 Gradle）
- Release 包默认未签名；如需在 CI 里签名，在仓库 `Settings → Secrets and variables → Actions` 配置上面四个变量即可自动签名。


## 开源协议

本项目采用 **MIT License**（见根目录 [LICENSE](./LICENSE)）。

- **只开源"空壳"代码**，不包含任何片源、播放源地址或影视数据。
- 用户自行配置的源地址仅保存在各自设备的本地存储，不入库、不上传。
- 本应用仅供**学习与个人技术研究**，使用者需自行遵守当地法律法规及第三方平台条款，不得用于侵权或商业用途。
- 欢迎提交 Issue 与 PR 改进功能；涉及聚合第三方资源的改动请同步说明合规边界。

### 本地如何导入开发

```bash
git clone <你的仓库地址>
cd <仓库目录>
echo "sdk.dir=/你的/android-sdk路径" > local.properties   # 不入库
./gradlew :app:assembleDebug
```
