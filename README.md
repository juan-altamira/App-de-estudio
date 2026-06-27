# App de Estudio

MVP local Android-first para repaso espaciado anti-procrastinación.

## Stack

- Kotlin + Jetpack Compose
- Hilt
- Room
- DataStore
- Navigation Compose
- SAF para importar JSON

## Qué incluye esta base

- Proyecto Android de un solo módulo `app`
- Seed demo idempotente en `app/src/main/assets/seed/demo_content.json`
- Importación JSON con validación estructural y warnings semánticos
- Persistencia local de contenido, progreso, sesiones y eventos
- Scheduler a nivel nodo con `Repaso rápido`, `Modo profundo` y `Vaciar tema`
- Flujo de estudio full-screen con micro-pregunta al primer back
- Tests unitarios iniciales y Compose test básico

## Entorno local instalado

Este repo ya quedó preparado para usar un toolchain local:

- JDK 17 en `~/.local/jdks/temurin-17`
- Android SDK en `~/Android/Sdk`
- AVD `app-estudio-api36`
- variables listas en `./android-env.sh`

## Comandos

El repo incluye un `gradlew` bootstrap que descarga Gradle 9.3.1 si no existe localmente.

```bash
source ./android-env.sh
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

## Flujo completo en una sola orden

Para bootear el emulador, correr tests conectados, instalar la app y abrirla:

```bash
./run-android.sh
```

Para ver qué devices ADB tenés conectados:

```bash
./run-android.sh --list-devices
```

Opciones útiles:

```bash
./run-android.sh --skip-connected-tests
./run-android.sh --skip-install
./run-android.sh --show-emulator --restart-emulator --skip-connected-tests
./run-android.sh --show-emulator --restart-emulator --wipe-data --skip-connected-tests
./run-android.sh --show-emulator --restart-emulator --boot-timeout 2400 --skip-connected-tests
./run-android.sh --serial TU_SERIAL --skip-unit-tests --skip-connected-tests
./run-android.sh --avd otro-avd
```

Si todo sale bien, el script deja una captura de pantalla en `build/app-launch.png`.

`--wipe-data` hace un cold boot real y puede tardar mucho. Usalo solo si el emulador quedó roto o congelado.
En esta máquina el primer boot puede tardar muchísimo; si hace falta, subí el timeout con `--boot-timeout`.

Si conectás un telefono por USB, podés apuntar directo a ese dispositivo con `--serial` y el script instala/abre la app usando `adb` sin depender del emulador.
