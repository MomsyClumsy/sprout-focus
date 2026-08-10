package com.sprout.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Приложение, которое человек сам назвал отвлекающим.
 *
 * Список ведётся вручную. Приложение не решает за человека, что ему вредно:
 * одному лента новостей — побег от работы, другому — работа. Автоматический
 * выбор к тому же начинал бы знакомство с «вот куда ушли твои часы», а это
 * ровно тот упрёк, которого в приложении быть не должно.
 *
 * [label] хранится рядом с именем пакета, чтобы барьер и список настроек
 * не лезли за ним в систему каждый раз — и продолжали читаться, даже если
 * приложение потом удалили.
 */
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val addedAt: Long = System.currentTimeMillis(),
)
