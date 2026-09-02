# O que adicionar no projeto

## 1. `app/build.gradle.kts` — dentro do bloco `dependencies { }`

Adicione estas linhas (mantenha o que já existe):

```kotlin
dependencies {
    // ... suas dependências existentes (appcompat, material, etc.) continuam aqui

    // Retrofit (chamadas HTTP)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // OkHttp + logging (pra ver as requisições no Logcat, ótimo pra debugar)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Corrotinas (para lifecycleScope.launch { } funcionar)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AppCompat + lifecycle (provavelmente já estão no projeto, confira antes de duplicar)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
```

Depois de editar, clique em **"Sync Now"** no aviso que aparece no topo do Android Studio.

## 2. `app/src/main/AndroidManifest.xml`

Duas coisas precisam entrar:

**a) Permissão de internet** — sem isso, TODA chamada de rede falha silenciosamente.
Adicione **fora** da tag `<application>`, direto dentro de `<manifest>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

**b) Registrar a LoginActivity e trocar a activity de entrada (launcher).**

Hoje seu `MainActivity` deve estar assim (com o `intent-filter` de launcher). Ajuste para:

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    ...>

    <!-- LoginActivity vira a tela inicial -->
    <activity
        android:name=".ui.login.LoginActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- MainActivity deixa de ser launcher, mas continua registrada -->
    <activity
        android:name=".MainActivity"
        android:exported="false" />

</application>
```

**Importante:** se o seu `MainActivity.kt` ainda tinha o `intent-filter` de `MAIN`/`LAUNCHER`, REMOVA de lá — só pode haver um launcher no app, e agora é a `LoginActivity`.

## 3. Rodando o teste

1. Suba o backend Kipper localmente (`mvnw spring-boot:run` ou pelo IntelliJ/Spring Tool), confirme que sobe na porta **8080**.
2. Rode o app no **emulador** (não em celular físico — o `10.0.2.2` só funciona no emulador).
3. Crie um usuário de teste primeiro. Como ainda não tem tela de cadastro no app, use o Swagger do backend (`http://localhost:8080/swagger-ui.html`) ou um `curl`/Postman:
   ```
   POST http://localhost:8080/api/v1/auth/register
   { "nome": "Teste", "email": "teste@teste.com", "password": "123456" }
   ```
4. Abra o app, faça login com esse e-mail/senha.
