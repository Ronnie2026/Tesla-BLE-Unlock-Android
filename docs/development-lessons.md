# 研发踩坑记录：特斯拉 BLE 钥匙 App

这份文档记录本项目从“能编译”到“实车可用”过程中踩过的坑。每个坑按 **现象 → 根因 → 处理** 记录，
希望能帮到同样在做 BLE 车钥匙 / iOS 或 Android 近场控制的开发者。实测环境：
特斯拉 2025 款 Model Y（中国产，VIN `LRW` 开头）+ 努比亚 NX888J（Android 16）。

---

## 一、BLE 扫描与权限

### 1.1 `neverForLocation` 会让部分 ROM 丢弃特定广播（最深的坑）

- **现象**：App 扫描 30 秒能正常收到周围 55~90 台设备（含广播名、厂商数据、原始字节），
  唯独收不到自己的车；而系统蓝牙设置里车清晰可见。
- **根因**：Manifest 中 `BLUETOOTH_SCAN` 带了 `neverForLocation` 标志。该机型的 ROM（Android 16）
  会把某些设备的广播报告丢弃，不投递给带此标志的三方扫描客户端。芯片和协议栈都收到了
  （HCI 抓包 800+ 条可证），只是不分发给 App；系统设置是特权应用，不走这条限制。
- **处理**：`BLUETOOTH_SCAN` 不带 `neverForLocation`，并同时申请 `ACCESS_FINE_LOCATION`，
  且要求系统定位服务开启。目标和设备你不可控（车、手环、陌生 IoT）时，不要用 `neverForLocation`。
- 完整排查过程见 [BLE 扫描定位权限经验](ble-scan-location-permission.md)。

### 1.2 去掉标志但不给定位权限 = 0 结果

- **现象**：去掉 `neverForLocation` 后，App 连一台设备都收不到，且不报错。
- **根因**：无标志的扫描需要已授予 `ACCESS_FINE_LOCATION` 且定位服务开启，否则系统按定位资格过滤。
- **处理**：三个权限（`BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`、`ACCESS_FINE_LOCATION`）一起运行时申请；
  扫描前检查定位服务状态。权限失败是“静默 0 结果”，**不会**抛异常或回调 `onScanFailed`。

### 1.3 广播名不是“固定以 C 结尾”

- **现象**：按 `S` + SHA-1(VIN) 前 16 位 hex + `"C"` 精确匹配广播名，理论上车在眼前却永远匹配不上。
- **根因**：广播名末位是 VIN 派生的校验字符，每辆车不同，`C` 只是某一台车的巧合。
- **处理**：用 SHA-1(VIN) 前 16 位 hex 作为前缀匹配（`S7ee461a98b6db8a8...`），
  末位字符不参与识别；同时用 `S[0-9a-fA-F]{16}.` 的正则识别“任意特斯拉格式广播名”，
  便于附近有多台特斯拉时让用户手动选择（并按 VIN 前缀排序标注“你的车辆”）。

### 1.4 车不是持续广播，而是间歇“簇”

- **现象**：同一台车，有时扫得到、有时扫不到，扫描日志里完全静默。
- **根因**：车机熄屏/整车低功耗后停止发送钥匙广播；HCI 抓包显示广播以约 30 秒的簇出现。
- **处理**：扫不到时先点亮车机屏幕、靠近车辆再扫。App 文案中加入此提示。

---

## 二、协议与加密

### 2.1 VCSEC(domain 2) 与 Infotainment(domain 3) 是两套独立会话

- **现象**：空调与车锁交替操作时互相打扰，偶发“路由不匹配”。
- **根因**：两个域使用各自独立的 route、challenge、会话密钥、epoch 和计数器；
  报文必须按 `domain + route` 过滤，否则会把另一个域的报文当成自己的响应。
- **处理**：`TeslaSession` 增加 `domain` 参数；每个会话保留独立状态；接收时先 `accepts()` 匹配再处理。

### 2.2 VCSEC 的应答是**明文**，不是加密的

