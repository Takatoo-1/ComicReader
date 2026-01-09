# Comic Reader

一个简洁的 Android 漫画阅读应用，基于 Kotlin 和 Jetpack Compose 构建。

## 功能特性

- 📚 **漫画管理**：从设备存储读取和管理漫画
- 🖼️ **图片浏览**：支持查看文件夹和压缩包中的图片
- 📦 **格式支持**：支持 ZIP 和 RAR 压缩包
- 🔍 **搜索功能**：快速搜索漫画
- 🎨 **现代化 UI**：Material3 设计风格

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **设计系统**：Material3
- **图片加载**：Coil
- **压缩支持**：junrar (RAR), ZipFile (ZIP)

## 系统要求

- Android 8.0 (API 26) 及以上
- 需要存储权限以访问漫画文件

## 安装使用

1. 克隆项目
2. 使用 Android Studio 打开项目
3. 同步 Gradle 依赖
4. 运行应用

首次启动时，应用会请求存储权限并创建 `ComicStorage` 文件夹用于存放漫画。

## 项目结构

```
app/src/main/java/com/example/comicreader/
├── MainActivity.kt          # 主 Activity
├── SecondActivity.kt        # 阅读器页面
└── ui/
    ├── screens/             # 页面组件
    │   ├── HomeScreen.kt    # 首页
    │   └── ProfileScreen.kt # 个人页面
    └── theme/               # 主题配置
```

## 构建配置

- **Compile SDK**：36
- **Min SDK**：26
- **Target SDK**：36

## 许可证

本项目仅供学习交流使用。
