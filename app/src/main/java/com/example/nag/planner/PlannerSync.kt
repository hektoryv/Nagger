package com.example.nag.planner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fetches and parses the ICS feed off the main thread. Thin glue, not unit tested — see [IcsFetcher]. */
object PlannerSync {

    suspend fun sync(feedUrl: String): Result<List<PlannerEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = IcsFetcher.fetch(feedUrl)
            IcsParser.parse(text)
        }
    }
}
