package com.arjun.tasknest.di

import android.content.Context
import androidx.room.Room
import com.arjun.tasknest.BuildConfig
import com.arjun.tasknest.data.local.DataStoreSyncCursorStore
import com.arjun.tasknest.data.local.SyncCursorStore
import com.arjun.tasknest.data.local.TaskNestDatabase
import com.arjun.tasknest.data.local.TaskDao
import com.arjun.tasknest.data.remote.TaskApi
import com.arjun.tasknest.data.repository.OfflineFirstTaskRepository
import com.arjun.tasknest.data.sync.SyncScheduler
import com.arjun.tasknest.data.sync.WorkManagerSyncScheduler
import com.arjun.tasknest.domain.TaskRepository
import com.arjun.tasknest.util.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskNestDatabase =
        Room.databaseBuilder(context, TaskNestDatabase::class.java, "tasknest.db").build()

    @Provides
    fun provideTaskDao(db: TaskNestDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()

    @Provides
    @Singleton
    fun provideTaskApi(client: OkHttpClient, json: Json): TaskApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TaskApi::class.java)

    @Provides
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindTaskRepository(impl: OfflineFirstTaskRepository): TaskRepository

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindSyncCursorStore(impl: DataStoreSyncCursorStore): SyncCursorStore
}
