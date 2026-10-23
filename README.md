# Tesla BLE Unlock Android

特斯拉蓝牙钥匙 · 安卓版。当前版本 **0.1.12**，Android 12（API 31）及以上。

> **实测环境**：特斯拉 **Model Y 2025 焕新版 标准续航版**（中国产，VIN `LRW` 开头）+
> **努比亚豆包手机二代**（NX888J，Android 16）。其他车型、年款、固件及手机均**未验证**，
> 不保证可用。本项目是个人实验性质的玩具，欢迎自行下载、fork、改成自己能用的版本。

## 免责声明与风险提示

- 本项目为**个人技术研究与学习项目**，与特斯拉公司（Tesla, Inc.）无任何关联，非官方软件。
- 当前实现仅通过车端 BLE 钥匙协议执行常规的钥匙配对、锁车/解锁、前后备箱和空调操作，
  **不涉及篡改车辆报文、不绕过车辆安全校验，也不涉及自动驾驶、制动、转向等行车安全系统**。
- 但请注意：**无论原厂还是非原厂软件，任何对车辆的控制操作都可能因车型、年款、固件版本、
  信号环境、操作时机等因素产生不可预知的行为和风险。** 使用本软件即表示你已自行完成
  风险识别与评估，并**自担全部风险**。
- 请仅在安全环境（驻车、非公共道路、可随时人工接管）下测试；车辆处于行驶、充电、
  OTA 升级等状态时请勿操作；请勿用于商业用途。
- 使用本软件可能影响车辆保修或违反相关服务条款，请自行确认并遵守当地法律法规。
- 因使用本软件造成的任何车辆损坏、数据丢失、人身或财产损失，**作者不承担任何责任**。
- **本项目仅用于技术研究。**

## 项目结构

- `app/`：Android App、BLE 通信、本机密钥、诊断日志及中文界面。
- `protocol/`：VCSEC 报文、认证会话、BLE 分帧及协议单元测试。
- `prototype/`：早期可交互 HTML 设计原型；与 Android 最新功能并非完全同步。
- `docs/`：图标与日志说明、[BLE 扫描定位权限经验](docs/ble-scan-location-permission.md)、[研发踩坑记录](docs/development-lessons.md)。
- `scripts/read-device-logs.py`：通过 ADB 导出手机本地历史日志；`scripts/parse_btsnoop.py`：解析 HCI 抓包中的 LE 广播。

## 已接入功能

按 VIN 搜索车辆、添加钥匙请求、钥匙认证、手动锁车/解锁、打开前备箱、打开/关闭后备箱、读取车锁及前后备箱状态，以及开启/关闭空调和读取空调状态。连接且处于前台时，每次状态查询完成后约 2 秒再次查询；控制操作与查询串行执行。已配对车辆从后台返回时会尝试重新搜索连接。

默认进入完整车辆页，右上角显示蓝牙连接状态：未连接为灰色，连接认证成功后为蓝色。未连接时所有车辆控制禁用，状态显示未知；点击蓝牙标志可添加钥匙或重新连接。App 已移除演示模式。搜索页提供呼吸动画；“我的”包含添加钥匙、换肤、帮助。帮助弹框说明六步添加流程。蓝牙扫描、连接、认证及操作结果自动写入本地轮转日志，读取方法见 [诊断说明](docs/diagnostics.md)。

> **给蓝牙开发者的提醒**：本项目的 `BLUETOOTH_SCAN` 未使用 `neverForLocation` 标志，并同时申请 `ACCESS_FINE_LOCATION`——两者缺一可能导致扫描静默收不到目标设备（部分 ROM 会直接丢弃 `neverForLocation` 客户端的广播报告）。完整案例和排查方法见 [BLE 扫描定位权限经验](docs/ble-scan-location-permission.md)。另外特斯拉车机熄屏数分钟后会停止发送钥匙广播，扫不到车时先点亮车机屏幕。

## 构建

