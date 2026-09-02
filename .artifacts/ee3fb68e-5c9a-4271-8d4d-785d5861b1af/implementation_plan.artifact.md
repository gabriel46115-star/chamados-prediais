# Fix Project Dependencies and Code Errors

The project currently has several issues that prevent it from compiling and running:
1. Missing Retrofit, OkHttp, and Coroutines dependencies in `build.gradle.kts`.
2. Incorrect `compileSdk` and `targetSdk` configurations in `build.gradle.kts`.
3. Missing Internet permission and incorrect launcher activity in `AndroidManifest.xml`.
4. Almost all Kotlin files are missing `import` statements.
5. Some files have the wrong `package` declaration (referencing `com.example.chamados` instead of `com.br.fiec.chamados`).

## Proposed Changes

### Configuration

#### [MODIFY] [build.gradle.kts](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/build.gradle.kts)
- Correct `compileSdk` and `targetSdk` to 35 (Android 15).
- Add Retrofit, OkHttp, and Coroutines dependencies.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.INTERNET" />`.
- Set `LoginActivity` as the launcher activity.
- Set `MainActivity` as a regular activity.

### Data Layer

#### [MODIFY] [AuthModels.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/data/model/AuthModels.kt)
- Fix package declaration.

#### [MODIFY] [SolicitacaoModels.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/data/model/SolicitacaoModels.kt)
- Fix package declaration.

#### [MODIFY] [TokenManager.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/data/TokenManager.kt)
- Add missing imports for `Context` and `SharedPreferences`.

#### [MODIFY] [ApiService.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/data/network/ApiService.kt)
- Add missing imports for Retrofit annotations and types.

#### [MODIFY] [AuthInterceptor.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/data/network/AuthInterceptor.kt)
- Fix package declaration and add missing imports for OkHttp and `TokenManager`.

#### [MODIFY] [RetrofitClient.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/data/network/RetrofitClient.kt)
- Add missing imports for Retrofit, OkHttp, and Gson.

### UI Layer

#### [MODIFY] [LoginActivity.kt](file:///C:/Users/46115/AndroidStudioProjects/ChamadosPrediais/app/src/main/java/com/br/fiec/chamados/ui/login/LoginActivity.kt)
- Add all missing imports (Android UI, Lifecycle, Coroutines, and local data classes).

## Verification Plan

### Automated Verification
- I will run a build check (if possible) or at least ensure all files are syntactically correct and have all necessary imports.

### Manual Verification
- The user should sync the project with Gradle files.
- The user should run the app on an emulator to verify that `LoginActivity` starts first.