- **现象**：钥匙认证成功后，状态查询报“车辆响应未加密，未确认执行结果”，随后主动断连，界面显示未连接。
- **根因**：我们假设所有应答都走 AES-GCM 加密（以 Infotainment 的经验类推）。官方
  `vehicle-command` 的 `unmarshalVCSECResponse` 从不解密 VCSEC 应答，而是直接解析
  `RoutableMessage.protobuf_message_as_bytes(10)` 里的 `FromVCSECMessage`。
- **处理**：VCSEC 应答按明文解析；仅当应答携带 `SignatureData.AES_GCM_Response_data(13/9)` 时才走解密。
  不要把“响应未加密”当作失败条件。

### 2.3 状态查询要用**无签名的** InformationRequest

- **现象**：状态查询换着花样发都被拒或得不到状态。
- **根因**：官方流程中 `GET_STATUS` 是 `UnsignedMessage{InformationRequest{type=0}}`，
  通过 `AuthMethodNone`（不签名、不加密）发送；应答是明文
  `FromVCSECMessage{vehicleStatus{closureStatuses=1, vehicleLockState=2}}`。
- **处理**：`plainStatusRequest(route)` 直接构造明文 RoutableMessage（to=domain 2，from=route，
  payload=field 10，uuid=field 51，无 signature、无 flags）。锁车/解锁等控制命令仍走签名加密请求。

### 2.4 proto3 零值不上线，导致“关着的备箱显示未知”

- **现象**：开锁状态读出来了，但前后备箱一直显示“状态未知”。
- **根因**：`ClosureState_E` 的 `CLOSED = 0` 是 proto3 默认值，编码时被省略；
  解析时得到 `null`，又被当成“未知”展示。而 `ClosureStatuses` 容器本身是否存在才是判断依据。
- **处理**：**容器存在 → 缺省成员按 CLOSED(0) 处理；容器缺失 → 才表示未知**。
  同理，锁状态 `UNLOCKED = 0` 缺省不出现时也按已解锁解释。

### 2.5 命令确认语义：CommandStatus.OperationStatus

- `FromVCSECMessage.commandStatus(4)` → `operationStatus(1)`：`0=OK`、`1=WAIT`、`2=ERROR`；
  `whitelistOperationStatus(3)` 里才有白名单操作的细因（如钥匙链已满）。
- 收到 `WAIT` 不应视为完成；收到 `ERROR` 要把原因透传，不要统一报“失败”。
- 配对时车机弹出确认框期间，会以约 1 秒间隔推送状态报文，这不是错误，不要据此中断。

### 2.6 加密请求的 AAD 细节（错一个字节车辆就拒绝）

- metadata tag 顺序即签名对象：`0=签名类型, 1=domain, 2=VIN(personalization), 3=epoch,
  4=expires_at, 5=counter, 7=flags, 8=request hash, 9=fault`，以 `255=END` 结尾；
  AAD 再对其做 SHA-256。
- `request_id = 0x05 || GCM tag`，响应 AAD 会引用它；计数器必须严格递增，重复响应要拒绝。
- 会话信息校验：用会话密钥派生 `HMAC("session info")` 再对 metadata 签名，任何字段错位都会认证失败。

### 2.7 配对流程的真实时序

- **发送 `pairRequest` → 车机弹出“是否添加此钥匙？” → 用户在车机确认 → 车机界面自动退出（正常现象）
  → 回到 App 点击“我已在车机确认，验证钥匙” → App 发 session hello → 车返回会话信息
  → `session info status(5) == 0` 才代表这把钥匙真正进入白名单。**
- 车机确认后**必须**回 App 完成 hello 验证，否则车端会超时回滚；
  车机界面“自己返回了”不是失败，不需要重新添加。
- 钥匙已存在时再次点配对，车机不再弹窗，App 直接验证通过并显示“已添加”——这是预期行为，
  需要在界面上区分“新增成功”和“已验证已有钥匙”，否则用户会以为流程被跳过了。

---

## 三、连接生命周期与 UI

### 3.1 一次解析失败导致全局断连

- 早期实现中，任何响应解析异常都会走统一的 `fail()`：断开 BLE、清空会话、界面回到“未连接”。
  于是“状态查询应答格式不符”这种小问题被放大成“连不上车”。
