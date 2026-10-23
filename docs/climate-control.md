# 空调开关（0.1.8）

## 界面与行为

车辆页的车机版和功能版都显示“空调 · 已开启/已关闭/状态未知/未连接”，提供开启空调、关闭空调、刷新三个入口。未连接、认证未完成及操作等待期间禁用。

切换时先清除旧空调状态，不把写入成功或命令确认当成实际开关状态；收到车辆命令确认后立即查询空调状态，再显示车辆返回的 is_climate_on。响应未携带该字段则保持未知并提示，不将缺失字段当成关闭。0.1.10 起已移除演示模式；所有控制都要求真实连接并通过认证。

## 协议及串行调度

参考 Tesla 官方 vehicle-command：
- `pkg/vehicle/climate.go`：ClimateOn/ClimateOff。
- `pkg/vehicle/infotainment.go`：命令发往 DOMAIN_INFOTAINMENT。
- `pkg/protocol/protobuf/car_server.proto`：Action.vehicleAction(2) → hvacAutoAction(10) → power_on(1)。关闭时保留 hvacAutoAction 的空嵌套消息，bool 使用 protobuf 默认 false。
- 状态请求：Action.vehicleAction(2) → getVehicleData(1) → getClimateState(3)。
- 状态响应：Response.vehicleData(2) → VehicleData.climate_state(4) → ClimateState.is_climate_on(110)。Response.actionStatus(1) 的 result=1 为错误，与 VCSEC 的错误枚举不同。

TeslaSession 新增 domain 参数，默认 2（VCSEC），空调用 3（Infotainment）。两个域保留独立路由、挑战、会话密钥、epoch、计数器和响应校验状态。加密命令及响应 AAD 使用对应域。只有当前域/路由匹配的报文才进入认证处理。

所有请求仍通过同一 BLE 队列串行发送。空调首次使用时先发独立 hello，认证后发送原目标命令。基础状态轮询间隔仍为查询完成后约 2 秒；空调在下一轮空闲调度中按至少 10 秒间隔读取。命令期间不并行轮询。

空调操作/认证超时 12 秒；失败清空空调会话、状态和待执行命令，显示空调异常，暂停空调自动轮询，恢复基础状态轮询。用户可点刷新或开关重试；不会自动重复开关命令。断连、进入后台或重新连接会清空两个域的会话及空调状态。

## 验证

新增测试覆盖三个请求的字节编码、返回的 true/false 与未知状态、车辆拒绝原因、独立域及路由，以及两域的认证/命令加密/响应解密及重复响应拒绝。测试使用虚拟公钥和测试共享密钥，不能证明真实 Android ECDH 或实车兼容性。

构建及全部 7 项协议测试通过。已覆盖安装到连接的手机；解锁后已检查车机版界面，并在演示模式点击开启/关闭，确认状态分别变为已开启/已关闭；同时修正空调区左右留白。功能版和真实未连接按钮禁用尚未逐项真机点击验收。实车空调开关、车辆唤醒状态、钥匙权限及固件兼容性需车旁验证。

## 实车验收

1. 配对并连接，等基础状态读取完成，查看自动获取的空调状态。
2. 点击开启，确认车辆实际空调启动，且查询回读后显示已开启。
3. 点击关闭，确认车辆停止，且查询回读后显示已关闭。
4. 用车机/其他 App 改变空调状态，确认前台下一轮读取后更新。
5. 车辆不响应/拒绝时应显示异常和未知，不误报开启/关闭，基础车锁仍可操作。

相关日志事件：climate.hello、climate.reply、climate.error；加密报文只记录长度，不记录原文或密钥。
