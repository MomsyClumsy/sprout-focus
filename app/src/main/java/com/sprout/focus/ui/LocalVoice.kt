package com.sprout.focus.ui

import androidx.compose.runtime.compositionLocalOf
import com.sprout.focus.data.Voice

/**
 * Голос приложения для любого экрана.
 *
 * Через CompositionLocal, а не параметром: обращение всплывает в десятке
 * разных мест, и протаскивать его через каждый экран значило бы менять
 * подписи функций ради одного слова. По умолчанию — безличный: пока
 * человек ничего о себе не сказал, приложение и не выдумывает.
 */
val LocalVoice = compositionLocalOf { Voice() }
