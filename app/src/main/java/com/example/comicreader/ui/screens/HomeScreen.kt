package com.example.comicreader.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import android.content.Context
import android.content.ContentResolver
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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

// 漫画数据模型
data class ComicItem(
    val id: Int,
    val name: String,
    val thumbnailPath: String?,
    val imageCount: Int,
    val folderPath: String
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var comicList by remember { mutableStateOf<List<ComicItem>>(emptyList()) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var selectedArchiveUri by remember { mutableStateOf<Uri?>(null) }
    var selectedComicItem by remember { mutableStateOf<ComicItem?>(null) }
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingImages by remember { mutableStateOf(false) }

    // 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            selectedFolderUri = it
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            parseFolder(context, it) { item ->
                comicList = comicList + item
            }
        }
        showAddMenu = false
    }

    // 文件选择器（用于压缩包）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedArchiveUri = it
            parseArchive(context, it) { item ->
                comicList = comicList + item
            }
        }
        showAddMenu = false
    }

    // 初始化时加载 res/example 文件夹
    LaunchedEffect(Unit) {
        loadExampleFolder(context, comicList.size) { item ->
            comicList = comicList + item
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFACA59)) // 黄色背景
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
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
                // it 代表传入的 String 参数
                // 当 Lambda 表达式只有一个参数时，可以使用 it 作为该参数的隐式名称，无需显式声明。
                // onQueryChange = { newQuery: String -> 
                //     searchQuery = newQuery 
                // }
            }

            // 漫画列表
            val filteredList = if (searchQuery.isBlank()) {
                comicList
            } else {
                comicList.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 30.dp),
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
                                    Log.e("ComicReader", "加载图片失败: ${item.name}, folderPath: ${item.folderPath}")
                                } else {
                                    Log.d("ComicReader", "加载图片成功: ${item.name}, 共 ${paths.size} 张")
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载中...",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
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
            modifier = Modifier.fillMaxWidth().drawBehind {
                // 设定边框宽度
                val strokeWidth = 7.dp.toPx()
                // 绘制底部的线
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, size.height),           // 起点：左下角
                    end = Offset(size.width, size.height),     // 终点：右下角
                    strokeWidth = strokeWidth
                )
            },
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
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
                            modifier = Modifier
                                .background(Color.White)
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
                                        Text(
                                            text = "Select Folder Path",
                                            color = Color.Black
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectFolder()
                                }
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
                                onClick = {
                                    onSelectArchive()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().drawBehind {
            val strokeWidth = 3.dp.toPx()
            drawLine(
                color = Color.Black,
                start = Offset(0f, 0f),           // 起点：左下角
                end = Offset(size.width, 0f),     // 终点：右下角
                strokeWidth = strokeWidth
            )
        },
        color = Color.White
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索漫画...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            singleLine = true
        )
    }
}

