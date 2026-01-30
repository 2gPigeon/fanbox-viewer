package com.example.fanboxviewer.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.tutorialDataStore by preferencesDataStore(name = "tutorial_prefs")

object TutorialPrefs {
    val CreatorsShown = booleanPreferencesKey("tutorial_creators_shown")
    val PostsSyncShown = booleanPreferencesKey("tutorial_posts_sync_shown")
    val PostsActionsShown = booleanPreferencesKey("tutorial_posts_actions_shown")
    val PostsHasSynced = booleanPreferencesKey("tutorial_posts_has_synced")

    fun shownFlow(context: Context, key: Preferences.Key<Boolean>): Flow<Boolean> {
        return context.tutorialDataStore.data
            .catch { ex ->
                if (ex is IOException) emit(emptyPreferences()) else throw ex
            }
            .map { prefs -> prefs[key] ?: false }
    }

    fun shownFlowNullable(context: Context, key: Preferences.Key<Boolean>): Flow<Boolean?> {
        return context.tutorialDataStore.data
            .catch { ex ->
                if (ex is IOException) emit(emptyPreferences()) else throw ex
            }
            .map { prefs -> prefs[key] }
    }

    suspend fun setShown(context: Context, key: Preferences.Key<Boolean>, shown: Boolean = true) {
        context.tutorialDataStore.edit { prefs -> prefs[key] = shown }
    }
}
