package com.catdaily.app

import android.app.Application
import com.catdaily.app.data.ProgressStore
import com.catdaily.app.data.QuestionBank
import com.catdaily.app.render.PdfCropRenderer
import com.catdaily.app.remote.Cloud

class CatApp : Application() {
    lateinit var bank: QuestionBank; private set
    lateinit var progress: ProgressStore; private set
    lateinit var renderer: PdfCropRenderer; private set
    lateinit var cloud: Cloud; private set

    override fun onCreate() {
        super.onCreate()
        bank = QuestionBank(this).apply { load() }
        progress = ProgressStore(this).apply { load() }
        renderer = PdfCropRenderer(this)
        cloud = Cloud(this)
    }
}
