# Kotlin 小括号 `()` vs 大括号 `{}` 详解

## 📋 快速判断规则

### ✅ 使用小括号 `()` 的情况：
1. **普通函数调用**：传递值、对象、表达式
   ```kotlin
   HomeScreen()                    // 调用函数，无参数
   Text("Hello")                   // 调用函数，传递字符串
   Modifier.fillMaxSize()          // 调用函数，返回 Modifier 对象
   selectedTabIndex == index       // 表达式，返回布尔值
   ```

### ✅ 使用大括号 `{}` 的情况：
1. **Lambda 表达式参数**：传递代码块
   ```kotlin
   bottomBar = { NavigationBar { } }  // Lambda 参数
   onClick = { count++ }              // Lambda 参数
   ```

2. **尾随 Lambda**：最后一个参数是 Lambda 时，可以移到括号外
   ```kotlin
   NavigationBar {  // 等价于 NavigationBar(content = { ... })
       // 内容
   }
   ```

## 🔍 详细对比

### 示例 1：普通参数 vs Lambda 参数

```kotlin
NavigationBarItem(
    selected = selectedTabIndex == index,  // ← 小括号：普通参数（布尔值）
    onClick = { selectedTabIndex = index } // ← 大括号：Lambda 参数（代码块）
)
```

**为什么？**
- `selected` 需要的是 `Boolean` 类型 → 用 `()` 传递值
- `onClick` 需要的是 `() -> Unit` 类型 → 用 `{}` 传递代码块

### 示例 2：尾随 Lambda 语法糖

```kotlin
// 标准写法（完整形式）
Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = { NavigationBar { } },
    content = { padding -> Box { } }  // content 在括号内
)

// 尾随 Lambda 写法（简化形式，更常用）
Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = { NavigationBar { } }
) { padding ->  // ← content 移到括号外，更清晰
    Box { }
}
```

**为什么？**
- Kotlin 允许最后一个 Lambda 参数移到括号外
- 这样代码更易读，特别是当 Lambda 很长时

### 示例 3：混合使用

```kotlin
Box(modifier = Modifier.padding(innerPadding)) {  // ← 小括号：普通参数
    // 大括号：尾随 Lambda（content 参数）
    Text("Hello")
}
```

**等价写法：**
```kotlin
Box(
    modifier = Modifier.padding(innerPadding),
    content = { Text("Hello") }  // 标准写法
)
```

## 🎯 记忆技巧

1. **传值/对象** → 用 `()`
   - `Text("Hello")` - 传字符串
   - `Modifier.fillMaxSize()` - 传 Modifier 对象
   - `selectedTabIndex == index` - 传布尔值

2. **传代码块** → 用 `{}`
   - `onClick = { ... }` - 传点击处理代码
   - `bottomBar = { ... }` - 传 UI 构建代码
   - `icon = { Icon(...) }` - 传图标构建代码

3. **最后一个 Lambda** → 可以移到括号外（尾随 Lambda）
   ```kotlin
   // 这两种写法等价：
   NavigationBar(content = { ... })
   NavigationBar { ... }  // 更简洁
   ```

## 📚 类比理解

### JavaScript/TypeScript 对比：
```javascript
// JavaScript
<Button 
    disabled={isDisabled}        // ← 传值，用 {}
    onClick={() => handleClick()} // ← 传函数，用 {}
/>

// Kotlin
Button(
    enabled = !isDisabled,        // ← 传值，用 ()
    onClick = { handleClick() }   // ← 传 Lambda，用 {}
)
```

### React 对比：
```jsx
// React
<Scaffold 
    modifier={Modifier.fillMaxSize()}
    bottomBar={() => <NavigationBar />}
/>

// Kotlin Compose
Scaffold(
    modifier = Modifier.fillMaxSize(),  // ← 传值，用 ()
    bottomBar = { NavigationBar { } }   // ← 传 Lambda，用 {}
)
```

## ⚠️ 常见错误

### ❌ 错误示例：
```kotlin
// 错误：把普通参数写成 Lambda
Text({ "Hello" })  // ❌ Text 需要 String，不是 Lambda

// 错误：把 Lambda 写成普通参数
onClick = selectedTabIndex = index  // ❌ onClick 需要 Lambda，不是赋值表达式
```

### ✅ 正确示例：
```kotlin
Text("Hello")                      // ✅ 传字符串
onClick = { selectedTabIndex = index }  // ✅ 传 Lambda
```

## 🔗 相关概念

- **Lambda 表达式**：`{ 参数 -> 代码 }`
- **尾随 Lambda**：最后一个 Lambda 参数可以移到括号外
- **Composable 函数**：`@Composable fun` 标记的函数，返回 UI
- **高阶函数**：接受函数作为参数的函数