- **处理**：区分“命令级失败”（保留连接、仅提示）与“会话级失败”（断开重连）；
  轮询类请求失败不要踢掉整个连接。

### 3.2 每 2 秒全量重建 View 树会闪烁

- **现象**：车辆页每隔两秒闪一下。
- **根因**：轮询刷新触发全量 `removeAllViews()` + 重建。
- **处理**：计算“可见状态签名”（页面、连接状态、状态文案、观察值、扫描结果等），
  签名不变则跳过本次重建。局部刷新优于全量重建，但签名方案改动最小、效果立竿见影。

### 3.3 自动重连的三个时机

- 后台返回前台：断开时记录 `wasBackground`，`onResume` 后自动重扫重连。
- 冷启动：进程被杀后重新打开，`autoConnectIfPaired()` 自动扫描、连接、验证、读取状态。
- 意外断连（如 GATT status 133）：延迟 3 秒自动重扫，避免用户手动点“连接”。

### 3.4 连接成功后先读状态，再做乐观 UI

- 任何控制命令在收到 `CommandStatus.OK` 后**立即再查一次状态**再更新界面；
  不根据“写入成功”或“命令已确认”直接推断锁/备箱状态，避免界面与实车不一致。

---

## 四、排查方法论（可直接复用）

按“从应用往下剥”的顺序取证，每一步都能排除一类嫌疑：

1. **App 层**：扫描回调里记录**原始广播字节**（`ScanRecord.bytes`）、RSSI、可连接标志。
   看到“收到了 90 台设备但无一匹配”，就可以把过滤逻辑排除。
2. **HCI 层**：`adb shell settings put secure bluetooth_hci_log 1` → 重启蓝牙 → 复现 → `adb bugreport`，
   取 `FS/data/misc/bluetooth/logs/btsnoop_hci.log*`，直接在字节里搜设备名 ASCII。
   注意部分机型只产出脱敏版（`.filtered`）：地址、部分 UUID 被打码，但**广播名和 RSSI 保留**。
3. **时间相关性**：把 HCI 中目标设备的广播时间戳与 App 扫描窗口对齐。
   若重叠窗口内栈已收到、App 却收不到，即可断定是“分发歧视”而非射频或车端问题。
4. **变量替换**：依次尝试服务 UUID 过滤、名字过滤、`setLegacy(false)` 扩展广播、加/去
   `neverForLocation`、加定位权限——每次只改一个变量，快速收敛到根因。

工具：`scripts/read-device-logs.py` 导出 App 轮转日志，`scripts/parse_btsnoop.py` 解析 HCI 广播。

**教训**：不要用“系统设置能扫到”来证明“App 的扫描代码没问题”；也不要用“App 扫不到”
直接怀疑库或框架——先确认广播是否真的到达了芯片/协议栈这一层。

---

## 五、测试与验证边界

- 协议层单测使用虚拟密钥与官方字节向量（广播名、空调报文、分域认证往返、重复响应拒绝），
  能在无车环境下回归协议改动；但**不能证明真实 ECDH、车辆固件兼容性或实车行为**。
- 实车验收项清单见 [空调实现说明](climate-control.md) 与 README 的验证边界。
- 诊断日志只记长度、结果和匿名标识，不记 VIN 全文、密钥、原始报文和真实 MAC；
  但广播名、RSSI 会留在本机日志中，导出分享时注意。

---

## 六、用到的官方资料

- [teslamotors/vehicle-command](https://github.com/teslamotors/vehicle-command)：
  `pkg/vehicle/vcsec.go`（VCSEC 命令与明文应答）、`pkg/protocol/protobuf/vcsec.proto`、
  `universal_message.proto`（RoutableMessage 字段号）、`signatures.proto`（AAD tag 表）。
- Android 官方文档：`BLUETOOTH_SCAN` 的 `neverForLocation` 语义、`ScanSettings.setLegacy`、
  `BluetoothLeScanner` 的过滤与回调类型。

有心补充其他机型/固件的坑，欢迎提 issue 或 PR。
