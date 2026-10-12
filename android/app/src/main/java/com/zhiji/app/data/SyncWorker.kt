package com.zhiji.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 周期同步：15 分钟一次（系统最小周期） */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = Repo.get(applicationContext).syncOnce()
        return if (r.error == null) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "zhiji-sync"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun scheduleIfConfigured(ctx: Context) {
            val api = Api(ctx)
            if (api.configured) schedule(ctx)
        }
    }
}
