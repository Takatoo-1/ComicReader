package com.example.comicreader.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 漫画数据模型
data class ComicItem(
        val id: Int, // 漫画ID
        val name: String, // 漫画名称
        val thumbnailPath: String?, // 缩略图路径
        val imageCount: Int, // 图片数量
        val folderPath: String // 文件夹路径
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var comicList by remember { mutableStateOf<List<ComicItem>>(emptyList()) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var selectedArchiveUri by remember { mutableStateOf<Uri?>(null) }
    var selectedComicItem by remember { mutableStateOf<ComicItem?>(null) }
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingImages by remember { mutableStateOf(false) }

    // 刷新漫画列表（从 ComicStorage 扫描）
    fun refreshComicListFromStorage(context: Context) {
        coroutineScope.launch {
            val items =
                    withContext(Dispatchers.IO) {
                        val result = mutableListOf<ComicItem>()
                        scanComicStorage(context) { item -> result.add(item) }
                        result
                    }
            // 在主线程更新 UI
            comicList = items
            Log.d("ComicReader", "从 ComicStorage 加载了 ${items.size} 个漫画文件夹")
        }
    }

    // 文件夹选择器
    val folderPickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                uri?.let {
                    selectedFolderUri = it
                    context.contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    // 获取文件夹名称
                    val folderName = getDisplayName(context.contentResolver, it) ?: "未知文件夹"
                    val displayName =
                            if (folderName.contains("/")) {
                                folderName.substringAfterLast("/")
                            } else {
                                folderName
                            }

                    // 在后台线程获取图片数量并检查重复
                    coroutineScope.launch {
                        val imageCount =
                                withContext(Dispatchers.IO) { getImageCountFromFolder(context, it) }

                        // 检查是否已存在相同名称和图片数量的文件夹
                        val isDuplicate =
                                comicList.any { item ->
                                    item.name == displayName && item.imageCount == imageCount
                                }

                        if (isDuplicate) {
                            // 如果重复，显示提示并终止，回退到主页
                            Toast.makeText(context, "该漫画文件夹已经添加到列表中了", Toast.LENGTH_SHORT).show()
                            Log.d("ComicReader", "文件夹已存在，跳过添加。名称: $displayName, 图片数量: $imageCount")
                            showAddMenu = false
                            return@launch
                        }

                        // 没有重复，继续复制文件夹到 ComicStorage
                        var copyCompleted = false
                        withContext(Dispatchers.IO) {
                            copyFolderToComicStorage(
                                    context = context,
                                    sourceUri = it,
                                    folderName = displayName,
                                    onProgress = { message -> Log.d("ComicReader", message) },
                                    onComplete = { targetFolder -> copyCompleted = true },
                                    onError = { e -> Log.e("ComicReader", "复制文件夹失败", e) }
                            )
                        }
                        // 复制完成后，在主线程刷新列表
                        if (copyCompleted) {
                            // 重新扫描 ComicStorage 并更新列表
                            val items =
                                    withContext(Dispatchers.IO) {
                                        val result = mutableListOf<ComicItem>()
                                        scanComicStorage(context) { item -> result.add(item) }
                                        result
                                    }
                            comicList = items
                            Log.d("ComicReader", "从 ComicStorage 加载了 ${items.size} 个漫画文件夹")
                        }
                    }
                }
                showAddMenu = false
            }

    // 文件选择器（用于压缩包）
    val filePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    selectedArchiveUri = it
                    // 在后台线程复制压缩包到 ComicStorage 并解压
                    coroutineScope.launch {
                        var copyCompleted = false
                        withContext(Dispatchers.IO) {
                            copyArchiveToComicStorage(
                                    context = context,
                                    sourceUri = it,
                                    onComplete = { copyCompleted = true },
                                    onError = { e -> Log.e("ComicReader", "复制压缩包失败", e) }
                            )
                        }
                        // 复制完成后，在主线程刷新列表
                        if (copyCompleted) {
                            // 重新扫描 ComicStorage 并更新列表
                            val items =
                                    withContext(Dispatchers.IO) {
                                        val result = mutableListOf<ComicItem>()
                                        scanComicStorage(context) { item -> result.add(item) }
                                        result
                                    }
                            comicList = items
                            Log.d("ComicReader", "从 ComicStorage 加载了 ${items.size} 个漫画文件夹")
                        }
                    }
                }
                showAddMenu = false
            }

    // 初始化时从 ComicStorage 加载所有漫画
    LaunchedEffect(Unit) { refreshComicListFromStorage(context) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFFFACA59)) // 黄色背景
        ) {
            // TopBar
            TopBar(
                    onSearchClick = { showSearchBar = !showSearchBar },
                    onAddClick = { showAddMenu = !showAddMenu },
                    showAddMenu = showAddMenu,
                    onSelectFolder = { folderPickerLauncher.launch(null) },
                    onSelectArchive = { filePickerLauncher.launch("application/zip") }
            )

            // 搜索框
            if (showSearchBar) {
                SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                // it 代表传入的 String 参数
                // 当 Lambda 表达式只有一个参数时，可以使用 it 作为该参数的隐式名称，无需显式声明。
                // onQueryChange = { newQuery: String ->
                //     searchQuery = newQuery
                // }
            }

            // 漫画列表
            val filteredList =
                    if (searchQuery.isBlank()) {
                        comicList
                    } else {
                        comicList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // itemsIndexed：LazyColumn 的扩展函数，用于遍历列表并渲染每个项目
                itemsIndexed(filteredList) { index, item ->
                    ComicListItem(
                            item = item,
                            index = index,
                            onClick = {
                                selectedComicItem = item
                                isLoadingImages = true
                                imagePaths = emptyList()
                                // 加载图片列表
                                loadImagePaths(context, item) { paths ->
                                    imagePaths = paths
                                    isLoadingImages = false
                                    // 如果加载失败，打印日志
                                    if (paths.isEmpty()) {
                                        Log.e(
                                                "ComicReader",
                                                "加载图片失败: ${item.name}, folderPath: ${item.folderPath}"
                                        )
                                    } else {
                                        Log.d(
                                                "ComicReader",
                                                "加载图片成功: ${item.name}, 共 ${paths.size} 张"
                                        )
                                    }
                                }
                            }
                    )
                }
            }
        }

        // 全屏图片浏览界面 - 使用 Box 覆盖在其他内容之上
        selectedComicItem?.let { item ->
            if (isLoadingImages) {
                // 加载中状态
                Box(
                        modifier =
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                ) { Text(text = "加载中...", color = Color.White, fontSize = 18.sp) }
            } else if (imagePaths.isNotEmpty()) {
                FullScreenImageViewer(
                        imagePaths = imagePaths,
                        comicName = item.name,
                        onClose = {
                            selectedComicItem = null
                            imagePaths = emptyList()
                            isLoadingImages = false
                        }
                )
            }
        }
    }
}

