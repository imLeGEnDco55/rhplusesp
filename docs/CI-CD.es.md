# Compilar y publicar rhplusesp

Este pipeline se mantiene separado de la traducción del PR #1. Se puede integrar
primero en `huadeng` y después actualizar la rama `l10n/es` desde `huadeng` para
que sus siguientes pushes ejecuten CI. No publicar una versión española hasta
que la traducción esté integrada y el build del commit final pase.

## Qué hace GitHub automáticamente

| Evento | Workflow | Resultado |
| --- | --- | --- |
| Push a cualquier rama | Android CI | APK debug y pruebas unitarias de `app` |
| Pull request, incluidos forks | Android CI | Compila el resultado del merge, sin secretos de firma |
| Actions → Android CI → Run workflow | Android CI | Compilación manual de la rama elegida |
| Push de un tag `v*` | Android Release | Release optimizada, firma, verificación y publicación |

Los APK de prueba están en **Actions → ejecución → Artifacts**, duran 14 días y
requieren iniciar sesión en GitHub para descargarlos. El debug usa
`me.rerere.rikkahub.debug`, por lo que puede convivir con la app release. Su clave
debug es temporal: un APK de otra ejecución puede necesitar desinstalar antes el
debug anterior. No sirve para actualizar una instalación release.

Las releases publican un APK `arm64-v8a` (Android 8 / API 26 o superior),
`SHA256SUMS` y `update.json`. También guardan un artifact firmado durante 30 días.
El APK adjunto a una release pública se descarga sin iniciar sesión.

## Hallazgos del proyecto heredado

- Gradle Wrapper 9.6.0, Android Gradle Plugin 9.4.0, Kotlin 2.4.10; SDK 37,
  `versionName = "2.5.4fix13"`, `versionCode = 226` en la base investigada.
- Java 21 para ejecutar Gradle; el bytecode Android sigue apuntando a Java 17.
  El criterio del daemon ya no exige exclusivamente el proveedor JetBrains.
- Chaquopy 17 necesita Python 3.12 en el host para empaquetar las dependencias
  Python. Se instala explícitamente en CI.
- El submódulo `material3/material-color-utilities` debe inicializarse.
- `:web:buildWebUi` necesita Node y pnpm. CI instala las dependencias con el lockfile
  y **sí construye la interfaz web**; no usa la exclusión heredada de esa tarea.
- Firebase está desactivado por defecto y se mantiene así en ambos workflows;
  no se necesita `google-services.json` ni un secreto de Firebase.
- El workflow anterior `build.yml` solo permitía el repositorio de MiaoWuNYA,
  no incluía PR y escribía una configuración de firma de prueba.
- `app/app.key` estaba versionado con credenciales de prueba públicas. Se elimina
  de la rama y se ignoran los archivos de keystore; la copia histórica sigue
  siendo pública y **no debe reutilizarse como clave privada de distribución**.
- Se retira `Build Original (Comparison)`, que dependía de esa clave y compilaba
  otro repositorio. `Daily Build` sigue limitado expresamente al repositorio de
  MiaoWuNYA y no publica nightlies en este fork.
- `checkJsEngines` advierte sobre 12 motores JS ausentes. Aunque su comentario
  menciona esbuild en CI, el workflow y los assets heredados no incluyen esa
  generación. Este pipeline conserva ese comportamiento; un APK compilado no
  prueba que esas funciones opcionales estén completas.
- Algunas dependencias Python no tienen versión fija y SQLite usa `-SNAPSHOT`.
  La compilación no es reproducible byte por byte; este cambio no actualiza ni
  congela las dependencias de la app.

## Configuración inicial en GitHub

1. Integra el PR del pipeline en `huadeng`. Si el fork muestra workflows
   deshabilitados en **Actions**, habilítalos. En **Settings → Actions → General**
   permite las acciones utilizadas por los workflows.
