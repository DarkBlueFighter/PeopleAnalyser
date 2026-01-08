# USB摄像头功能集成说明

## 已完成的工作

我已经为您的Android应用添加了USB摄像头支持功能。以下是所做的修改：

### 1. 添加的文件

#### UsbCameraManager.kt
创建了一个USB摄像头管理类，负责：
- 检测USB摄像头设备的连接和断开
- 请求USB设备权限
- 打开和关闭USB摄像头
- 启动和停止预览
- 提供帧数据回调接口（用于图像分析）

### 2. 修改的文件

#### AndroidManifest.xml
- 添加了USB相关权限：`USB_PERMISSION`
- 添加了USB主机模式特性声明：`android.hardware.usb.host`
- 添加了USB设备连接的intent-filter
- 添加了前台服务相机权限：`FOREGROUND_SERVICE_CAMERA`
- 引用了设备过滤器配置文件

#### build.gradle.kts
- 修复了namespace为 `com.uka.peopleanalyser`（匹配您的包名）
- 添加了UVCCamera库依赖：`com.github.saki4510t:UVCCamera:2.5.6`
- 该库提供了USB摄像头的底层支持

#### MainActivity.kt
添加了以下功能：
- USB摄像头管理器初始化
- USB摄像头连接/断开按钮
- USB摄像头视图（SurfaceView）
- USB设备事件回调处理
- 自动切换RTSP和USB摄像头显示

#### activity_main.xml
- 添加了USB摄像头预览的SurfaceView
- 添加了"连接USB摄像头"按钮
- 布局支持在RTSP流和USB摄像头之间切换

#### device_filter.xml (新文件)
定义了要监听的USB设备类型：
- Video class设备（class 14）
- UVC设备（class 239, subclass 2, protocol 1）

## 使用方法

### 在Android Studio中：

1. **同步Gradle**
   - 打开项目后，点击 "File" -> "Sync Project with Gradle Files"
   - 或点击顶部工具栏的"Sync Now"按钮
   - 这会下载所需的UVCCamera库

2. **连接USB摄像头**
   - 使用USB OTG线将USB摄像头连接到Android设备
   - 确保您的Android设备支持USB Host模式（大多数现代Android设备都支持）

3. **运行应用**
   - 应用启动后，会自动检测USB摄像头
   - 点击"连接USB摄像头"按钮
   - 系统会弹出USB权限请求对话框，点击"允许"
   - 摄像头画面会显示在屏幕上

4. **开始分析**
   - 连接成功后，"开始人流量分析"按钮会启用
   - 点击开始分析即可

## 功能特点

✅ **自动检测USB设备**：当USB摄像头插入时自动检测
✅ **权限管理**：自动请求USB权限
✅ **双模式支持**：支持RTSP网络流和USB摄像头两种输入源
✅ **自动切换显示**：根据输入源自动切换显示视图
✅ **错误处理**：完善的错误提示和异常处理
✅ **资源管理**：正确释放资源，避免内存泄漏

## 技术细节

### 支持的USB摄像头
- 标准UVC（USB Video Class）摄像头
- 大多数USB网络摄像头
- 部分专业USB摄像头

### 预览参数
- 默认分辨率：640x480
- 默认格式：MJPEG
- 可在UsbCameraManager.kt中调整

### 权限说明
应用需要以下权限：
- `CAMERA`：访问摄像头
- `USB_PERMISSION`：访问USB设备
- `FOREGROUND_SERVICE_CAMERA`：前台服务使用摄像头

## 下一步扩展

如果需要进一步功能，可以：

1. **图像处理**：使用`setFrameCallback()`获取每帧数据进行AI分析
2. **分辨率调整**：修改`setPreviewSize()`参数
3. **多摄像头支持**：扩展管理器以支持多个USB摄像头
4. **录制功能**：添加视频录制功能

## 故障排查

### 如果遇到问题：

1. **检测不到USB摄像头**
   - 确认设备支持USB Host模式
   - 检查USB OTG线是否正常
   - 在设置中检查USB调试是否开启

2. **编译错误**
   - 确保已同步Gradle
   - 检查JitPack仓库是否正确配置
   - 清理项目：Build -> Clean Project

3. **权限被拒绝**
   - 卸载应用重新安装
   - 在系统设置中手动授予权限

## 需要的操作

⚠️ **重要**：请在Android Studio中执行以下步骤：

1. 点击顶部的 **"Sync Now"** 按钮或使用菜单：
   **File > Sync Project with Gradle Files**

2. 等待同步完成（会下载UVCCamera库）

3. 编译并运行项目

如果Gradle同步失败，请检查：
- 网络连接是否正常
- JitPack.io是否可访问
- 是否已设置JAVA_HOME环境变量

---

如有任何问题，请随时询问！

