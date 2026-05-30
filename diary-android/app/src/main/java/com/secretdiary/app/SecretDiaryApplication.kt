package com.secretdiary.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.secretdiary.app.ui.components.AttachmentFetcher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SecretDiaryApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var attachmentFetcherFactory: AttachmentFetcher.Factory

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(attachmentFetcherFactory)
            }
            .build()
    }
}
