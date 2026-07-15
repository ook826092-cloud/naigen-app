# 隐私政策

**最后更新：2026-07-15**

本隐私政策描述 NaiGen Android 应用（以下简称"本应用"）如何收集、使用和保护您的信息。

## 1. 数据收集

本应用**不收集任何个人身份信息**。具体来说：

| 数据类型 | 是否收集 | 说明 |
|---|---|---|
| 设备标识符（IMEI / Android ID） | 否 | 不读取 |
| 地理位置 | 否 | 不读取 |
| 通讯录 / 短信 | 否 | 不读取 |
| 摄像头 / 麦克风 | 否 | 不读取 |
| 网络浏览历史 | 否 | 不读取 |
| 设备品牌 / 型号 | 是（仅本地） | 用于显示厂商保活引导，不上传 |
| 应用列表 | 否 | 不读取 |

## 2. 用户主动提供的数据

以下数据由用户主动输入，**仅保存在 App 私有目录**（`/data/data/com.naigen.app/`），不会上传到任何服务器：

- **API Token**：用于调用 API 服务（https://API 服务器），通过 DataStore 加密存储
- **生成历史**：图片文件 + 缩略图 + 提示词，存于 App 私有 `filesDir/images/`
- **Prompt 收藏夹**：用户保存的常用提示词模板，存于 Room 数据库
- **自定义风格**：用户自定义的画师串，存于 Room 数据库
- **App 设置**：NSFW 开关、上次使用的参数等，存于 DataStore

卸载 App 后，以上所有数据会被系统自动清除。

## 3. 网络请求

本应用仅与以下服务通信：

- **API**（https://API 服务器）
  - 用途：调用文生图 API
  - 发送数据：用户输入的提示词、画师串、API Token、设备生成的随机种子
  - 接收数据：生成的图片字节流、任务 ID、余额点数
  - 不经过任何中间服务器，App 直接与 API 通信

- **GitHub 仓库**（https://github.com/ook826092-cloud/naigen-app）
  - 用途：用户主动点击"关于"或"说明文档"时跳转浏览器查看
  - 不发送任何数据，仅 URL 跳转

## 4. 权限使用

| 权限 | 用途 | 是否必须 |
|---|---|---|
| `INTERNET` | 调用 API | 是 |
| `ACCESS_NETWORK_STATE` | 检测网络状态 | 是 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 后台轮询生成任务 | 是 |
| `POST_NOTIFICATIONS` | 显示生成进度通知 | 否（可拒绝） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 申请加入电池白名单 | 否（手动） |
| `READ_MEDIA_IMAGES` | 保存到相册时扫描已存在文件 | 否（仅 Android 13+） |
| `WRITE_EXTERNAL_STORAGE` | 保存到相册（Android 9 及以下） | 否 |

## 5. 第三方 SDK

本应用使用以下第三方 SDK，它们各有独立的隐私政策：

| SDK | 用途 | 是否上传数据 |
|---|---|---|
| OkHttp | 网络请求 | 否（仅 HTTP 客户端） |
| kotlinx.serialization | JSON 解析 | 否（本地） |
| Coil | 图片加载与缓存 | 否（本地） |
| Room | 本地数据库 | 否（本地） |
| DataStore | 偏好存储 | 否（本地） |
| Shizuku | 启动隐藏系统 Activity | 否（本地 IPC） |

## 6. 数据安全

- API Token 使用 Android 系统的 DataStore 加密存储（受 App sandbox 保护）
- 网络请求全部走 HTTPS
- 不使用任何统计分析 SDK（无 Firebase、无友盟、无 Google Analytics）
- 不包含广告 SDK

## 7. 儿童隐私

本应用不面向 13 岁以下儿童，也不收集儿童信息。

## 8. 用户权利

您可以随时：
- 在「设置 → API 服务商」中删除 Token
- 在「相册」中删除任意生成图片
- 卸载 App 以彻底清除所有本地数据

## 9. 隐私政策变更

本政策如有变更，会在 GitHub 仓库更新，并通过 App Release Notes 通知。

## 10. 联系方式

如有隐私问题，请在 GitHub 提 Issue：https://github.com/ook826092-cloud/naigen-app/issues

---

*本隐私政策以 MIT 协议发布，托管于 https://github.com/ook826092-cloud/naigen-app/blob/main/PRIVACY.md*
