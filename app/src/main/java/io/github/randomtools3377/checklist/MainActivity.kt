package io.github.randomtools3377.checklist

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.ui.ChecklistApp
import io.github.randomtools3377.checklist.ui.ChecklistTheme
import io.github.randomtools3377.checklist.ui.MainViewModel

/** A FragmentActivity, so the privacy lock can ask for the phone's own lock. */
class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Privacy.apply(this)
        handle(intent)
        setContent {
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            ChecklistTheme(appearance) {
                ChecklistApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is when misses are found and asked about.
        vm.closeDays()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks what is behind the privacy lock again.
        vm.lockAgain()
    }

    private fun handle(intent: Intent?) {
        val goal = intent?.getLongExtra(EXTRA_SKIP, -1) ?: -1
        if (goal >= 0) {
            vm.requestSkip(goal)
            intent?.removeExtra(EXTRA_SKIP)
        }
        intent?.getStringExtra(EXTRA_OPEN)?.let { tab ->
            vm.openTab.value = tab
            intent.removeExtra(EXTRA_OPEN)
        }
        val timed = intent?.getLongExtra(EXTRA_STOP_TIMING, -1) ?: -1
        if (timed >= 0) {
            vm.openTab.value = "today"
            vm.stopRequest.value = timed
            intent?.removeExtra(EXTRA_STOP_TIMING)
        }
    }

    companion object {
        /** Opens the skip dialog for this goal: a notification's Skip that needs a reason. */
        const val EXTRA_SKIP = "skip_goal"

        /** Opens a tab, like "review" from the weekly summary. */
        const val EXTRA_OPEN = "open_tab"

        /** Stop on the timing notification: asks how much of this goal was done. */
        const val EXTRA_STOP_TIMING = "stop_timing"
    }
}
