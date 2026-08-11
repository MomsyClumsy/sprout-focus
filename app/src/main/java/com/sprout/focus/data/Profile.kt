package com.sprout.focus.data

import android.content.Context

/**
 * Как приложение обращается к человеку.
 *
 * Нужен ровно затем, чтобы не говорить «начнёшь ты сама» тому, кто «сам».
 * Всё остальное — возраст, пол как таковой, что угодно ещё — приложение
 * не спрашивает: поле, которым нечего менять, это требование без ответа.
 */
enum class Gender {
    /** «ты сама», «ты оставила» */
    FEMININE,

    /** «ты сам», «ты оставил» */
    MASCULINE,

    /**
     * Не сказал(а) — и не надо.
     *
     * Тогда приложение говорит безлично: не «начнёшь ты сама», а
     * «начинать тебе». Это не запасной вариант на крайний случай,
     * а обычный: пропустить знакомство — законный выбор.
     */
    UNKNOWN;

    companion object {
        fun of(raw: String?): Gender = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

/**
 * Голос приложения: имя человека и форма обращения.
 *
 * Живёт отдельно от экранов, чтобы одну и ту же фразу можно было проверить
 * тестом во всех трёх видах. Тон при этом не меняется: словарь запрещённых
 * слов действует одинаково и для «сама», и для «сам», и для безличного.
 */
data class Voice(
    val name: String? = null,
    val gender: Gender = Gender.UNKNOWN,
) {
    /**
     * Выбрать форму фразы.
     *
     * Безличный вариант обязателен и пишется первым по важности: им
     * приложение говорит с каждым, кто не стал заполнять знакомство.
     */
    fun say(feminine: String, masculine: String, neutral: String): String = when (gender) {
        Gender.FEMININE -> feminine
        Gender.MASCULINE -> masculine
        Gender.UNKNOWN -> neutral
    }

    /** «Марина, » — или пусто, если имени нет. Пустое имя не заменяем на «друг». */
    val address: String get() = name?.takeIf { it.isNotBlank() }?.let { "$it, " }.orEmpty()

    /** Имя с заглавной для заголовков: «Привет, Марина». */
    val nameOrNull: String? get() = name?.takeIf { it.isNotBlank() }

    val isKnown: Boolean get() = nameOrNull != null || gender != Gender.UNKNOWN
}

/**
 * Профиль в настройках, а не в базе.
 *
 * Это не событие и не данные о том, что человек делал, — это одна строка
 * и один переключатель, которые он про себя сказал. Заодно они попадают
 * в копию данных вместе с остальными настройками.
 */
class ProfileRepository(context: Context) {

    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    var voice: Voice
        get() = Voice(
            name = prefs.getString(KEY_NAME, null),
            gender = Gender.of(prefs.getString(KEY_GENDER, null)),
        )
        set(value) = prefs.edit()
            .putString(KEY_NAME, value.name?.trim()?.takeIf { it.isNotEmpty() })
            .putString(KEY_GENDER, value.gender.name)
            .apply()

    /**
     * Знакомство показано.
     *
     * Отдельно от заполненности профиля: человек мог пройти знакомство
     * и не назвать имени. Спрашивать второй раз — значит не услышать
     * первый отказ.
     */
    var metPerson: Boolean
        get() = prefs.getBoolean(KEY_MET, false)
        set(value) = prefs.edit().putBoolean(KEY_MET, value).apply()

    companion object {
        const val KEY_NAME = "name"
        const val KEY_GENDER = "gender"
        const val KEY_MET = "met"
    }
}
