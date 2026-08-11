import java.util.Properties

/**
 * Ключ подписи выпуска.
 *
 * Лежит вне репозитория: в git не должно быть ни ключа, ни пароля к нему.
 * Файла нет — релиз просто не подпишется этим ключом, и сборка об этом
 * скажет прямо, а не соберёт молча что-то другое.
 *
 * Копия ключа и пароля — в OneDrive, папка «ключ-подписи». Потерянный ключ
 * означает, что обновление не встанет поверх установленного приложения:
 * человеку придётся удалить его вместе со всеми своими данными.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    // Плагин Kotlin отдельно не подключается: начиная с AGP 9
    // поддержка Kotlin встроена и включена по умолчанию.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sprout.focus"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sprout.focus"
        minSdk = 26          // Android 8 — покрывает почти все живые устройства
        targetSdk = 37
        // versionCode растёт на единицу с каждым выпуском: по нему Android
        // понимает, что новее. versionName — то, что видит человек
        versionCode = 4
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Сокращение кода выключено намеренно. Room и Glance работают
            // через сгенерированные классы, и правила для R8 к ним пришлось бы
            // подбирать; выигрыш в размере у приложения на пару мегабайт
            // не стоит поломки, которая проявится только в релизной сборке —
            // то есть у людей, а не здесь.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Нужен ради версии приложения: она пишется в файл копии, чтобы потом
        // было видно, из какого Sprout эти данные
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // BOM выравнивает версии всех compose-библиотек между собой
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // Настоящая org.json для тестов: та, что в Android SDK, в юнит-тестах
    // подменена заглушками и на любой вызов бросает «not mocked».
    // Копия данных разбирается именно ею, и проверить её надо по-настоящему
    testImplementation(libs.json)

    // На устройстве: миграции базы и сквозной путь по экранам
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