@Composable
fun TopBar(
        onSearchClick: () -> Unit,
        onAddClick: () -> Unit,
        showAddMenu: Boolean,
        onSelectFolder: () -> Unit,
        onSelectArchive: () -> Unit
) {
    Box {
        Surface(
                modifier =
                        Modifier.fillMaxWidth().drawBehind {
                            // 设定边框宽度
                            val strokeWidth = 7.dp.toPx()
                            // 绘制底部的线
                            drawLine(
                                    color = Color.Black,
                                    start = Offset(0f, size.height), // 起点：左下角
                                    end = Offset(size.width, size.height), // 终点：右下角
                                    strokeWidth = strokeWidth
                            )
                        },
                color = Color.White
        ) {
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(
                                            start = 24.dp,
                                            end = 8.dp,
                                            top = 12.dp,
                                            bottom = 12.dp
                                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "COMIC READER",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                )

                Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.Black
                        )
                    }

                    Box {
                        IconButton(onClick = onAddClick) {
                            Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加",
                                    tint = Color.Black
                            )
                        }

                        // 下拉菜单
                        DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { onAddClick() },
                                modifier =
                                        Modifier.background(Color.White)
                                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                    text = {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = null,
                                                    tint = Color.Black
                                            )
                                            Text(text = "Select Folder Path", color = Color.Black)
                                        }
                                    },
                                    onClick = { onSelectFolder() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                    text = {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                    imageVector = Icons.Default.Home,
                                                    contentDescription = null,
                                                    tint = Color.Black
                                            )
                                            Text(
                                                    text = "Select Archive (.zip/.cbz)",
                                                    color = Color.Black
                                            )
                                        }
                                    },
                                    onClick = { onSelectArchive() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(
            modifier =
                    Modifier.fillMaxWidth().drawBehind {
                        val strokeWidth = 3.dp.toPx()
                        drawLine(
                                color = Color.Black,
                                start = Offset(0f, 0f), // 起点：左下角
                                end = Offset(size.width, 0f), // 终点：右下角
                                strokeWidth = strokeWidth
                        )
                    },
            color = Color.White
    ) {
        TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索漫画...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                colors =
                        TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                        ),
                singleLine = true
        )
    }
}

