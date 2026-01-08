package com.example.comicreader

// Material Icons 是共享资源，Material 和 Material3 都使用相同的图标库
// 虽然包名是 material.icons，但它完全适用于 Material3，这是官方推荐的做法
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.comicreader.ui.screens.HomeScreen
import com.example.comicreader.ui.screens.ProfileScreen
import com.example.comicreader.ui.theme.ComicReaderTheme
import java.io.File

class MainActivity : ComponentActivity() {
    // - `onCreate`：方法名，Activity 生命周期方法
    // **生命周期对比：**
    // | Android Activity | React | Vue |
    // |-----------------|-------|-----|
    // | `onCreate()` | `componentDidMount()` / `useEffect(() => {}, [])` | `mounted()` |
    // | `onStart()` | 组件显示时 | `activated()` |
    // | `onResume()` | 页面获得焦点 | - |
    // | `onPause()` | 页面失去焦点 | - |
    // | `onDestroy()` | `componentWillUnmount()` / cleanup | `unmounted()` |

    // - `override`：关键字，表示重写父类的方法（类似 JavaScript 中重写父类方法）
    // - `savedInstanceState: Bundle?`：参数，保存的状态（`?` 表示可空类型）
    // Bundle 是 Android 系统的一个键值对集合类，用于在 Activity 之间传递数据
    override fun onCreate(savedInstanceState: Bundle?) {
        // - `super`：关键字，指向父类ComponentActivity
        // - `super.onCreate(savedInstanceState)`：调用父类的 `onCreate` 方法
        super.onCreate(savedInstanceState)

        // 检查并请求存储权限（Android 11+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            checkAndRequestStoragePermission()
        }

        // 创建应用的存储文件夹 ComicStorage
        createComicStorageFolder()

        // - `enableEdgeToEdge`：调用之前导入的 `enableEdgeToEdge` 函数
        // - 启用全屏显示模式
        // - 让应用内容延伸到状态栏和导航栏下方
        enableEdgeToEdge()
        // - `setContent`：是 Jetpack Compose 的入口函数 （类似 React 的 `render()` 或 Vue 的 `template`）
        // - 在这里定义整个 Activity(根组件) 的 UI
        setContent { ComicReaderTheme { MainScreen() } }
    }

    // 创建 ComicStorage 文件夹
    private fun createComicStorageFolder() {
        try {
            // /storage/emulated/0	外部存储根目录（主存储）
            val storageDir =
                    File(android.os.Environment.getExternalStorageDirectory(), "ComicStorage")
            android.util.Log.d("MainActivity", "尝试创建文件夹: ${storageDir.absolutePath}")
            android.util.Log.d("MainActivity", "文件夹是否存在: ${storageDir.exists()}")

            if (!storageDir.exists()) {
                val created = storageDir.mkdirs()
                android.util.Log.d("MainActivity", "mkdirs() 返回值: $created")

                // 验证文件夹是否真的创建成功
                val actuallyExists = storageDir.exists() && storageDir.isDirectory
                android.util.Log.d("MainActivity", "创建后文件夹是否存在: $actuallyExists")

                if (actuallyExists) {
                    android.util.Log.d(
                            "MainActivity",
                            "ComicStorage 文件夹创建成功: ${storageDir.absolutePath}"
                    )
                } else {
                    android.util.Log.e(
                            "MainActivity",
                            "ComicStorage 文件夹创建失败！路径: ${storageDir.absolutePath}"
                    )
                    android.util.Log.e(
                            "MainActivity",
                            "可能原因: Android 10+ 分区存储限制，需要 MANAGE_EXTERNAL_STORAGE 权限"
                    )
                }
            } else {
                android.util.Log.d(
                        "MainActivity",
                        "ComicStorage 文件夹已存在: ${storageDir.absolutePath}"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "创建 ComicStorage 文件夹失败", e)
        }
    }

    // 检查并请求存储权限（Android 11+）
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                android.util.Log.w("MainActivity", "需要 MANAGE_EXTERNAL_STORAGE 权限")
                android.util.Log.w("MainActivity", "请前往设置 -> 应用 -> Comic Reader -> 权限 -> 管理所有文件")
                try {
                    // 打开系统设置页面让用户手动赋予权限
                    val intent =
                            android.content.Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                            )
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "无法打开权限设置页面", e)
                }
            } else {
                android.util.Log.d("MainActivity", "已拥有 MANAGE_EXTERNAL_STORAGE 权限")
            }
        }
    }
}

// - `@Composable` 是一个注解（annotation）
// - 标记一个函数为 Composable 函数（类似 React 的函数组件标记）
// - 所有 Compose UI 组件都必须用 `@Composable` 标记
// - 只有 `@Composable` 函数才能调用其他 `@Composable` 函数
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "你好 $name!", modifier = modifier)
}

// - `@Preview` 是一个注解
// - 用于在 Android Studio 中预览 Composable 组件（类似浏览器的实时预览）
// - 快速查看 UI 效果，不需要运行整个应用
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ComicReaderTheme { Greeting("Android") }
}

// 底部导航栏的数据类
sealed class BottomNavItem(
        val title: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val route: String
) {
    object Home : BottomNavItem("首页", Icons.Default.Home, "home")
    object Profile : BottomNavItem("我的", Icons.Default.Person, "profile")
}

@Composable
fun MainScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val navItems = listOf(BottomNavItem.Home, BottomNavItem.Profile)

    Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                        modifier =
                                Modifier.drawBehind {
                                    val borderwidth = 7.dp.toPx()
                                    drawLine(
                                            color = Color.Black,
                                            start = Offset(0f, 0f), // 起点：左上角
                                            end = Offset(size.width, 0f), // 终点：右上角
                                            strokeWidth = borderwidth
                                    )
                                    //      |   └─ Float 类型的 0（y 坐标）
                                    //      └───── Float 类型的 0（x 坐标）
                                }
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) },
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index }
                        )
                    }
                }
            }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTabIndex) {
                0 -> HomeScreen()
                1 -> ProfileScreen()
            }
        }
    }
}

@Composable
fun Counter(name: String, originValue: Int = 0, modifier: Modifier = Modifier) {
    var count: Int by remember { mutableStateOf(originValue) }
    Column(modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$name : $originValue")
        Text(text = "Count : $count , 点击新增数值")
        Button(onClick = { count++ }) { Text("+") }
        Button(onClick = { count-- }) { Text("-") }
        Button(onClick = { count = originValue }) { Text("Reset") }
    }
    Text(text = "try it")
}
