#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_SCRIPT="${ROOT_DIR}/android-env.sh"
EMULATOR_LOG="${ROOT_DIR}/build/emulator.log"
SCREENSHOT_PATH="${ROOT_DIR}/build/app-launch.png"
AVD_NAME="app-estudio-api36"
APP_ID="com.estudio.antiprocrastinacion"
TEST_APP_ID="${APP_ID}.test"
MAIN_ACTIVITY="${APP_ID}/.MainActivity"
BOOT_TIMEOUT_SECONDS=2400
DEBUG_APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
ANDROID_TEST_APK_PATH="${ROOT_DIR}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SKIP_UNIT_TESTS=false
SKIP_CONNECTED_TESTS=false
SKIP_INSTALL=false
SKIP_SCREENSHOT=false
NEEDS_DEVICE=true
SHOW_EMULATOR=false
RESTART_EMULATOR=false
WIPE_DATA=false
TARGET_SERIAL=""
LIST_DEVICES=false

usage() {
    cat <<'EOF'
Uso:
  ./run-android.sh [opciones]

Opciones:
  --avd <name>              Nombre del AVD. Default: app-estudio-api36
  --serial <adb_serial>     Usa un device especifico por serial
  --list-devices            Lista devices ADB y sale
  --show-emulator           Abre el emulador con ventana visible
  --restart-emulator        Mata el emulador actual y lo relanza
  --wipe-data               Fuerza cold boot borrando datos del emulador
  --boot-timeout <seg>      Timeout de boot del emulador. Default: 2400
  --skip-unit-tests         Saltea ./gradlew testDebugUnitTest
  --skip-connected-tests    Saltea ./gradlew connectedDebugAndroidTest
  --skip-install            Saltea ./gradlew installDebug y launch
  --skip-screenshot         No captura screenshot tras abrir la app
  --help                    Muestra esta ayuda
EOF
}

log() {
    printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$*" >&2
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --avd)
            shift
            [[ $# -gt 0 ]] || fail "Falta valor para --avd"
            AVD_NAME="$1"
            ;;
        --serial)
            shift
            [[ $# -gt 0 ]] || fail "Falta valor para --serial"
            TARGET_SERIAL="$1"
            ;;
        --list-devices)
            LIST_DEVICES=true
            ;;
        --show-emulator)
            SHOW_EMULATOR=true
            ;;
        --restart-emulator)
            RESTART_EMULATOR=true
            ;;
        --wipe-data)
            WIPE_DATA=true
            ;;
        --boot-timeout)
            shift
            [[ $# -gt 0 ]] || fail "Falta valor para --boot-timeout"
            BOOT_TIMEOUT_SECONDS="$1"
            ;;
        --skip-unit-tests)
            SKIP_UNIT_TESTS=true
            ;;
        --skip-connected-tests)
            SKIP_CONNECTED_TESTS=true
            ;;
        --skip-install)
            SKIP_INSTALL=true
            ;;
        --skip-screenshot)
            SKIP_SCREENSHOT=true
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            fail "Opción no reconocida: $1"
            ;;
    esac
    shift
done

[[ -f "${ENV_SCRIPT}" ]] || fail "No existe ${ENV_SCRIPT}"
source "${ENV_SCRIPT}" >/dev/null

mkdir -p "${ROOT_DIR}/build"

command -v adb >/dev/null || fail "adb no está disponible"
command -v emulator >/dev/null || fail "emulator no está disponible"

device_serial() {
    adb devices | awk '$2 == "device" { print $1; exit }'
}

emulator_serial() {
    adb devices | awk '$1 ~ /^emulator-/ { print $1; exit }'
}

physical_device_serials() {
    adb devices | awk '$2 == "device" && $1 !~ /^emulator-/ { print $1 }'
}

is_emulator_serial() {
    [[ "$1" == emulator-* ]]
}

has_connected_device() {
    local serial="$1"
    adb devices | awk -v serial="${serial}" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }'
}

list_devices_and_exit() {
    adb devices -l
    exit 0
}

