# App 图标

用户选择方案 1：黑色石墨底、白色特斯拉 T、右下角蓝色蓝牙徽章。
使用内置 imagegen 根据选定对比图生成单独方形原稿。
提示词要点：保持方案 1；全幅深石墨背景；白色 Tesla T 加右下蓝色 Bluetooth 圆形徽章；标志在中心约 60% 区域；无文字、外框、预裁圆角，由 Android 自适应图标掩膜裁切。

彩色原稿：`app/src/main/res/drawable-nodpi/ic_launcher_art.png`。
彩色层：`drawable/ic_launcher_foreground.xml`；主题单色层：`drawable/ic_launcher_monochrome.xml`。
入口：`mipmap-anydpi-v26/ic_launcher.xml`。Android minSdk 为 31，使用 adaptive icon。
