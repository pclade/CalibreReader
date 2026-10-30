package ch.sakru.calibrereader.app

import android.app.Application

class CalibreReaderApplication : Application() {
    val services: CalibreReaderApp by lazy { CalibreReaderApp(this) }
}