@Composable
fun ComicListItem(item: ComicItem, index: Int, onClick: () -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth().height(120.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7E2AB)),
            border = BorderStroke(3.dp, Color.Black)
    ) {
        Row(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            Box(
                    modifier =
                            Modifier.size(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                item.thumbnailPath?.let { path ->
                    AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(path).build(),
                            contentDescription = "缩略图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                    )
                }
            }

            // 内容信息
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = "No. ${String.format("%03d", item.id)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                )
                Text(
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                )
                Text(
                        text = "共${item.imageCount}张图片",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                )
            }

            // 箭头图标
            Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "打开",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
            )
        }
    }
}

// 获取文件显示名称
fun getDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
    return try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            } else {
                null
            }
        }
    } catch (e: Exception) {
        uri.lastPathSegment
    }
}

// 判断文件名是否为图片文件（通用方法）
fun isImageFile(fileName: String?): Boolean {
    return fileName != null &&
            (fileName.endsWith(".jpg", ignoreCase = true) ||
                    fileName.endsWith(".jpeg", ignoreCase = true) ||
                    fileName.endsWith(".png", ignoreCase = true) ||
                    fileName.endsWith(".webp", ignoreCase = true))
}

// 获取文件夹中的图片数量
fun getImageCountFromFolder(context: Context, uri: Uri): Int {
    return try {
        val contentResolver = context.contentResolver
        var imageCount = 0

        // 获取树URI下的所有子文档
        val childrenUri =
                android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        uri,
                        android.provider.DocumentsContract.getTreeDocumentId(uri)
                )

        contentResolver.query(
                        childrenUri,
                        arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null
                )
                ?.use { cursor ->
                    val nameColumn =
                            cursor.getColumnIndexOrThrow(
                                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                            )

                    while (cursor.moveToNext()) {
                        val fileName = cursor.getString(nameColumn)

                        // 使用通用方法判断是否为图片文件
                        if (isImageFile(fileName)) {
                            imageCount++
                        }
                    }
                }

        imageCount
    } catch (e: Exception) {
        Log.e("ComicReader", "获取图片数量失败", e)
        0
    }
}