@Composable
fun ComicListItem(
    item: ComicItem,
    index: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7E2AB)
        ),
        border = BorderStroke(3.dp, Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                item.thumbnailPath?.let { path ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(path)
                            .build(),
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

// 加载 assets/example 文件夹作为示例数据
fun loadExampleFolder(
    context: Context,
    currentListSize: Int,
    onItemParsed: (ComicItem) -> Unit
) {
    try {
        val assetManager = context.assets
        val imagePaths = mutableListOf<String>()

        // 从 assets/example 读取所有图片文件
        val files = assetManager.list("example")
        if (files != null && files.isNotEmpty()) {
            // 过滤出图片文件并按名称排序
            files.filter { file ->
                file.endsWith(".jpg", ignoreCase = true) ||
                        file.endsWith(".jpeg", ignoreCase = true) ||
                        file.endsWith(".png", ignoreCase = true) ||
                        file.endsWith(".webp", ignoreCase = true)
            }.sorted().forEach { fileName ->
                // assets 文件的路径格式：file:///android_asset/example/文件名
                val path = "file:///android_asset/example/$fileName"
                imagePaths.add(path)
            }
        }

        // 如果找到图片文件，创建 ComicItem
        if (imagePaths.isNotEmpty()) {
            val thumbnailPath = imagePaths.first()
            val item = ComicItem(
                id = currentListSize + 1,
                name = "Example Comic",
                thumbnailPath = thumbnailPath,
                imageCount = imagePaths.size,
                folderPath = "assets/example"
            )
            onItemParsed(item)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 解析文件夹
fun parseFolder(
    context: Context,
    uri: Uri,
    onItemParsed: (ComicItem) -> Unit
) {
    try {
        val contentResolver = context.contentResolver
        val folderName = getDisplayName(contentResolver, uri) ?: "未知文件夹"

        val imageUris = mutableListOf<Uri>()

        // 获取树URI下的所有子文档
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        )

        contentResolver.query(
            childrenUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeTypeColumn =
                cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                if (mimeType?.startsWith("image/") == true) {
                    val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        documentId
                    )
                    imageUris.add(documentUri)
                }
            }
        }

        if (imageUris.isNotEmpty()) {
            // 排序以确保顺序
            val sortedUris = imageUris.sortedBy { it.toString() }
            val thumbnailUri = sortedUris.first()
            val item = ComicItem(
                id = System.currentTimeMillis().toInt(),
                name = folderName,
                thumbnailPath = thumbnailUri.toString(),
                imageCount = sortedUris.size,
                folderPath = uri.toString()
            )
            onItemParsed(item)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 解析压缩包
fun parseArchive(
    context: Context,
    uri: Uri,
    onItemParsed: (ComicItem) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("comic_", ".zip", context.cacheDir)
        inputStream?.use { it.copyTo(tempFile.outputStream()) }

        val zipFile = ZipFile(tempFile)
        val entries = zipFile.entries().toList()

        val imageEntries = entries.filter { entry ->
            !entry.isDirectory && (
                    entry.name.endsWith(".jpg", ignoreCase = true) ||
                            entry.name.endsWith(".jpeg", ignoreCase = true) ||
                            entry.name.endsWith(".png", ignoreCase = true) ||
                            entry.name.endsWith(".webp", ignoreCase = true)
                    )
        }.sortedBy { it.name }

        if (imageEntries.isNotEmpty()) {
            val archiveName = getDisplayName(context.contentResolver, uri)?.removeSuffix(".zip")
                ?.removeSuffix(".cbz") ?: "未知压缩包"

            // 提取第一张图片作为缩略图
            val firstImage = imageEntries.first()
            zipFile.getInputStream(firstImage).use { input ->
                val thumbnailFile = File(context.cacheDir, "thumb_${firstImage.name}")
                thumbnailFile.outputStream().use { output ->
                    input.copyTo(output)
                }

                val item = ComicItem(
                    id = System.currentTimeMillis().toInt(),
                    name = archiveName,
                    thumbnailPath = thumbnailFile.absolutePath,
                    imageCount = imageEntries.size,
                    folderPath = uri.toString()
                )
                onItemParsed(item)
            }
        }

        zipFile.close()
    } catch (e: Exception) {
        e.printStackTrace()
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

// 加载图片路径列表
fun loadImagePaths(
    context: Context,
    item: ComicItem,
    onPathsLoaded: (List<String>) -> Unit
) {
    try {
        val paths = mutableListOf<String>()

        when {
            // Assets 文件夹
            item.folderPath.startsWith("assets/") -> {
                val assetManager = context.assets
                val folderName = item.folderPath.removePrefix("assets/")
                val files = assetManager.list(folderName)
                if (files != null) {
                    files.filter { file ->
                        file.endsWith(".jpg", ignoreCase = true) ||
                                file.endsWith(".jpeg", ignoreCase = true) ||
                                file.endsWith(".png", ignoreCase = true) ||
                                file.endsWith(".webp", ignoreCase = true)
                    }.sorted().forEach { fileName ->
                        paths.add("file:///android_asset/$folderName/$fileName")
                    }
                }
            }
            // URI 文件夹（通过 DocumentsContract）
            item.folderPath.startsWith("content://") -> {
                val uri = Uri.parse(item.folderPath)
                val contentResolver = context.contentResolver
                val imageUris = mutableListOf<Uri>()

                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    uri,
                    android.provider.DocumentsContract.getTreeDocumentId(uri)
                )

                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idColumn =
                        cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeTypeColumn =
                        cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(idColumn)
                        val mimeType = cursor.getString(mimeTypeColumn)

                        if (mimeType?.startsWith("image/") == true) {
                            val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                uri,
                                documentId
                            )
                            imageUris.add(documentUri)
                        }
                    }
                }

                imageUris.sortedBy { it.toString() }.forEach { uri ->
                    paths.add(uri.toString())
                }
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

                    val imageEntries = entries.filter { entry ->
                        !entry.isDirectory && (
                                entry.name.endsWith(".jpg", ignoreCase = true) ||
                                        entry.name.endsWith(".jpeg", ignoreCase = true) ||
                                        entry.name.endsWith(".png", ignoreCase = true) ||
                                        entry.name.endsWith(".webp", ignoreCase = true)
                                )
                    }.sortedBy { it.name }

                    // 提取所有图片到临时文件
                    imageEntries.forEachIndexed { index, entry ->
                        zipFile.getInputStream(entry).use { input ->
                            val imageFile = File(context.cacheDir, "comic_${item.id}_${index}_${entry.name}")
                            imageFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
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
fun FullScreenImageViewer(
    imagePaths: List<String>,
    comicName: String,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { imagePaths.size }, initialPage = 0)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 图片浏览区域
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imagePaths[page])
                        .build(),
                    contentDescription = "图片 ${page + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 顶部栏（标题和关闭按钮）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
