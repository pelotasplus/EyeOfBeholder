# Architecture: features, UI and DI

## Pattern: MVVM (Model-View-ViewModel)

The codebase follows clean MVVM architecture with clear separation:

- **View Layer**: Composable screens using Jetpack Compose
- **ViewModel Layer**: State management with `StateFlow` and lifecycle awareness
- **Repository Layer**: Data access abstraction with interface/implementation pattern
- **Model Layer**: Domain models representing game data structures

## Package Structure

```
composeApp/src/commonMain/kotlin/pl/pelotasplus/eyeofbeholder/
├── di/                    # Dependency Injection (Koin)
├── data/
│   ├── model/            # Domain models (Inf, Maz, Vcn, Vmp, Item, etc.)
│   │   └── script/       # Script token types (29 opcodes)
│   ├── repository/       # Data access layer (10 repositories)
│   ├── ByteReader.kt     # Binary file parsing utility
│   └── LCWHelper.kt      # LCW compression/decompression
├── features/             # Feature modules (each self-contained)
│                          # (golden-image tests live in composeApp/src/jvmTest/)
│   ├── main_debug/       # DebugMenu — the global top-right navigation menu
│   ├── levels_debug/     # Level (INF) picker
│   ├── cps_debug/        # Graphics debugging
│   └── view_cone_debug/  # 3D viewport rendering — the start destination
└── navigation/           # Navigation routing
```

## Feature Module Pattern

Each feature follows a consistent structure:

1. **ViewModel** (`*ViewModel.kt`): Manages state with `MutableStateFlow`, handles events
2. **Screen** (`*Screen.kt`): Composable UI with `collectAsStateWithLifecycle()`
3. **DI Module** (`di/Modules.kt`): Feature-specific dependency injection

```kotlin
// Standard feature pattern
@Stable
class MyViewModel(private val repository: MyRepository) : ViewModel() {
    val state = MutableStateFlow(State())
    fun onEvent(event: Event) { /* handle events */ }
}

@Composable
fun MyScreen(viewModel: MyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // UI implementation
}

// DI registration
val sharedFeaturesMyModule = module {
    viewModelOf(::MyViewModel)
}
```

## Navigation

Type-safe navigation using `androidx.navigation.compose` with `@Serializable`
routes in `navigation/Route.kt`: `ViewConeDebug(level)`, `LevelsDebug`,
`CpsDebug`.

`ViewConeDebug()` is the start destination — the app opens on the rendered
level rather than a menu. `DebugMenu` is drawn in `App.kt` outside the
`NavHost`, aligned top-end, so it is reachable from every screen; it is the
only navigation affordance, and no screen has its own back button.

## Dependency Injection (Koin)

The project uses Koin for DI with a modular structure:

1. **Root setup** in `di/InitKoin.kt`
2. **Data module** in `data/di/Modules.kt` (repositories)
3. **Feature modules** in `features/*/di/Modules.kt` (ViewModels)

Currently registered modules:
- `sharedDataModule` — all repositories
- `sharedFeaturesCpsDebugModule`
- `sharedFeaturesLevelsDebugModule`
- `sharedFeaturesViewConeDebugModule`

To add a new feature:
```kotlin
// 1. Create feature DI module
val sharedFeaturesMyModule = module {
    viewModelOf(::MyViewModel)
}

// 2. Register in InitKoin.kt
modules(
    sharedDataModule,
    sharedFeaturesMyModule,
    // ... other modules
)
```

## Adding a New Feature

1. **Create domain model** in `data/model/MyData.kt`
2. **Create repository interface + impl** in `data/repository/MyRepository.kt`
3. **Add DI binding** in `data/di/Modules.kt`:
   ```kotlin
   factory<MyRepository> { MyRepositoryImpl(get()) }
   ```
4. **Create feature ViewModel** in `features/my_feature/MyViewModel.kt`
5. **Create feature Screen** in `features/my_feature/MyScreen.kt`
6. **Create feature DI module** in `features/my_feature/di/Modules.kt`
7. **Register in root DI** in `di/InitKoin.kt`
8. **Add navigation route** in `navigation/Route.kt` and wire in `App.kt`
9. **Add it to the Debug menu** in `features/main_debug/DebugMenu.kt`