// 复制压缩包到 ComicStorage 并解压
fun copyArchiveToComicStorage(
        context: Context,
        sourceUri: Uri,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit = { it.printStackTrace() }
) {
    try {
        val storageDir = ensureComicStorageExists()
        val archiveName =
                getDisplayName(context.contentResolver, sourceUri)
                        ?.removeSuffix(".zip")
                        ?.removeSuffix(".cbz")
                        ?: "未知压缩包"

        // 创建目标文件夹
        var targetFolder = File(storageDir, archiveName)
        var counter = 1
        while (targetFolder.exists()) {
            targetFolder = File(storageDir, "$archiveName($counter)")
            counter++
        }
        targetFolder.mkdirs()

        // 复制并解压压缩包
        val inputStream = context.contentResolver.openInputStream(sourceUri)
        val tempFile = File.createTempFile("comic_", ".zip", context.cacheDir)
        inputStream?.use { it.copyTo(tempFile.outputStream()) }

        val zipFile = ZipFile(tempFile)
        val entries = zipFile.entries().toList()

        val imageEntries =
                entries
                        .filter { entry ->
                            !entry.isDirectory &&
                                    (entry.name.endsWith(".jpg", ignoreCase = true) ||
                                            entry.name.endsWith(".jpeg", ignoreCase = true) ||
                                            entry.name.endsWith(".png", ignoreCase = true) ||
                                            entry.name.endsWith(".webp", ignoreCase = true))
                        }
                        .sortedBy { it.name }

        // 提取所有图片到目标文件夹
        imageEntries.forEach { entry ->
            zipFile.getInputStream(entry).use { input ->
                val fileName = File(entry.name).name // 获取文件名（去除路径）
                val targetFile = File(targetFolder, fileName)
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        zipFile.close()
        tempFile.delete()
        onComplete()
    } catch (e: Exception) {
        onError(e)
    }
}

// 加载图片路径列表
fun loadImagePaths(context: Context, item: ComicItem, onPathsLoaded: (List<String>) -> Unit) {
    try {
        val paths = mutableListOf<String>()

        when {
            // 本地文件路径（ComicStorage 中的文件夹）
            item.folderPath.startsWith("/") -> {
                val folder = File(item.folderPath)
                if (folder.exists() && folder.isDirectory) {
                    val imageFiles =
                            folder.listFiles { file ->
                                file.isFile &&
                                        (file.name.endsWith(".jpg", ignoreCase = true) ||
                                                file.name.endsWith(".jpeg", ignoreCase = true) ||
                                                file.name.endsWith(".png", ignoreCase = true) ||
                                                file.name.endsWith(".webp", ignoreCase = true))
                            }
                    if (imageFiles != null) {
                        imageFiles.sortedBy { it.name }.forEach { file ->
                            paths.add(file.absolutePath)
                        }
                    }
                }
            }
            // Assets 文件夹
            item.folderPath.startsWith("assets/") -> {
                val assetManager = context.assets
                val folderName = item.folderPath.removePrefix("assets/")
                val files = assetManager.list(folderName)
                if (files != null) {
                    files
                            .filter { file ->
                                file.endsWith(".jpg", ignoreCase = true) ||
                                        file.endsWith(".jpeg", ignoreCase = true) ||
                                        file.endsWith(".png", ignoreCase = true) ||
                                        file.endsWith(".webp", ignoreCase = true)
                            }
                            .sorted()
                            .forEach { fileName ->
                                paths.add("file:///android_asset/$folderName/$fileName")
                            }
                }
            }
            // URI 文件夹（通过 DocumentsContract）
            item.folderPath.startsWith("content://") -> {
                val uri = Uri.parse(item.folderPath)
                val contentResolver = context.contentResolver
                val imageUris = mutableListOf<Uri>()

                val childrenUri =
                        android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                                uri,
                                android.provider.DocumentsContract.getTreeDocumentId(uri)
                        )

                contentResolver.query(
                                childrenUri,
                                arrayOf(
                                        android.provider.DocumentsContract.Document
                                                .COLUMN_DOCUMENT_ID,
                                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                                ),
                                null,
                                null,
                                null
                        )
                        ?.use { cursor ->
                            val idColumn =
                                    cursor.getColumnIndexOrThrow(
                                            android.provider.DocumentsContract.Document
                                                    .COLUMN_DOCUMENT_ID
                                    )
                            val mimeTypeColumn =
                                    cursor.getColumnIndexOrThrow(
                                            android.provider.DocumentsContract.Document
                                                    .COLUMN_MIME_TYPE
                                    )

                            while (cursor.moveToNext()) {
                                val documentId = cursor.getString(idColumn)
                                val mimeType = cursor.getString(mimeTypeColumn)

                                if (mimeType?.startsWith("image/") == true) {
                                    val documentUri =
                                            android.provider.DocumentsContract
                                                    .buildDocumentUriUsingTree(uri, documentId)
                                    imageUris.add(documentUri)
                                }
                            }
                        }

                imageUris.sortedBy { it.toString() }.forEach { uri -> paths.add(uri.toString()) }
            }
            // ZIP 压缩包
            else -> {
                try {
                    val uri = Uri.parse(item.folderPath)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File.createTempFile("comic_", ".zip", context.cacheDir)
                    inputStream?.use { it.copyTo(tempFile.outputStream()) }

                    val zipFile = ZipFile(tempFile)
                    val entries = zipFile.entries().toList()

                    val imageEntries =
                            entries
                                    .filter { entry ->
                                        !entry.isDirectory &&
                                                (entry.name.endsWith(".jpg", ignoreCase = true) ||
                                                        entry.name.endsWith(
                                                                ".jpeg",
                                                                ignoreCase = true
                                                        ) ||
                                                        entry.name.endsWith(
                                                                ".png",
                                                                ignoreCase = true
                                                        ) ||
                                                        entry.name.endsWith(
                                                                ".webp",
                                                                ignoreCase = true
                                                        ))
                                    }
                                    .sortedBy { it.name }

                    // 提取所有图片到临时文件
                    imageEntries.forEachIndexed { index, entry ->
                        zipFile.getInputStream(entry).use { input ->
                            val imageFile =
                                    File(
                                            context.cacheDir,
                                            "comic_${item.id}_${index}_${entry.name}"
                                    )
                            imageFile.outputStream().use { output -> input.copyTo(output) }
                            paths.add(imageFile.absolutePath)
                        }
                    }

                    zipFile.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        onPathsLoaded(paths)
    } catch (e: Exception) {
        e.printStackTrace()
        onPathsLoaded(emptyList())
    }
}

// 全屏图片浏览界面
@Composable
fun FullScreenImageViewer(imagePaths: List<String>, comicName: String, onClose: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { imagePaths.size }, initialPage = 0)
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 图片浏览区域
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                        model = ImageRequest.Builder(context).data(imagePaths[page]).build(),
                        contentDescription = "图片 ${page + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                )
            }
        }

        // 顶部栏（标题和关闭按钮）
        Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.7f)
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "$comicName (${pagerState.currentPage + 1}/${imagePaths.size})",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                    )
                }
            }
        }
    }
}

