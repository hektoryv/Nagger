package com.example.nag.notify

import android.content.BroadcastReceiver
import android.content.Context
import com.example.nag.planner.IcsFetcher
import com.example.nag.planner.IcsParser
import com.example.nag.planner.PlannerStore
import com.example.nag.widget.NagWidget
import kotlin.concurrent.thread

/**
 * Re-fetches the class schedule off the main thread, piggybacking on the nightly
 * habit check-in alarm rather than a second alarm — one nightly wake is enough.
 *
 * A BroadcastReceiver can't block on network I/O directly (NetworkOnMainThreadException),
 * so this needs [android.content.BroadcastReceiver.goAsync]'s PendingResult, finished
 * from a plain background thread once the fetch completes or fails. Best-effort: a
 * failure here is silent and just leaves last session's cached classes in place until
 * the next nightly try, app open, or manual "Sync now".
 */
object PlannerSyncWork {

    fun syncIfLinked(context: Context, pendingResult: BroadcastReceiver.PendingResult?) {
        val url = PlannerStore.loadFeedUrl(context)
        if (url == null) {
            pendingResult?.finish()
            return
        }
        thread(name = "planner-nightly-sync") {
            runCatching {
                val events = IcsParser.parse(IcsFetcher.fetch(url))
                PlannerStore.saveEvents(context, events)
                NagWidget.refresh(context)
            }
            pendingResult?.finish()
        }
    }
}
