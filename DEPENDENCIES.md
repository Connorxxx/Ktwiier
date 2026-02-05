# Kwitter - KMP 依赖配置说明

## 已配置的依赖

### 1. Kotlinx Serialization (Protobuf)
- **版本**: 1.8.0
- **包含**:
  - `kotlinx-serialization-core`
  - `kotlinx-serialization-json`
  - `kotlinx-serialization-protobuf`
- **用途**: 数据序列化（JSON、ProtoBuf）
- **配置**: 已在 `plugins` 中添加 `kotlinSerialization`

```kotlin
@Serializable
data class User(
    val id: String,
    val name: String
)

// JSON
val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// ProtoBuf
val protobuf = ProtoBuf {
    encodeDefaults = true
}
```

### 2. Coroutines
- **版本**: 1.10.1
- **包含**:
  - `kotlinx-coroutines-core` (commonMain)
  - `kotlinx-coroutines-android` (androidMain)
  - `kotlinx-coroutines-test` (commonTest)
- **用途**: 异步编程、并发处理

```kotlin
suspend fun fetchData() {
    coroutineScope {
        val data = async { loadData() }
        data.await()
    }
}
```

### 3. DataStore (KMP)
- **版本**: 1.2.0-alpha01
- **包含**:
  - `androidx-datastore-preferences`
  - `androidx-datastore-core`
- **用途**: 键值对持久化存储
- **示例**: `DataStoreManager.kt`、平台特定实现

```kotlin
// 使用示例
val dataStoreManager = DataStoreManager(dataStore)
dataStoreManager.setLoginStatus(true)
dataStoreManager.isLoggedIn.collect { isLoggedIn ->
    // 处理登录状态
}
```

### 4. Room (KMP)
- **版本**: 2.7.0-alpha15
- **包含**:
  - `androidx-room-runtime`
  - `androidx-room-compiler` (KSP)
  - `androidx-sqlite-bundled`
- **用途**: SQLite 数据库 ORM
- **配置**:
  - 已添加 Room plugin
  - KSP 处理器已配置（Android、iOS）
  - Schema 目录: `composeApp/schemas`

```kotlin
@Entity
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String
)

@Dao
interface UserDao {
    @Query("SELECT * FROM UserEntity")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert
    suspend fun insert(user: UserEntity)
}
```

### 5. Arrow (FP)
- **版本**: 2.1.0
- **包含**:
  - `arrow-core` - 核心类型（Either、Option 等）
  - `arrow-fx-coroutines` - 协程集成
  - `arrow-optics` - 数据操作
  - `arrow-optics-ksp-plugin` - Optics 代码生成
  - `arrow-resilience` - 弹性模式（重试、熔断）
- **用途**: 函数式编程工具

```kotlin
// Either 错误处理
suspend fun fetchUser(): Either<Error, User> =
    runCatchingEither {
        api.getUser()
    }

// 重试机制
val result = runWithRetry(maxRetries = 3) {
    unstableOperation()
}
```

### 6. Molecule
- **版本**: 2.0.0
- **包含**: `molecule-runtime`
- **用途**: 将 @Composable 转换为 Flow，用于状态管理
- **示例**: `MoleculeExample.kt`

```kotlin
@Composable
fun MyPresenter(events: Flow<Event>): State {
    var state by remember { mutableStateOf(State()) }

    LaunchedEffect(Unit) {
        events.collect { event ->
            // 处理事件
        }
    }

    return state
}

// 在 ViewModel 中使用
val stateFlow = scope.launchMolecule {
    MyPresenter(events)
}
```

### 7. Koin (DI)
- **版本**: 4.1.0 / 2.0.0 (compose)
- **包含**:
  - `koin-core` - 核心 DI
  - `koin-android` - Android 扩展
  - `koin-androidx-compose` - Android Compose
  - `koin-compose` - KMP Compose
  - `koin-compose-viewmodel` - Compose ViewModel
  - `koin-test` - 测试工具
- **用途**: 依赖注入
- **示例**: `AppModule.kt`

```kotlin
// 定义模块
val appModule = module {
    single { MyRepository() }
    viewModel { MyViewModel(get()) }
}

// 启动 Koin
fun initKoin() {
    startKoin {
        modules(appModule)
    }
}

// 在 Composable 中使用
@Composable
fun MyScreen() {
    val viewModel = koinViewModel<MyViewModel>()
}
```

### 8. Navigation3 (KMP)
- **版本**: 1.0.0-alpha05
- **包含**:
  - `androidx-navigation3-ui` (JetBrains KMP)
  - `androidx-navigation3-runtime-android` (Android 专用)
  - `androidx-lifecycle-viewmodel-navigation3` (ViewModel 集成)
- **用途**: Compose Navigation 新一代，完全类型安全
- **示例**: `AppNavigation.kt`, `NavigationExample.kt`