pick_target_serial() {
    if [[ -n "${TARGET_SERIAL}" ]]; then
        has_connected_device "${TARGET_SERIAL}" || fail "El device ${TARGET_SERIAL} no aparece como 'device' en adb"
        echo "${TARGET_SERIAL}"
        return
    fi

    local -a physicals=()
    mapfile -t physicals < <(physical_device_serials)
    if (( ${#physicals[@]} == 1 )) && [[ "${SHOW_EMULATOR}" != true && "${RESTART_EMULATOR}" != true && "${WIPE_DATA}" != true ]]; then
        echo "${physicals[0]}"
        return
    fi

    if (( ${#physicals[@]} > 1 )) && [[ "${SHOW_EMULATOR}" != true && "${RESTART_EMULATOR}" != true && "${WIPE_DATA}" != true ]]; then
        fail "Hay multiples dispositivos fisicos conectados. Usa --serial <adb_serial>."
    fi

    emulator_serial
}

stop_existing_emulator() {
    local serial
    serial="$(emulator_serial)"
    if [[ -n "${serial}" ]]; then
        log "Cerrando emulador actual: ${serial}"
        adb -s "${serial}" emu kill >/dev/null 2>&1 || true
        sleep 5
    fi

    if pgrep -f "qemu-system.*-avd ${AVD_NAME}" >/dev/null; then
        pkill -f "qemu-system.*-avd ${AVD_NAME}" || true
        sleep 5
    fi
}

ensure_adb() {
    adb start-server >/dev/null
}

start_emulator_if_needed() {
    local serial
    if [[ "${RESTART_EMULATOR}" == true ]]; then
        stop_existing_emulator
    fi

    serial="$(pick_target_serial)"
    if [[ -n "${serial}" ]]; then
        log "Usando device ya conectado: ${serial}"
        return
    fi

    if pgrep -f "qemu-system.*-avd ${AVD_NAME}" >/dev/null; then
        log "El AVD ${AVD_NAME} ya está corriendo; esperando que termine de bootear"
        return
    fi

    log "Levantando AVD ${AVD_NAME}"
    local -a emulator_args=(
        -avd "${AVD_NAME}"
        -gpu swiftshader_indirect
        -accel off
        -no-snapshot
        -no-audio
        -no-boot-anim
        -skip-adb-auth
    )

    if [[ "${WIPE_DATA}" == true ]]; then
        emulator_args+=(-wipe-data)
    fi

    if [[ "${SHOW_EMULATOR}" != true ]]; then
        emulator_args+=(-no-window)
    fi

    nohup emulator \
        "${emulator_args[@]}" \
        >"${EMULATOR_LOG}" 2>&1 &
    sleep 5
}

wait_for_boot() {
    local elapsed=0
    local serial=""

    serial="$(pick_target_serial)"
    if [[ -n "${serial}" ]] && ! is_emulator_serial "${serial}"; then
        log "Usando dispositivo fisico: ${serial}"
        echo "${serial}"
        return
    fi

    while (( elapsed < BOOT_TIMEOUT_SECONDS )); do
        serial="$(pick_target_serial)"
        if [[ -n "${serial}" ]]; then
            local boot_completed
            boot_completed="$(adb -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
            if [[ "${boot_completed}" == "1" ]]; then
                adb -s "${serial}" shell input keyevent 82 >/dev/null 2>&1 || true
                log "Device listo: ${serial}"
                echo "${serial}"
                return
            fi
        fi

        local offline
        offline="$(emulator_serial)"
        if [[ -n "${offline}" ]]; then
            adb reconnect offline >/dev/null 2>&1 || true
        fi

        sleep 5
        elapsed=$((elapsed + 5))
    done

    if [[ -f "${EMULATOR_LOG}" ]]; then
        echo >&2
        echo "Ultimas lineas de ${EMULATOR_LOG}:" >&2
        tail -n 40 "${EMULATOR_LOG}" >&2 || true
    fi
    fail "El emulador no llegó a estado 'device' dentro de ${BOOT_TIMEOUT_SECONDS}s"
}

run_gradle() {
    log "Ejecutando: ./gradlew $*"
    ./gradlew "$@"
}

install_apk() {
    local serial="$1"
    local apk_path="$2"
    [[ -f "${apk_path}" ]] || fail "No existe el APK ${apk_path}"
    log "Instalando $(basename "${apk_path}") en ${serial}"
    adb -s "${serial}" install -r -t "${apk_path}"
}

run_connected_tests_via_adb() {
    local serial="$1"
    run_gradle assembleDebug assembleDebugAndroidTest
    install_apk "${serial}" "${DEBUG_APK_PATH}"
    install_apk "${serial}" "${ANDROID_TEST_APK_PATH}"
    log "Ejecutando tests instrumentados en ${serial}"
    adb -s "${serial}" shell am instrument -w "${TEST_APP_ID}/androidx.test.runner.AndroidJUnitRunner"
    reenable_accessibility "${serial}"
}

install_debug_via_adb() {
    local serial="$1"
    run_gradle assembleDebug
    install_apk "${serial}" "${DEBUG_APK_PATH}"
    reenable_accessibility "${serial}"
}

# Android deja el servicio de accesibilidad en estado "crashed" tras cada reinstalación (mata el
# proceso al reemplazar el APK y no lo re-vincula). Esto lo apaga y prende para forzar el re-vínculo,
# preservando el resto de servicios. Solo actúa si ya estaba habilitado por el usuario.
reenable_accessibility() {
    local serial="$1"
    local comp="com.estudio.antiprocrastinacion/com.estudio.antiprocrastinacion.app.socialgate.SocialGateAccessibilityService"
    local current without
    current="$(adb -s "${serial}" shell settings get secure enabled_accessibility_services | tr -d '\r')"
    [[ "${current}" == "null" ]] && current=""
    log "Garantizando el servicio de accesibilidad activo y re-vinculado tras reinstalar"
    adb -s "${serial}" shell am force-stop com.estudio.antiprocrastinacion || true
    without="$(printf '%s' "${current}" | tr ':' '\n' | grep -vxF "${comp}" | grep -v '^$' | paste -sd ':' -)"
    adb -s "${serial}" shell settings put secure enabled_accessibility_services "${without}"
    if [[ -n "${without}" ]]; then
        adb -s "${serial}" shell settings put secure enabled_accessibility_services "${comp}:${without}"
    else
        adb -s "${serial}" shell settings put secure enabled_accessibility_services "${comp}"
    fi
    adb -s "${serial}" shell settings put secure accessibility_enabled 1
}

take_screenshot() {
    local serial="$1"
    log "Capturando screenshot en ${SCREENSHOT_PATH}"
    adb -s "${serial}" exec-out screencap -p >"${SCREENSHOT_PATH}"
}

ensure_adb

if [[ "${LIST_DEVICES}" == true ]]; then
    list_devices_and_exit
fi

if [[ "${SKIP_CONNECTED_TESTS}" == true && "${SKIP_INSTALL}" == true ]]; then
    NEEDS_DEVICE=false
fi

if [[ "${NEEDS_DEVICE}" == true ]]; then
    start_emulator_if_needed
    SERIAL="$(wait_for_boot)"
fi

if [[ "${SKIP_UNIT_TESTS}" != true ]]; then
    run_gradle testDebugUnitTest
fi

if [[ "${SKIP_CONNECTED_TESTS}" != true ]]; then
    run_connected_tests_via_adb "${SERIAL}"
fi

if [[ "${SKIP_INSTALL}" != true ]]; then
    if [[ "${SKIP_CONNECTED_TESTS}" == true ]]; then
        install_debug_via_adb "${SERIAL}"
    fi
    log "Abriendo ${MAIN_ACTIVITY}"
    adb -s "${SERIAL}" shell am start -n "${MAIN_ACTIVITY}" >/dev/null
    if [[ "${SKIP_SCREENSHOT}" != true ]]; then
        sleep 4
        take_screenshot "${SERIAL}"
    fi
fi

log "Flujo Android completado"
