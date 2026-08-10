package com.sprout.focus.focusguard

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.sprout.focus.MainActivity
import com.sprout.focus.R
import com.sprout.focus.SproutApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Экран, который появляется поверх отвлекающего приложения.
 *
 * Одно окно на всё приложение: барьеров не бывает два сразу, а хранить
 * ссылку на него надо, чтобы было чем убрать. Всё, что здесь есть, —
 * напоминание, за чем человек сел, и две честные двери наружу.
 */
object BarrierWindow {

    private var view: View? = null
    private val main = Handler(Looper.getMainLooper())

    fun show(context: Context, packageName: String, taskTitle: String, endsAt: Long?) {
        main.post { showOnMainThread(context, packageName, taskTitle, endsAt) }
    }

    fun hide() {
        main.post {
            val current = view ?: return@post
            val wm = current.context.getSystemService(WindowManager::class.java)
            runCatching { wm?.removeView(current) }
            view = null
        }
    }

    private fun showOnMainThread(
        context: Context,
        packageName: String,
        taskTitle: String,
        endsAt: Long?,
    ) {
        if (view != null) return
        val wm = context.getSystemService(WindowManager::class.java) ?: return

        val content = LayoutInflater.from(context).inflate(R.layout.barrier, null)
        content.findViewById<TextView>(R.id.barrier_task).text =
            taskTitle.ifBlank { "Идёт работа" }
        content.findViewById<TextView>(R.id.barrier_until).text = when (endsAt) {
            null -> "Сессия идёт"
            else -> "Сессия до ${formatTime(endsAt)}"
        }

        val app = context.applicationContext as SproutApplication
        val answer = { returned: Boolean ->
            CoroutineScope(Dispatchers.IO).launch {
                val taskId = app.database.dao().getActiveSession()?.taskId
                app.guard.answered(packageName, taskId, returned)
            }
        }

        content.findViewById<TextView>(R.id.barrier_return).setOnClickListener {
            answer(true)
            hide()
            // Открываем Sprout, а не просто убираем окно: под барьером
            // осталась лента, и человек вернётся в неё через полсекунды.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        content.findViewById<TextView>(R.id.barrier_pass).setOnClickListener {
            answer(false)
            GuardService.pass(context, packageName)
            hide()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Окно ловит касания, но не забирает клавиатуру: печатать здесь
            // нечего, а перехват ввода — уже вмешательство не по делу.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE,
        )

        runCatching {
            wm.addView(content, params)
            view = content
        }
    }

    private fun formatTime(at: Long): String {
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }
}