2. En **Settings → Environments**, crea `android-release` y limita los despliegues
   a tags `v*`. Puedes añadir revisores si quieres aprobación antes de firmar;
   sin revisores, la publicación será automática al subir un tag válido.
3. Añade estos cuatro **Environment secrets** dentro de `android-release`:

| Secreto | Contenido |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Archivo JKS completo codificado en Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del archivo JKS |
| `ANDROID_KEY_ALIAS` | Alias de la clave, por ejemplo `rhplusesp` |
| `ANDROID_KEY_PASSWORD` | Contraseña de esa clave |

No pegues los secretos en issues, PR, archivos, logs ni en el chat. Base64 es una
codificación, no cifrado. `GITHUB_TOKEN` lo proporciona GitHub automáticamente:
no hay que crear un PAT. Los trabajos de compilación solo tienen `contents: read`;
únicamente el trabajo de publicación tiene `contents: write`.

El trabajo de firma utiliza un runner separado, sin checkout, Gradle, npm, pip ni
cachés de compilación. Decodifica la clave en un archivo temporal, pasa las
contraseñas por variables de entorno a `apksigner` y elimina la clave al terminar,
también si falla. Solo se suben APK, checksum y manifiesto. Si falta un secreto
o la firma es inválida, falla: no sustituye la clave por una debug.

## Crear y conservar la clave de distribución

Si ya tienes una clave privada con la que distribuiste **tu** fork, conserva esa
misma. Si aún no, ejecuta una sola vez, en una carpeta privada fuera del repo:

```sh
keytool -genkeypair -keystore rhplusesp-release.jks -storetype JKS \
  -alias rhplusesp -keyalg RSA -keysize 3072 -validity 10000
```

El comando solicita las contraseñas de forma interactiva. Haz una copia de
seguridad cifrada del JKS, alias y contraseñas. GitHub no permite recuperar el
valor de un secreto una vez guardado. No generes una clave nueva en cada build.

Para obtener Base64 sin imprimirlo en la terminal, en PowerShell:

```powershell
$keyPath = (Resolve-Path .\rhplusesp-release.jks).Path
$encodedKey = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath))
Set-Clipboard -Value $encodedKey
```

Pega el contenido en `ANDROID_KEYSTORE_BASE64` y guarda el secreto. Después:

```powershell
Set-Clipboard -Value ''
Remove-Variable encodedKey
```

En Linux: `base64 -w 0 rhplusesp-release.jks > rhplusesp-release.jks.base64`.
En macOS: `base64 -i rhplusesp-release.jks -o rhplusesp-release.jks.base64`.
Trata también el archivo Base64 como secreto y bórralo después de guardarlo en
GitHub. Nunca lo añadas a Git.

Android exige el mismo identificador de paquete y firma para actualizar una app.
La release conserva `me.rerere.rikkahub`: una instalación del RikkaHub/HuaDeng de
otro autor normalmente usa otra firma. Exporta sus datos antes de desinstalarla
para instalar por primera vez tu fork. CI no puede obtener ni sustituir la clave
privada del autor original. Las versiones posteriores de tu fork sí se podrán
actualizar conservando tu propia clave.

## Publicar una versión

1. En `app/build.gradle.kts`, aumenta **versionName y versionCode**. El código debe
   ser mayor que el de cualquier APK de tu fork ya distribuido. El workflow no
   inventa versiones a partir de la fecha ni del número de ejecución.
2. Usa versiones compatibles con el comparador existente: `2.5.5`,
   `2.5.4fix14` o `2.5.5-beta.1`. No uses sufijos como `-es-MX` para una release
   estable. `versionName` no lleva la `v` inicial.
3. Integra el cambio, comprueba el CI del commit y crea el tag en ese mismo commit:

```sh
git switch huadeng
git pull --ff-only
git tag -a v2.5.5 -m "rhplusesp 2.5.5"
git push origin v2.5.5
```

