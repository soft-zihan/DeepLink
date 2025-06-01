package com.example.aggregatesearch

import android.app.Application
import com.example.aggregatesearch.data.SearchUrlDatabase
import com.example.aggregatesearch.data.SearchUrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SearchApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { SearchUrlDatabase.getDatabase(this) }
    val repository by lazy { SearchUrlRepository(database.searchUrlDao()) }
}
