#!/system/bin/sh
# OVERGLOW — переключатель профиля сборки.
#
#   sh scripts/switch-profile.sh            показать текущий профиль
#   sh scripts/switch-profile.sh modern     Gradle 9.0.0 + AGP 8.13.0 + compileSdk 36 (по умолчанию;
#                                           ровно как в шаблоне my_project из main, собранном на телефоне)
#   sh scripts/switch-profile.sh androidide Gradle 8.6 + AGP 8.2.2 + compileSdk 33 (для классического AndroidIDE)
#
# Запускать из корня проекта. Работает в терминале AndroidIDE/Termux.

set -e
cd "$(dirname "$0")/.."

MODERN_GRADLE="9.0.0"
MODERN_AGP="8.13.0"
MODERN_SDK="36"
MODERN_TARGET="34"
AIDE_GRADLE="8.6"
AIDE_AGP="8.2.2"
AIDE_SDK="33"
AIDE_TARGET="33"

set_profile() {
    gradle="$1"; agp="$2"; sdk="$3"; target="$4"

    sed -i.bak "s|gradle-[0-9][0-9.]*-bin.zip|gradle-${gradle}-bin.zip|" gradle/wrapper/gradle-wrapper.properties
    sed -i.bak "s|^agp = \"[^\"]*\"|agp = \"${agp}\"|" gradle/libs.versions.toml
    sed -i.bak "s|compileSdk = [0-9]*|compileSdk = ${sdk}|" app/build.gradle.kts
    sed -i.bak "s|targetSdk = [0-9]*|targetSdk = ${target}|" app/build.gradle.kts

    rm -f gradle/wrapper/gradle-wrapper.properties.bak gradle/libs.versions.toml.bak app/build.gradle.kts.bak

    echo "Готово. Профиль установлен:"
    echo "  Gradle:      ${gradle}  (gradle/wrapper/gradle-wrapper.properties)"
    echo "  AGP:         ${agp}  (gradle/libs.versions.toml)"
    echo "  compileSdk:  ${sdk}   (app/build.gradle.kts)"
    echo "  targetSdk:   ${target}   (app/build.gradle.kts)"
}

show() {
    gradle=$(grep -o 'gradle-[0-9][0-9.]*-bin.zip' gradle/wrapper/gradle-wrapper.properties | head -1 | sed 's/gradle-//; s/-bin.zip//')
    agp=$(grep '^agp = ' gradle/libs.versions.toml | sed 's/agp = "//; s/"//')
    sdk=$(grep -o 'compileSdk = [0-9]*' app/build.gradle.kts | head -1 | grep -o '[0-9]*')
    echo "Текущий профиль: Gradle ${gradle} · AGP ${agp} · compileSdk ${sdk}"
    echo "  modern:     Gradle ${MODERN_GRADLE} + AGP ${MODERN_AGP} (рабочая связка с телефона, по умолчанию)"
    echo "  androidide: Gradle ${AIDE_GRADLE} + AGP ${AIDE_AGP} (для классического AndroidIDE)"
}

case "${1:-}" in
    modern)
        set_profile "$MODERN_GRADLE" "$MODERN_AGP" "$MODERN_SDK" "$MODERN_TARGET"
        echo "Это основной профиль — Gradle 9.0.0 + AGP 8.13.0."
        ;;
    androidide)
        set_profile "$AIDE_GRADLE" "$AIDE_AGP" "$AIDE_SDK" "$AIDE_TARGET"
        echo "Профиль для классического AndroidIDE (Tooling API 8.6): Gradle 8.6 + AGP 8.2.2."
        echo "Переоткройте проект в IDE и соберите APK."
        echo "Вернуть основной профиль: sh scripts/switch-profile.sh modern"
        ;;
    *)
        show
        echo ""
        echo "Использование: sh scripts/switch-profile.sh [modern|androidide]"
        ;;
esac
