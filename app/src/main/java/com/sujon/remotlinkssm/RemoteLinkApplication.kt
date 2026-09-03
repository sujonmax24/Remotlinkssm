package com.sujon.remotlinkssm

import android.app.Application
import com.sujon.remotlinkssm.data.local.AppDatabase

class RemoteLinkApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
}
