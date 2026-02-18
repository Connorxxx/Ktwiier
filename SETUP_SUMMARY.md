# Kwitter KMP 项目依赖配置总结

## 配置完成情况 ✅

所有依赖已成功配置并通过 Gradle 同步。

### 配置的依赖版本

| 依赖 | 版本 | 说明 |
|-----|------|------|
| Kotlin | 2.3.0 | 最新稳定版本 |
| KSP | 2.3.4 | 与 Kotlin 2.3.0 兼容 |
| kotlinx-serialization | 1.8.0 | JSON + ProtoBuf |
| kotlinx-coroutines | 1.10.1 | 异步编程 |
| DataStore | 1.2.0-alpha01 | KMP 版本 |
| Room | 2.8.4 | 最新稳定版，KMP 支持 |
| SQLite | 2.6.2 | Room 依赖 |
| Arrow | 2.1.0 | 函数式编程 |
| Molecule | 2.0.0 | 状态管理 |
| Koin | 4.1.0 / 2.0.0 | 依赖注入 |
| Navigation | 2.9.0-alpha05 | Compose Navigation KMP |
| Ktor | 3.0.3 | HTTP 客户端 |

### 项目结构

```
Kwitter/
├── gradle/
│   └── libs.versions.toml          # 版本目录配置
├── composeApp/
│   ├── build.gradle.kts            # 应用构建配置
│   ├── schemas/                    # Room schema 导出目录
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/com/connor/kwitter/
│       │       ├── KwitterApp.kt           # Koin 初始化
│       │       ├── di/
│       │       │   ├── AppModule.kt        # DI 配置
│       │       │   └── PlatformModule.kt   # 平台 expect
│       │       ├── data/
│       │       │   ├── database/           # Room
│       │       │   │   ├── AppDatabase.kt
│       │       │   │   └── DatabaseBuilder.kt (expect)
│       │       │   └── datastore/          # DataStore
│       │       │       ├── DataStoreManager.kt
│       │       │       └── DataStoreFactory.kt (expect)
│       │       ├── navigation/
│       │       │   └── AppNavigation.kt    # Navigation 配置
│       │       ├── ui/
│       │       │   └── MoleculeExample.kt  # Molecule 示例
│       │       └── utils/
│       │           └── ArrowExtensions.kt  # Arrow 工具
│       ├── androidMain/
│       │   └── kotlin/com/connor/kwitter/
│       │       ├── di/
│       │       │   └── PlatformModule.android.kt
│       │       └── data/
│       │           ├── database/
│       │           │   └── DatabaseBuilder.android.kt
│       │           └── datastore/
│       │               └── DataStoreFactory.android.kt
│       └── iosMain/
│           └── kotlin/com/connor/kwitter/
│               ├── di/
│               │   └── PlatformModule.ios.kt
│               └── data/
│                   ├── database/
│                   │   └── DatabaseBuilder.ios.kt
│                   └── datastore/
│                       └── DataStoreFactory.ios.kt
├── build.gradle.kts                # 根项目配置
└── DEPENDENCIES.md                 # 详细依赖文档
```

## 核心功能示例

### 1. 依赖注入 (Koin)

```kotlin
// 初始化 Koin
fun main() {
    initKoin()
}

// 在 Composable 中使用
@Composable
fun MyScreen() {
    val viewModel = koinViewModel<MyViewModel>()
    val httpClient: HttpClient = koinInject()
}
```

### 2. 数据持久化

**DataStore (轻量级键值对)**:
```kotlin
val dataStoreManager: DataStoreManager = get()
dataStoreManager.setLoginStatus(true)
```

**Room (关系型数据库)**:
```kotlin
@Entity
data class Tweet(
    @PrimaryKey val id: String,
    val content: String,
    val authorId: String
)

@Dao
interface TweetDao {
    @Query("SELECT * FROM Tweet")
    fun getAllTweets(): Flow<List<Tweet>>
}
```

### 3. 网络请求 (Ktor)

```kotlin
val client: HttpClient = get()

suspend fun fetchTweets(): List<Tweet> {
    return client.get("https://api.kwitter.com/tweets").body()
}
```

