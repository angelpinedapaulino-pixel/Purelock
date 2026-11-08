package com.purelock

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * Se ejecuta (a) cada ~15 min y (b) cuando cambia el ajuste de DNS privado del sistema.
 * Si alguien lo cambió, lo restaura. No necesita estar la app abierta.
 */
class EnforceJobService : JobService() {
    override fun onStartJob(p: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            try {
                if (Store.armed(app) && Enforcer.isOwner(app)) {
                    Enforcer.apply(app)
                    Enforcer.scheduleWatchers(app)   // el disparador por cambio es de un solo uso
                }
            } finally { jobFinished(p, false) }
        }.start()
        return true
    }
    override fun onStopJob(p: JobParameters) = false
}
