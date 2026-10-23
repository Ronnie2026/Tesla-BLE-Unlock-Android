# 手机本地诊断日志

从 0.1.2 开始自动记录，保存在 App 私有目录 `files/diagnostics/`，JSONL 格式。
每条带本地时间及 UTC 偏移、开机毫秒数、进程运行编号和搜索尝试编号。
每个文件约 512 KiB，当前文件加 4 个轮转文件，总量约 2.5 MiB；超限覆盖最旧记录，不承诺固定保留天数。
重启 App/手机仍保留；覆盖安装保留；卸载或清除应用数据会删除。

记录启动版本、系统和机型、权限结果、扫描目标广播名、附近设备匿名标识、Tesla 格式广播名、RSSI、可连接标志、服务 UUID、扫描结束/错误、GATT 状态/MTU/订阅/写入状态、消息长度、配对/认证/命令结果、超时及前后台切换。
不记录完整 VIN、私钥、公钥、会话密钥、原始报文和原始蓝牙地址；非 Tesla 广播名用 other/unnamed 代替。广播名及匿名标识仍仅留本地，不上传。

## 读取历史日志

手机 USB 连接并允许调试，使用当前 debug APK：

```sh
python3 scripts/read-device-logs.py
```

导出至被 Git 忽略的 `captures/diagnostics-时间.tar`。包含当前及轮转日志，分析时按 time/elapsedMs 及 run/attempt 关联。
使用 `adb shell run-as com.ronnie.teslakey ls -l files/diagnostics` 可检查文件。
脚本依赖 debug 包的 run-as 权限，不适用于未来不可调试的 release 包。

实车测试：保持前台，搜索一次，等待结果；如有必要再运行诊断扫描。发生问题后保留 App 数据，告诉维护者大致测试时间。日志不能补录升级前发生的问题。
