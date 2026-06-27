#!/usr/bin/env bash
#
# Re-binds the app's accessibility service after a reinstall.
#
# Why this is needed: when an APK is replaced (every `installDebug`/`run-android.sh`), Android kills
# the running process and leaves the accessibility service in a "crashed" state. The toggle still
# shows as ON, but the system never re-binds it, so the social gate silently stops working. Toggling
# the service off and on again (which requires WRITE_SECURE_SETTINGS — granted to `adb shell`) forces
# a clean re-bind. Other enabled accessibility services are preserved.
#
# Usage: ./tools/reenable-accessibility.sh
set -euo pipefail

COMP="com.estudio.antiprocrastinacion/com.estudio.antiprocrastinacion.app.socialgate.SocialGateAccessibilityService"
PKG="com.estudio.antiprocrastinacion"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb no está en el PATH. Corré primero: source ./android-env.sh" >&2
  exit 1
fi

if [ -z "$(adb get-state 2>/dev/null || true)" ]; then
  echo "No hay dispositivo ADB conectado/autorizado." >&2
  exit 1
fi

echo "→ Reiniciando el proceso de la app para un re-vínculo limpio"
adb shell am force-stop "$PKG" || true

current="$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
[ "$current" = "null" ] && current=""

# Garantizamos que quede activo: una reinstalación a veces lo deja "crasheado" (sigue en la lista) y
# otras lo saca de la lista por completo. En ambos casos lo dejamos habilitado y re-vinculado, sin
# tocar el resto de los servicios de accesibilidad.

# Lista de servicios habilitados SIN el nuestro (preserva el resto).
without="$(printf '%s' "$current" | tr ':' '\n' | grep -vxF "$COMP" | grep -v '^$' | paste -sd ':' -)"

echo "→ Quitando nuestro servicio (off)"
adb shell settings put secure enabled_accessibility_services "$without"

echo "→ Reactivando nuestro servicio (on → fuerza re-vínculo)"
if [ -n "$without" ]; then
  adb shell settings put secure enabled_accessibility_services "$COMP:$without"
else
  adb shell settings put secure enabled_accessibility_services "$COMP"
fi
adb shell settings put secure accessibility_enabled 1

echo "→ Estado:"
adb shell dumpsys accessibility 2>/dev/null | grep -iE "Enabled services|Crashed services" | head -2
echo "Listo. Si nuestro servicio aparece en 'Enabled services' y NO en 'Crashed services', quedó re-vinculado."