// ==================== ComicStorage 管理函数 ====================

// 获取 ComicStorage 文件夹路径
fun getComicStoragePath(): File {
    return File(Environment.getExternalStorageDirectory(), "ComicStorage")
}

// 确保 ComicStorage 文件夹存在
fun ensureComicStorageExists(): File {
    val storageDir = getComicStoragePath()
    if (!storageDir.exists()) {
        val created = storageDir.mkdirs()
        val actuallyExists = storageDir.exists() && storageDir.isDirectory

        if (actuallyExists) {
            Log.d("ComicReader", "ComicStorage 文件夹创建成功: ${storageDir.absolutePath}")
        } else {
            Log.e("ComicReader", "ComicStorage 文件夹创建失败！mkdirs()=$created, exists()=$actuallyExists")
            Log.e("ComicReader", "路径: ${storageDir.absolutePath}")
            Log.e("ComicReader", "可能原因: Android 10+ 分区存储限制，需要 MANAGE_EXTERNAL_STORAGE 权限")
        }
    }
    return storageDir
}

// 复制文件夹内容到 ComicStorage
fun copyFolderToComicStorage(
        context: Context,
        sourceUri: Uri,
        folderName: String,
        onProgress: (String) -> Unit = {},
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit = { it.printStackTrace() }
) {
    try {
        val storageDir = ensureComicStorageExists()
        // 创建目标文件夹，使用文件夹名称（如果已存在则添加序号）
        var targetFolder = File(storageDir, folderName)
        var counter = 1
        while (targetFolder.exists()) {
            targetFolder = File(storageDir, "$folderName($counter)")
            counter++
        }
        targetFolder.mkdirs()

        onProgress("正在复制文件夹: $folderName")

        val contentResolver = context.contentResolver
        val imageUris = mutableListOf<Uri>()

        // 获取源文件夹中的所有图片
        val childrenUri =
                android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        sourceUri,
                        android.provider.DocumentsContract.getTreeDocumentId(sourceUri)
                )

        contentResolver.query(
                        childrenUri,
                        arrayOf(
                                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        ),
                        null,
                        null,
                        null
                )
                ?.use { cursor ->
                    val idColumn =
                            cursor.getColumnIndexOrThrow(
                                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
                            )
                    val mimeTypeColumn =
                            cursor.getColumnIndexOrThrow(
                                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                            )
                    val nameColumn =
                            cursor.getColumnIndexOrThrow(
                                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                            )

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(idColumn)
                        val mimeType = cursor.getString(mimeTypeColumn)
                        val fileName = cursor.getString(nameColumn)

                        if (mimeType?.startsWith("image/") == true) {
                            val documentUri =
                                    android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                            sourceUri,
                                            documentId
                                    )
                            imageUris.add(documentUri)

                            // 复制文件
                            try {
                                contentResolver.openInputStream(documentUri)?.use { input ->
                                    val targetFile = File(targetFolder, fileName)
                                    targetFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            } catch (e: Exception) {
                                Log.e("ComicReader", "复制文件失败: $fileName", e)
                            }
                        }
                    }
                }

        onProgress("复制完成: ${imageUris.size} 张图片")
        onComplete(targetFolder)
    } catch (e: Exception) {
        onError(e)
    }
}

// 从 ComicStorage 扫描所有子文件夹并生成 ComicItem
fun scanComicStorage(context: Context, onItemParsed: (ComicItem) -> Unit) {
    try {
        val storageDir = getComicStoragePath()
        if (!storageDir.exists() || !storageDir.isDirectory) {
            Log.d("ComicReader", "ComicStorage 文件夹不存在或不是目录")
            return
        }

        val subFolders = storageDir.listFiles { file -> file.isDirectory }
        if (subFolders == null || subFolders.isEmpty()) {
            Log.d("ComicReader", "ComicStorage 中没有子文件夹")
            return
        }

        var itemId = 1
        subFolders.forEach { folder ->
            val imageFiles = folder.listFiles { file -> file.isFile && isImageFile(file.name) }

            if (imageFiles != null && imageFiles.isNotEmpty()) {
                val sortedFiles = imageFiles.sortedBy { it.name }
                val thumbnailFile = sortedFiles.first()

                val item =
                        ComicItem(
                                id = itemId++,
                                name = folder.name,
                                thumbnailPath = thumbnailFile.absolutePath,
                                imageCount = sortedFiles.size,
                                folderPath = folder.absolutePath
                        )
                onItemParsed(item)
            }
        }
    } catch (e: Exception) {
        Log.e("ComicReader", "扫描 ComicStorage 失败", e)
    }
}