El ejemplo presupone que ya cambiaste `versionName` a `2.5.5` y aumentaste el
`versionCode`. También puedes crear el tag desde GitHub. **Crear un tag dispara
el workflow; editar solo el título de una release no lo dispara.** No publiques
manualmente una release vacía: el pipeline la crea como borrador, adjunta todos
los archivos y después la hace pública.

El workflow compila `:app:assembleRelease` sin firma y ejecuta las pruebas
unitarias. Reserva 4 GB de heap para Gradle y limita el trabajo paralelo a dos
workers, porque la optimización R8 de esta app supera el margen de los 2 GB
heredados. Valida el tag contra `output-metadata.json` generado por Android y
rechaza versiones discordantes, APK debug o múltiples APK inesperados. Otro
runner alinea, firma y verifica el APK con Android Build Tools.

Un tag con sufijo `-beta.1` u otro prerrelease genera una **prerelease**, que no se
marca como latest. Una versión estable se marca latest; publica versiones estables
en orden creciente. No se permite reemplazar una release ya pública. Si falla
antes de publicar, usa **Re-run failed jobs** o **Re-run all jobs**: el borrador
puede completarse. Si expiró el artifact unsigned, vuelve a ejecutar todos los
trabajos. Para modificar una versión ya publicada, incrementa la versión y crea
otro tag.

## Cómo llega la actualización a la app

Compilar, firmar y publicar son pasos de GitHub Actions. La app consulta por su
cuenta `https://api.github.com/repos/imLeGEnDco55/rhplusesp/releases/latest` y
compara el tag con su `BuildConfig.VERSION_NAME`. Ahora muestra solo assets APK,
sin ofrecer `SHA256SUMS` o `update.json` como instaladores.

Si falla la API, usa el asset
`https://github.com/imLeGEnDco55/rhplusesp/releases/latest/download/update.json`,
generado en la misma ejecución que el APK. Conserva los proxies heredados como
alternativas. No consulta el `update.json` histórico de la raíz, que contiene
información de HuaDeng; no hay commits automáticos a la rama para actualizarlo.

Solo se ofrecen releases estables públicas: no artifacts de CI, borradores ni
prereleases. El mecanismo descarga el APK mediante Android DownloadManager;
no es instalación silenciosa y requiere la intervención normal del usuario.
Antes de la primera release estable, no habrá una actualización disponible en
este fork. Las instalaciones antiguas aún apuntan a su origen anterior hasta
instalar una versión con este cambio.

El debug puede consultar el mismo canal estable, pero ese APK release es otro
paquete: no actualiza la aplicación debug.

## Verificación y diagnóstico

- `Android CI / Debug APK and unit tests` debe terminar en verde y mostrar un APK.
- El job de release debe mostrar una firma válida en `apksigner verify`.
- Instala el APK en un dispositivo ARM64 y prueba también actualizar una versión
  anterior de tu fork sin perder datos. CI no sustituye esa prueba de dispositivo.
- `Missing secret ...`: revisa los cuatro secretos del environment.
- Tag distinto de la versión: aumenta la versión en el código y crea el tag correcto.
- Error de firma al instalar: comprueba paquete, certificado y `versionCode`.
- Un fallo de Kotlin, recursos, dependencias o tests detiene el pipeline; no se
  oculta para producir un APK con checks fallidos.

Validación local de la lógica de publicación:

```sh
python -m unittest discover -s .github/scripts -p 'test_*.py'
actionlint .github/workflows/build.yml .github/workflows/release.yml
./gradlew :app:assembleDebug :app:testDebugUnitTest -Prikkahub.enableFirebase=false
./gradlew :app:assembleRelease -Prikkahub.unsignedRelease=true -Prikkahub.enableFirebase=false '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' --max-workers=2
```

Fuentes: [compatibilidad de AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Chaquopy](https://chaquo.com/chaquopy/doc/current/android.html),
[firma Android](https://developer.android.com/studio/publish/app-signing),
[eventos de Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows).