```kotlin
import androidx.navigation3.compose.*
import androidx.navigation3.viewmodel.viewModel

@Serializable
object HomeRoute

@Serializable
data class DetailRoute(val id: String)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen()
        }
        composable<DetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DetailRoute>()
            // Navigation3 支持 ViewModel 集成
            DetailScreen(
                id = route.id,
                viewModel = viewModel { DetailViewModel(route.id) }
            )
        }
    }
}
```

### 9. KSP (Kotlin Symbol Processing)
- **版本**: 2.3.0-1.0.30
- **用途**: 代码生成（Room、Arrow Optics）
- **配置**:
  - Room Compiler (Android, iOS)
  - Arrow Optics Plugin (所有平台)

### 10. Ktor (HTTP Client)
- **版本**: 3.4.0
- **包含**:
  - `ktor-client-core` - 核心
  - `ktor-client-cio` - 跨平台纯 Kotlin 引擎 (推荐)
  - `ktor-client-content-negotiation` - 内容协商
  - `ktor-client-logging` - 日志
  - `ktor-client-auth` - 认证
  - `ktor-client-encoding` - 压缩
  - `ktor-serialization-kotlinx-json` - JSON 序列化
  - `ktor-serialization-kotlinx-protobuf` - ProtoBuf 序列化
- **用途**: HTTP 网络请求
- **示例**: `AppModule.kt`
- **引擎选择**: CIO 引擎是纯 Kotlin 实现，支持所有 KMP 平台，适合激进的跨平台项目

```kotlin
val client = HttpClient {
    // CIO 引擎会自动使用，无需显式配置
    install(ContentNegotiation) {
        json()
        protobuf()
    }

    install(Logging) {
        level = LogLevel.INFO
    }

    install(Auth) {
        bearer {
            loadTokens {
                // 加载 token
            }
        }
    }
}

// 使用
suspend fun fetchUser(id: String): User {
    return client.get("https://api.example.com/users/$id").body()
}
```

### 额外依赖

#### Kotlinx Extensions
- **kotlinx-datetime** (0.6.1): 日期时间处理
- **kotlinx-atomicfu** (0.27.0): 原子操作
- **kotlinx-io-core** (0.6.0): IO 操作

## 项目结构

```
composeApp/
├── src/
│   ├── commonMain/
│   │   └── kotlin/
│   │       └── com/connor/kwitter/
│   │           ├── di/              # Koin DI 配置
│   │           ├── data/
│   │           │   ├── database/    # Room Database
│   │           │   └── datastore/   # DataStore
│   │           ├── navigation/      # Navigation
│   │           ├── ui/              # Molecule 示例
│   │           └── utils/           # Arrow 工具
│   ├── androidMain/
│   │   └── kotlin/                  # Android 平台实现
│   └── iosMain/
│       └── kotlin/                  # iOS 平台实现
└── schemas/                         # Room schema 导出
```

## 构建和同步

1. **同步 Gradle**:
```bash
./gradlew --refresh-dependencies
```

2. **生成 KSP 代码** (Room, Arrow):
```bash
./gradlew kspCommonMainKotlinMetadata
./gradlew kspAndroid
./gradlew kspIosArm64
```

3. **构建项目**:
```bash
./gradlew build
```

## 注意事项

### Room Database
- Room KMP 目前是 Alpha 版本
- 需要为每个平台配置 KSP
- 数据库文件路径由平台特定实现提供

### DataStore
- 需要为每个平台提供文件路径
- Android 使用 `preferencesDataStore` delegate
- iOS 需要手动指定文档目录

### Ktor
- 使用 CIO 引擎实现跨平台统一，纯 Kotlin 实现
- CIO 支持所有 KMP 目标平台（Android、iOS、Desktop、Web）
- 建议在 DI 中配置单例 HttpClient

### Arrow Optics
- 使用 KSP 生成 Lens、Prism 等
- 需要在数据类上添加 `@optics` 注解

### Navigation3
- Navigation 新一代，API 更简洁，性能更好
- 使用 `@Serializable` 注解定义路由实现类型安全
- 需要 kotlinx-serialization plugin
- 支持与 ViewModel 深度集成（lifecycle-viewmodel-navigation3）
- Android 使用 androidx.navigation3，跨平台使用 org.jetbrains.androidx.navigation3

## 开发哲学

所有依赖都是 KMP 兼容版本，支持：
- ✅ Android
- ✅ iOS
- ⚠️ Desktop (部分支持，需额外配置)
- ⚠️ Web (部分支持，需额外配置)

使用这些库可以实现：
1. **类型安全**: Serialization、Navigation
2. **函数式编程**: Arrow（Either、Option、Optics）
3. **响应式编程**: Coroutines + Flow
4. **声明式 UI**: Compose + Molecule
5. **依赖注入**: Koin
6. **数据持久化**: Room + DataStore
7. **网络请求**: Ktor
8. **弹性设计**: Arrow Resilience

## 下一步

1. 根据实际需求创建 Entity 和 DAO
2. 定义 API 接口和数据模型
3. 实现具体的 Repository
4. 创建 ViewModel/Presenter
5. 构建 UI 界面

所有配置已就绪，可以开始开发了！
