package com.woocommerce.android.analytics

import android.content.Context
import java.util.HashMap
import com.automattic.android.tracks.TracksClient
import android.preference.PreferenceManager
import java.util.UUID
import org.json.JSONObject


class AnalyticsTracker constructor(context: Context) {
    enum class Stat {
        SIGNED_IN,
        LOGIN_ACCESSED,
        LOGIN_MAGIC_LINK_EXITED,
        LOGIN_MAGIC_LINK_FAILED,
        LOGIN_MAGIC_LINK_OPENED,
        LOGIN_MAGIC_LINK_REQUESTED,
        LOGIN_MAGIC_LINK_SUCCEEDED,
        LOGIN_FAILED,
        LOGIN_INSERTED_INVALID_URL,
        LOGIN_AUTOFILL_CREDENTIALS_FILLED,
        LOGIN_AUTOFILL_CREDENTIALS_UPDATED,
        LOGIN_EMAIL_FORM_VIEWED,
        LOGIN_MAGIC_LINK_OPEN_EMAIL_CLIENT_VIEWED,
        LOGIN_MAGIC_LINK_OPEN_EMAIL_CLIENT_CLICKED,
        LOGIN_MAGIC_LINK_REQUEST_FORM_VIEWED,
        LOGIN_PASSWORD_FORM_VIEWED,
        LOGIN_URL_FORM_VIEWED,
        LOGIN_URL_HELP_SCREEN_VIEWED,
        LOGIN_USERNAME_PASSWORD_FORM_VIEWED,
        LOGIN_TWO_FACTOR_FORM_VIEWED,
        LOGIN_FORGOT_PASSWORD_CLICKED,
        LOGIN_SOCIAL_BUTTON_CLICK,
        LOGIN_SOCIAL_BUTTON_FAILURE,
        LOGIN_SOCIAL_CONNECT_SUCCESS,
        LOGIN_SOCIAL_CONNECT_FAILURE,
        LOGIN_SOCIAL_SUCCESS,
        LOGIN_SOCIAL_FAILURE,
        LOGIN_SOCIAL_2FA_NEEDED,
        LOGIN_SOCIAL_ACCOUNTS_NEED_CONNECTING,
        LOGIN_SOCIAL_ERROR_UNKNOWN_USER,
        LOGIN_WPCOM_BACKGROUND_SERVICE_UPDATE,
        ADDED_SELF_HOSTED_SITE,
        APPLICATION_OPENED,
        APPLICATION_CLOSED
    }

    private var tracksClient = TracksClient.getClient(context)
    private val context = context
    private var username: String? = null
    private var anonymousID: String? = null

    private fun clearAnonID() {
        anonymousID = null
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (preferences.contains(TRACKS_ANON_ID)) {
            val editor = preferences.edit()
            editor.remove(TRACKS_ANON_ID)
            editor.apply()
        }
    }

    private fun getAnonID(): String? {
        if (anonymousID == null) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            anonymousID = preferences.getString(TRACKS_ANON_ID, null)
        }
        return anonymousID
    }

    private fun generateNewAnonID(): String {
        val uuid = UUID.randomUUID().toString()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = preferences.edit()
        editor.putString(TRACKS_ANON_ID, uuid)
        editor.apply()

        anonymousID = uuid
        return uuid
    }

    private fun track(stat: Stat, properties: Map<String, *>) {
        if (tracksClient == null) {
            return
        }

        val eventName = stat.name.toLowerCase()

        val user: String
        val userType: TracksClient.NosaraUserType
        if (username != null) {
            user = username!!
            userType = TracksClient.NosaraUserType.WPCOM
        } else {
            // This is just a security checks since the anonID is already available here.
            // refresh metadata is called on login/logout/startup and it loads/generates the anonId when necessary.
            if (getAnonID() == null) {
                user = generateNewAnonID()
            } else {
                user = getAnonID()!!
            }
            userType = TracksClient.NosaraUserType.ANON
        }

        if (properties != null) {
            val propertiesJson = JSONObject(properties)
            tracksClient.track(EVENTS_PREFIX + eventName, propertiesJson, user, userType)
        } else {
            tracksClient.track(EVENTS_PREFIX + eventName, user, userType)
        }
    }

    private fun flush() {
        tracksClient.flush()
    }

    private fun refreshMetadata(newUsername: String) {
        if (newUsername.isNotEmpty()) {
            username = newUsername;
            if (getAnonID() != null) {
                tracksClient.trackAliasUser(username, getAnonID(), TracksClient.NosaraUserType.WPCOM);
                clearAnonID();
            }
        } else {
            username = null;
            if (getAnonID() == null) {
                generateNewAnonID();
            }
        }
    }

    companion object {
        private lateinit var instance: AnalyticsTracker
        private const val TRACKS_ANON_ID = "nosara_tracks_anon_id"
        private const val EVENTS_PREFIX = "woocommerceandroid_"

        fun track(stat: Stat) {
            track(stat, emptyMap<String, String>())
        }

        fun track(stat: Stat, properties: Map<String, *>) {
            instance.track(stat, properties)
        }

        /**
         * A convenience method for logging an error event with some additional meta data.
         * @param stat The stat to track.
         * @param errorContext A string providing additional context (if any) about the error.
         * @param errorType The type of error.
         * @param errorDescription The error text or other description.
         */
        fun track(stat: Stat, errorContext: String, errorType: String, errorDescription: String) {
            val props = HashMap<String, String>()
            props.put("error_context", errorContext)
            props.put("error_type", errorType)
            props.put("error_description", errorDescription)
            track(stat, props)
        }

        fun flush() {
            instance.flush()
        }

        fun refreshMetadata(username: String) {
            instance.refreshMetadata(username)
        }
    }
}
