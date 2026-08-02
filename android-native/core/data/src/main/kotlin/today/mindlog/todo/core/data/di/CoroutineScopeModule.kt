package today.mindlog.todo.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Outlives any screen — for work tied to the process, not to a ViewModel. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope =
        // SupervisorJob: one failing collector must not take the others down
        // with it.
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
