# Tesla BLE Unlock Android

特斯拉蓝牙钥匙 · 安卓版。当前版本 **0.1.7**，Android 12（API 31）及以上。

## 项目结构

- `app/`：Android App、BLE 通信、本机密钥、诊断日志及中文界面。
- `protocol/`：VCSEC 报文、认证会话、BLE 分帧及协议单元测试。
- `prototype/`：早期可交互 HTML 设计原型；与 Android 最新功能并非完全同步。
- `docs/`：图标与日志说明。
- `scripts/read-device-logs.py`：通过 ADB 导出手机本地历史日志。

## 已接入功能

按 VIN 搜索车辆、添加钥匙请求、钥匙认证、手动锁车/解锁、打开前备箱、打开/关闭后备箱、读取车锁及前后备箱状态。连接且处于前台时，每次状态查询完成后约 2 秒再次查询；控制操作与查询串行执行。已配对车辆从后台返回时会尝试重新搜索连接。

搜索页提供呼吸动画；“我的”包含添加钥匙、外观模式、帮助。帮助弹框说明六步添加流程。蓝牙扫描、连接、认证及操作结果自动写入本地轮转日志，读取方法见 [诊断说明](docs/diagnostics.md)。

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

构建和现有单元测试通过，日志已验证可以在 App 重启后保留并导出。**实车搜索、配对、控制和状态同步尚未完成验收**；车型及固件可能影响功能。现有测试覆盖广播名及状态文案，不代表协议和车辆控制已经全面验证。

当前只接入 VCSEC；空调、车窗、充电、电量查询尚未实现。前备箱不能通过此 App 关闭；后备箱关闭能力依车型而异。App 在后台断开连接，不提供后台自动解锁。演示模式只改变模拟状态。

本机配置、构建产物、密钥、导出日志和真机 QA 截图均排除入库。
