package com.bloklan

import android.app.Application
import com.bloklan.data.repository.AppRepository

class BloklanApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize repository singleton with context
        AppRepository.instance.init(this)
    }
}
