package com.sprout.focus

import android.app.Application
import com.sprout.focus.data.GardenRepository
import com.sprout.focus.data.InsightsRepository
import com.sprout.focus.data.PlanRepository
import com.sprout.focus.data.SessionRepository
import com.sprout.focus.data.SproutDatabase
import com.sprout.focus.data.TaskRepository
import com.sprout.focus.plan.PlanNotifications
import com.sprout.focus.timer.FocusNotifications

/**
 * Держит базу и репозиторий на всё время жизни приложения.
 * by lazy — база откроется при первом обращении, а не на старте,
 * чтобы не замедлять запуск.
 */
class SproutApplication : Application() {
    val database by lazy { SproutDatabase.build(this) }
    val repository by lazy { TaskRepository(database.dao()) }
    val garden by lazy { GardenRepository(database.dao()) }
    val sessions by lazy { SessionRepository(database.dao(), this, garden) }
    val plans by lazy { PlanRepository(database.dao(), this) }
    val insights by lazy { InsightsRepository(database.dao()) }

    override fun onCreate() {
        super.onCreate()
        FocusNotifications.createChannels(this)
        PlanNotifications.createChannel(this)
    }
}
