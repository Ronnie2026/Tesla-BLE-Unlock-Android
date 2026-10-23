# BLE 扫描必须同时申请定位权限 —— 一次真实排障记录

面向安卓蓝牙应用开发者（尤其是做车钥匙、IoT 配网类 App 的）的经验教训。
结论先行，过程和证据在后，方便直接抄作业。

## TL;DR

1. **`BLUETOOTH_SCAN` 和 `ACCESS_FINE_LOCATION` 是绑在一起的。**
   Android 12+ 上只要你的 `BLUETOOTH_SCAN` **没有**带 `neverForLocation` 标志，
   系统就要求：已授予 `ACCESS_FINE_LOCATION` 运行时权限 **且** 系统定位服务开关打开，
   否则扫描**静默返回 0 结果**——不报错、不回调 `onScanFailed`，就是什么都收不到。
2. **反过来，带了 `neverForLocation` 也有坑。**
   部分厂商 ROM（本文实测：努比亚 NX888J「豆包手机」，Android 16）会**丢弃**发给
   `neverForLocation` 客户端的特定设备广播报告：芯片和协议栈都收到了（HCI 抓包可证），
   但三方 App 的 `ScanCallback` 永远等不到它。系统蓝牙设置是特权应用不走这条路，
   所以会出现"系统设置扫得到、你的 App 扫不到"的灵异现象。
3. **稳妥的组合**（本项目最终采用）：

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
```

```kotlin
// 运行时三个权限一起申请，缺一个都可能表现为"扫描不到某些设备"
requestPermissions(arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION), REQ)
```

同时提醒用户打开系统定位服务开关（不是授予 App 定位权限，是系统级的那个开关）。

## 症状（本项目踩坑实录）

- 车辆：特斯拉（BLE 广播名 `S` + 16 位 hex + 1 位校验字符，如 `S7ee461a98b6db8a8C`）。
- App 扫描 30 秒能收到周围 55~90 台设备（名字、厂商数据、原始广播字节全部正常解析），
  **唯独收不到车**；系统蓝牙设置里车明明可见。
- 最初怀疑广播名匹配规则错误——不是。匹配规则再宽松也没用，因为广播**根本没进回调**。

## 排查过程（可复用的方法论）

按"逐层往下剥"的顺序取证，每一层都能排除一半嫌疑：

1. **App 层**：给扫描回调日志加原始广播字节（`ScanRecord.bytes`），
   确认收到 90 台设备且无一匹配 → 过滤逻辑无辜，嫌疑移到系统层。
2. **HCI 抓包层**：`adb shell settings put secure bluetooth_hci_log 1` 后重启蓝牙、
   复现扫描、`adb bugreport` 导出 `FS/data/misc/bluetooth/logs/btsnoop_hci.log*`。
   直接在原始字节里搜车名 ASCII。结果：**车的高频广播全程都在**（每秒最多 30 条），
   芯片和协议栈都收到了 → 不是射频问题、不是车没广播。
3. **时间相关性**：把 HCI 里车广播的时间戳与 App 扫描窗口对齐，
   发现在重叠窗口内（App 正在扫、栈正在收）App 依然一条都收不到 →
   锁定为**栈对应用层的分发歧视**，而非车端或射频问题。
4. **变量替换**：依次试了服务 UUID 过滤、精确名字过滤、`setLegacy(false)` 扩展广播扫描，
   全部同样收不到 → 不是扫描参数问题，只剩"客户端身份"（`neverForLocation` 标志）这一个变量。
   去掉标志 + 补上定位权限后，**车立刻出现在配对列表**，问题终结。

## 两类"扫不到"的鉴别

| 现象 | 原因 | 处理 |
|---|---|---|
| 其他设备能收到、特定设备永远收不到；系统设置能看到它 | ROM 对 `neverForLocation` 客户端丢报告（本文案例） | 去掉标志 + 申请定位权限 |
| 所有设备都收不到（0 结果） | 定位权限或定位服务开关缺失（无标志时）；或射频/权限问题 | 补权限、开定位服务 |
| 有时收得到有时收不到 | **设备端间歇广播**（特斯拉车机熄屏数分钟后即停止发钥匙广播，呈约 30 秒间歇簇） | 点亮车机屏幕、靠近设备再扫 |

## 什么时候可以用 `neverForLocation`

- 它省掉定位权限和定位服务依赖，对"信标类"场景（自己控制的广播内容）通常没问题。
- 但只要你扫描的**目标设备不受你控制**（车、手环、陌生 IoT），就可能踩中本文这类
  ROM 级分发缺陷，且极难排查（因为系统设置能扫到，看起来像你代码的锅）。
- 建议：目标设备不可控时，宁可多要一个定位权限，也不要带这个标志。

## 证据补充（给较真的读者）

- 车辆广播原文（App 侧收到后的日志）：`020106 1aff4c000215 74278bda-b644-4520-8f0c-720eaf059935 0082 42c5 03022211 1309 "S7ee461a98b6db8a8C"`——
  flags + Apple iBeacon 格式厂商数据（特斯拉 beacon UUID）+ 16 位服务 UUID 列表 + 完整广播名。
- 该机型即使开了 HCI 日志，导出的也是脱敏版（`btsnoop_hci.log.filtered`）：设备地址、
  部分 UUID 被打码，但**广播名和 RSSI 保留**，足够做本文的时间相关性和字节搜索分析。
- 本项目 App 内置了轮转诊断日志（含 raw adv 字段），配合 `scripts/read-device-logs.py`
  和 `scripts/parse_btsnoop.py` 可完整复现上述取证过程。

实测环境：努比亚 NX888J（豆包手机），Android 16（BP2A.250605.031），2026-10。
其他 ROM 的同类问题欢迎在 issue 中补充机型和版本。