### 4. 错误处理 (Arrow)

```kotlin
suspend fun fetchUserSafely(id: String): Either<Error, User> =
    runCatchingEither {
        api.getUser(id)
    }

// 使用
fetchUserSafely("123").fold(
    ifLeft = { error -> /* 处理错误 */ },
    ifRight = { user -> /* 使用数据 */ }
)
```

### 5. 导航 (Navigation)

```kotlin
@Serializable
data class ProfileRoute(val userId: String)

// 导航
navController.navigate(ProfileRoute(userId = "123"))

// 接收参数
composable<ProfileRoute> { backStackEntry ->
    val route = backStackEntry.toRoute<ProfileRoute>()
    ProfileScreen(userId = route.userId)
}
```

### 6. 状态管理 (Molecule)

```kotlin
@Composable
fun TweetListPresenter(
    events: Flow<TweetEvent>
): TweetListState {
    var tweets by remember { mutableStateOf(emptyList<Tweet>()) }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is TweetEvent.LoadTweets -> {
                    tweets = repository.getTweets()
                }
            }
        }
    }

    return TweetListState(tweets)
}
```

## 验证配置

构建项目验证配置:
```bash
# 清理并构建
./gradlew clean build

# 生成 KSP 代码 (Room, Arrow Optics)
./gradlew kspCommonMainKotlinMetadata

# Android 平台
./gradlew :composeApp:assembleDebug

# iOS 平台
./gradlew :composeApp:linkDebugFrameworkIosArm64
```

## 已知警告

构建时可能出现的警告（不影响功能）:
1. AGP 9.0 与 KMP plugin 兼容性警告 - 正常，等待 AGP 10.0
2. 部分 gradle.properties 选项被标记为 deprecated - 正常

## 下一步建议

1. **定义数据模型**:
   - 创建 Entity 类（Room）
   - 创建 DTO 类（Ktor）
   - 添加 @Serializable 注解

2. **实现 Repository 层**:
   - 本地数据源（Room + DataStore）
   - 远程数据源（Ktor）
   - 使用 Arrow 进行错误处理

3. **构建 UI 层**:
   - 创建 Screen Composables
   - 使用 Navigation 配置路由
   - 使用 Koin 注入 ViewModel

4. **添加业务逻辑**:
   - 使用 Molecule 管理复杂状态
   - 使用 Coroutines Flow 处理数据流
   - 使用 Arrow Resilience 添加重试逻辑

## 参考资源

- [Room KMP 文档](https://developer.android.com/kotlin/multiplatform/room)
- [Ktor 文档](https://ktor.io/)
- [Koin 文档](https://insert-koin.io/)
- [Arrow 文档](https://arrow-kt.io/)
- [Molecule GitHub](https://github.com/cashapp/molecule)
- [Navigation Compose 文档](https://developer.android.com/jetpack/compose/navigation)
- [KSP Release Notes](https://github.com/google/ksp/releases)

## 开发建议

### 架构模式
建议使用 **Clean Architecture** + **MVI/MVVM**:
```
UI Layer (Compose + Navigation)
    ↓
Presentation Layer (ViewModel/Presenter + Molecule)
    ↓
Domain Layer (Use Cases + Arrow)
    ↓
Data Layer (Repository + Room + Ktor + DataStore)
```

### 错误处理
统一使用 Arrow Either:
```kotlin
sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class DatabaseError(val message: String) : AppError()
    data class ValidationError(val message: String) : AppError()
}

typealias Result<T> = Either<AppError, T>
```

### 依赖注入分层
```kotlin
val dataModule = module { /* Room, Ktor, DataStore */ }
val domainModule = module { /* Use Cases, Repositories */ }
val presentationModule = module { /* ViewModels */ }
```

## 配置文件说明

### libs.versions.toml
包含所有依赖版本的集中管理文件，便于维护和更新。

### build.gradle.kts
- **根目录**: 声明所有 plugins
- **composeApp**: 配置 KMP 目标平台、添加依赖、配置 KSP

### gradle.properties
项目级别的 Gradle 配置，优化构建性能。

---

所有依赖已配置完成，可以开始开发了！🚀
