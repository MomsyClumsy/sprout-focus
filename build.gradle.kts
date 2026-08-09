// Корневой build-файл. Здесь только объявляем плагины,
// сами настройки живут в app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
