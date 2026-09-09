// Arena Agent — корневой build-файл (Kotlin DSL, как в шаблоне, собранном в IDE на телефоне).
// Профиль по умолчанию: Gradle 9.0.0 + AGP 8.13.0 — конфигурация 1-в-1 как в my_project (main).
plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
