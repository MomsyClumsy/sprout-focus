package com.sprout.focus.data

/**
 * Причины, по которым не получается начать.
 *
 * Это самое ценное поле во всём приложении: больше его нигде не собирают.
 * На нём строится главный вывод аналитики — «ты откладываешь из-за тревоги,
 * а не из-за скуки, значит помогают не таймеры, а снижение планки».
 */
object CantStartReason {
    const val ANXIETY = "anxiety"          // боюсь, что не получится
    const val BOREDOM = "boredom"          // скучно
    const val NO_ENERGY = "no_energy"      // нет сил
    const val TOO_BIG = "too_big"          // слишком большая
    const val NO_MEANING = "no_meaning"    // не хочу, бессмысленно
    const val DISTRACTED = "distracted"    // отвлекаюсь
}

/** Чем закончился разговор. Нужно, чтобы понимать, какие приёмы срабатывают. */
object CantStartResolution {
    const val STARTED_SMALLER = "started_smaller"   // запустили сессию покороче
    const val SPLIT = "split"                       // разбили задачу
    const val FOUND_MEANING = "found_meaning"       // записали, зачем это нужно
    const val DROPPED = "dropped"                   // честно отказались
    const val POSTPONED = "postponed"               // отложили на потом
}