需要 JDK 17 和 Android SDK 34。在未入库的 `local.properties` 配置本机 `sdk.dir`，或设置 `ANDROID_HOME`。

```sh
./gradlew :protocol:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 添加钥匙

坐进车辆并准备已有实体钥匙卡 → App 自动生成本机密钥 → 输入 VIN 搜索连接 → App 提交添加请求 → 按车型要求刷实体卡 → 车机确认后回 App 验证。

实现参考 [Tesla 官方 vehicle-command](https://github.com/teslamotors/vehicle-command)。当前使用 Driver 角色和 Android 设备类型。

## 验证边界

构建和现有单元测试通过，日志已验证可以在 App 重启后保留并导出。实车搜索、配对、连接认证与状态同步已在 2025 款 Model Y（中国产）上验证；**锁车、解锁、前后备箱与空调等控制操作尚未完成实车验收**，车型及固件可能影响功能。现有 8 项单元测试覆盖广播名、状态文案、空调报文及响应解析、VCSEC/Infotainment 分域认证和加密往返、重复响应拒绝；不代表车辆控制已完成实车验证。

车锁和前后备箱使用 VCSEC；空调使用单独的 Infotainment 认证会话。前台空调状态约每 10 秒轮询一次，可手动刷新；空调失败暂停其自动轮询，保留基础控制并允许手动重试。详情见 [空调实现说明](docs/climate-control.md)。车窗、充电、电量查询尚未实现。前备箱不能通过此 App 关闭；后备箱关闭能力依车型而异。App 在后台断开连接，不提供后台自动解锁。

本机配置、构建产物、密钥、导出日志和真机 QA 截图均排除入库。

## 研发踩坑记录

从“能编译”到“实车可用”踩过的坑，按 **现象 → 根因 → 处理** 记录。实测环境：特斯拉 Model Y 2025 焕新版 标续（VIN `LRW` 开头）+ 努比亚豆包手机二代（NX888J，Android 16）。其他车型未验证；详细排查过程见 [BLE 扫描定位权限经验](docs/ble-scan-location-permission.md)。

### 扫描与权限

- **`neverForLocation` 会让部分 ROM 丢弃特定广播**：App 能收到周围 90 台设备却唯独收不到车，
  而系统蓝牙设置可见。根因是该机型的栈不向带此标志的三方客户端投递这辆车的广播（芯片与协议栈均已收到，
  HCI 抓包可证；系统设置是特权应用不受限）。处理：`BLUETOOTH_SCAN` 不带该标志，
  同时申请 `ACCESS_FINE_LOCATION` 并要求定位服务开启。
- **去掉标志但不给定位权限 = 静默 0 结果**：无标志的扫描需要精确定位权限，否则系统按资格过滤，
  不报错、不回调失败。三个权限要一起运行时申请。
- **广播名末位不是固定 `C`**：`S` + SHA-1(VIN) 前 16 位 hex + VIN 派生的校验字符，每辆车不同。
  硬编码末位会导致永远匹配不上；应按 16 位 hex 前缀匹配，并用 `S[0-9a-fA-F]{16}.` 识别任意特斯拉广播名，
  附近有多台时按 VIN 前缀排序标注“你的车辆”。
- **车不是持续广播**：车机熄屏、整车低功耗后停止发送钥匙广播，广播呈约 30 秒的间歇簇。
  扫不到时先点亮车机屏幕、靠近车辆再扫。

### 协议与加密

- **VCSEC（domain 2）与 Infotainment（domain 3）是两套独立会话**：route、challenge、会话密钥、
  epoch、计数器各自独立；接收报文必须按 `domain + route` 过滤，否则会串台。
- **VCSEC 应答是明文**：官方 `unmarshalVCSECResponse` 从不解密，直接解析
  `RoutableMessage.protobuf_message_as_bytes(10)` 中的 `FromVCSECMessage`。
  把“响应未加密”当失败会导致状态查询后主动断连、界面回到“未连接”。
  仅当应答带 `SignatureData.AES_GCM_Response_data(13/9)` 时才走解密。
- **状态查询用无签名的 InformationRequest**：`UnsignedMessage{InformationRequest{type=GET_STATUS}}`，
  以 `AuthMethodNone` 明文发送；应答为
  `FromVCSECMessage{vehicleStatus{closureStatuses=1, vehicleLockState=2}}`。
  锁车/解锁等控制命令仍走签名加密请求。
- **proto3 零值不上线**：`ClosureState_E.CLOSED = 0` 编码时被省略，若把 `null` 当“未知”，
  关着的备箱也会显示“状态未知”。正确语义：**容器存在则缺省成员按 CLOSED 处理；容器缺失才是未知**。
- **命令确认语义**：`commandStatus.operationStatus` 为 `0=OK / 1=WAIT / 2=ERROR`；
  白名单操作的细因在 `whitelistOperationStatus`。`WAIT` 不算完成；配对等待车机确认期间
  车会以约 1 秒间隔推送状态报文，属正常现象，不要据此中断。
- **加密请求的 AAD 一个字节都不能错**：metadata tag 顺序
  `0=签名类型, 1=domain, 2=VIN, 3=epoch, 4=expires_at, 5=counter, 7=flags, 8=request_hash, 9=fault, 255=END`；
  `request_id = 0x05 || GCM tag`；计数器严格递增、重复响应必须拒绝。
- **配对时序**：发 `pairRequest` → 车机弹窗 → 车机确认后界面自动退出（正常）→
  回 App 点“我已在车机确认，验证钥匙” → session hello 的 `session info status(5) == 0`
  才代表钥匙进入白名单。车机确认后不回 App 验证会超时回滚；钥匙已存在时车机不再弹窗，
  App 直接验证通过，属预期行为。

### 连接与界面

- **一次解析失败不要踢掉整个连接**：区分“命令级失败”（保留连接、仅提示）与
  “会话级失败”（断开重连）；轮询类请求失败不应断开 BLE。
- **每 2 秒全量重建 View 树会闪烁**：按“可见状态签名”比对，签名不变跳过重建。
- **自动重连的三个时机**：后台返回前台、冷启动（`autoConnectIfPaired`）、
  意外断连（GATT 133 等，延迟 3 秒重扫）。
- **控制后以车端回读为准**：收到 `CommandStatus.OK` 后立即再查状态，不根据写入成功推断锁/备箱状态。

### 排查方法论

按“从应用往下剥”的顺序取证，每一步排除一类嫌疑：

1. **App 层**：扫描回调记录原始广播字节（`ScanRecord.bytes`）、RSSI、可连接标志。
   “收到 90 台设备但无一匹配”即可排除过滤逻辑。
2. **HCI 层**：`adb shell settings put secure bluetooth_hci_log 1` → 重启蓝牙 → 复现 →
   `adb bugreport`，取 `FS/data/misc/bluetooth/logs/btsnoop_hci.log*`，直接在字节里搜设备名 ASCII。
   部分机型只产出脱敏版（`.filtered`）：地址与部分 UUID 打码，但广播名和 RSSI 保留。
3. **时间相关性**：把 HCI 中目标设备的广播时间戳与 App 扫描窗口对齐；重叠窗口内
   栈已收到而 App 收不到，即可断定是分发问题而非射频或车端问题。
4. **变量替换**：服务 UUID 过滤、名字过滤、`setLegacy(false)`、加/去 `neverForLocation`、
   加定位权限——每次只改一个变量，快速收敛。
5. 工具：`scripts/read-device-logs.py` 导出 App 轮转日志，`scripts/parse_btsnoop.py` 解析 HCI 广播。

**教训**：不要用“系统设置能扫到”证明 App 代码没问题，也不要用“App 扫不到”直接怀疑框架——
先确认广播是否真的到达了芯片/协议栈这一层。协议层单测用虚拟密钥和官方字节向量，
能回归协议改动，但不能替代实车验收。
