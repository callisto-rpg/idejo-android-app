// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.model.UnifiedPushSubscription

/** Which UnifiedPush distributor takes this device's pushes (NT-06, ST-20). */
enum class DistributorChoice {
    /** One is chosen, or the only one there is (the embedded FCM distributor included). */
    Ready,

    /** Several are installed and none is chosen: the member picks one (ST-20). */
    ToSelect,

    /** None: no distributor app, and no Google Play services or microG for FCM. */
    NoneAvailable,
}

/**
 * The UnifiedPush distributor, behind the connector (NT-02): one registration per forum,
 * its `instance` the forum's origin. Its answers come back through [PushRegistrar].
 */
interface PushDistributor {
    fun choice(): DistributorChoice

    /** Asks for [instance]'s endpoint; [vapid] signs the forum's pushes (FCM needs it). */
    fun register(
        instance: String,
        name: String,
        vapid: String?,
    )

    fun unregister(instance: String)

    /** The distributors installed, by package; the app's own for its embedded FCM. */
    fun installed(): List<String> = emptyList()

    /** The distributor in use, by package; null when none is chosen. */
    fun current(): String? = null

    /** Uses [distributor] from now on (ST-20); the one before ends its registrations. */
    fun use(distributor: String) = Unit

    /**
     * NT-06, one tap: the distributor in use, else the default one, an installed one (ntfy,
     * Sunup…) before the app's own FCM; Android may ask the member to choose, hence
     * [activity]. [done] says whether there is one.
     */
    fun useDefault(
        activity: Activity,
        done: (Boolean) -> Unit,
    ) = done(false)

    /** Push turned off: every registration ends and the distributor is forgotten. */
    fun removeAll() = Unit
}

/** How push stands on this device for the forum open (NT-06, NT-08). */
sealed interface PushStatus {
    /** A visitor, a forum without push, or not asked yet. */
    data object Off : PushStatus

    /** No distributor is installed: settings explain how to get one (NT-06). */
    data object NoDistributor : PushStatus

    /** Several distributors and none chosen (ST-20). */
    data object ChooseDistributor : PushStatus

    data object Registering : PushStatus

    data object Registered : PushStatus

    /** The distributor gave up ([reason], its words for it) or the forum refused. */
    data class Failed(
        val reason: String? = null,
        val problem: ApiProblem? = null,
    ) : PushStatus
}

/**
 * Registers this device for every signed-in forum's pushes (NT-02): asks the distributor
 * for the forum's endpoint, sends it with its Web Push keys (`POST /me/push/unifiedpush`,
 * the device's name), sends it again when the distributor changes it, and ends it when the
 * forum is signed out of. Nothing polls (NT-09): outside the foreground, pushes are all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One per thing push does or hears.
class PushRegistrar(
    private val sessions: SessionManager,
    private val distributor: PushDistributor,
    private val deviceName: String,
    private val scope: CoroutineScope,
) {
    private val mutableStatus = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = mutableStatus.asStateFlow()
    private val lock = Mutex()

    fun start() {
        // Subscribed before start() returns: a sign-out right after is not missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.signedOut.collect { forget(it) }
        }
        scope.launch {
            sessions.launch
                .flatMapLatest { launch ->
                    val session = (launch as? Launch.Forum)?.session
                    session?.access?.map { session to it } ?: flowOf(null)
                }.collectLatest { opened ->
                    val (session, access) = opened ?: return@collectLatest
                    if (access == Access.SignedIn) {
                        ensure(session)
                    } else {
                        mutableStatus.value = PushStatus.Off
                    }
                }
        }
        // idejo ID mode: the inbox (its private messages) whatever is open.
        scope.launch {
            sessions.state
                .map { state ->
                    val inbox = state.inbox?.takeIf { state.mode == AppMode.IdejoId }
                    InboxPush(
                        inbox?.origin?.takeIf { inbox.sealedToken != null && !inbox.signedOut },
                        on = state.privatePush && !state.pushOff,
                        pushOff = state.pushOff,
                    )
                }.distinctUntilChanged()
                .collectLatest { inbox(it) }
        }
    }

    /** What the inbox's push depends on: its origin while signed in, and the switches. */
    private data class InboxPush(
        val origin: String?,
        val on: Boolean,
        val pushOff: Boolean,
    )

    /**
     * idejo ID mode's inbox, registered while **Private messages** is on (by default) and
     * push is; switched off alone, its registration ends (push turned off ends it already).
     */
    private suspend fun inbox(push: InboxPush) {
        val origin = push.origin ?: return
        when {
            push.on -> {
                val record = sessions.state.first().record(origin) ?: return
                if (UNIFIED_PUSH !in record.discovery.features) return
                val server = call(origin) { server() }?.getOrNull() ?: return
                if (server.settings.push != true) return
                if (distributor.choice() != DistributorChoice.Ready) return
                distributor.register(origin, record.name, server.settings.pushVapidKey)
            }

            !push.pushOff -> {
                lock.withLock {
                    sessions.state.first().record(origin)?.push?.let { old ->
                        call(origin) { deletePushRegistration(old.registrationId) }
                    }
                    sessions.updateForum(origin) { it.copy(push = null) }
                    distributor.unregister(origin)
                }
            }
        }
    }

    /** The distributors to choose from (ST-20), and the one in use. */
    fun distributors(): Pair<List<String>, String?> =
        distributor.installed() to distributor.current()

    /** [packageName] chosen in App settings (ST-20): the forum is registered through it. */
    fun use(packageName: String) {
        distributor.use(packageName)
        retry()
    }

    /** A distributor was chosen (ST-20), or the member asks again: register now. */
    fun retry() {
        scope.launch {
            sessions.active?.takeIf { it.access.value == Access.SignedIn }?.let {
                ensure(
                    it,
                )
            }
        }
    }

    /**
     * **Enable push notifications** (NT-06): the distributor there is, or the default one;
     * the forum open registers through it. [done] is false when there is none at all.
     */
    fun enable(
        activity: Activity,
        done: (Boolean) -> Unit,
    ) {
        distributor.useDefault(activity) { found ->
            if (found) retry()
            done(found)
        }
    }

    /**
     * Push turned off on this device (the flag is set first): every forum's registration
     * ends, on the forum and with the distributor.
     */
    suspend fun disable() =
        lock.withLock {
            val state = sessions.state.first()
            (state.forums + listOfNotNull(state.inbox)).forEach { record ->
                val push = record.push ?: return@forEach
                sessions.signedIn(record.origin) { deletePushRegistration(push.registrationId) }
                sessions.updateForum(record.origin) { it.copy(push = null) }
            }
            distributor.removeAll()
            mutableStatus.value = PushStatus.Off
        }

    /** Push turned off on this device (App settings, NT-06). */
    private suspend fun pushOff() = sessions.state.first().pushOff

    /** NT-02: the forum's push, if it has any, through the distributor there is. */
    private suspend fun ensure(session: ForumSession) {
        if (pushOff()) {
            mutableStatus.value = PushStatus.Off
            return
        }
        val record = session.record.value
        if (UNIFIED_PUSH !in record.discovery.features) {
            mutableStatus.value = PushStatus.Off
            return
        }
        val server = session.call { server() }.getOrNull()
        if (server?.settings?.push != true) {
            mutableStatus.value = PushStatus.Off
            return
        }
        mutableStatus.value =
            when (distributor.choice()) {
                DistributorChoice.NoneAvailable -> {
                    PushStatus.NoDistributor
                }

                DistributorChoice.ToSelect -> {
                    PushStatus.ChooseDistributor
                }

                DistributorChoice.Ready -> {
                    // The connector answers with the endpoint it has, or a new one.
                    distributor.register(session.origin, session.name, server.settings.pushVapidKey)
                    if (record.push != null) PushStatus.Registered else PushStatus.Registering
                }
            }
    }

    /**
     * The distributor's endpoint for [instance] (a new one, or the same again): sent to the
     * forum unless it already has it with these keys; a replaced one is ended first. Any
     * forum held with its token, open or not: the distributor may change an endpoint while
     * the app is closed (FCM renews its tokens).
     */
    suspend fun onEndpoint(
        instance: String,
        endpoint: String,
        publicKey: String,
        auth: String,
    ) = lock.withLock {
        val record = sessions.state.first().record(instance)
        if (record == null || !mayRegister(instance)) {
            distributor.unregister(instance)
            return@withLock
        }
        val vapid =
            call(instance) { server() }
                ?.getOrNull()
                ?.settings
                ?.pushVapidKey
        val kept = record.push
        if (kept != null && kept.endpoint == endpoint && kept.publicKey == publicKey) {
            if (kept.vapidKey != vapid) {
                sessions.updateForum(instance) { it.copy(push = kept.copy(vapidKey = vapid)) }
            }
            status(instance, PushStatus.Registered)
            return@withLock
        }
        kept?.let { old -> call(instance) { deletePushRegistration(old.registrationId) } }
        val subscription = UnifiedPushSubscription(endpoint, publicKey, auth, deviceName)
        when (val result = call(instance) { registerPush(subscription) }) {
            is ApiResult.Success -> {
                val push = PushRecord(result.value.id, endpoint, publicKey, vapid)
                sessions.updateForum(instance) { it.copy(push = push) }
                status(instance, PushStatus.Registered)
            }

            is ApiResult.Failure -> {
                sessions.updateForum(instance) { it.copy(push = null) }
                status(instance, PushStatus.Failed(problem = result.problem))
            }

            null -> {
                Unit
            }
        }
    }

    /**
     * Whether [instance] may take pushes now: held with its token, signed in where open,
     * push on, and for the inbox **Private messages** on.
     */
    private suspend fun mayRegister(instance: String): Boolean {
        val state = sessions.state.first()
        val open = sessions.active?.takeIf { it.origin == instance }
        return when {
            state.pushOff -> false
            state.record(instance)?.sealedToken == null -> false
            open != null && open.access.value != Access.SignedIn -> false
            else -> state.inbox?.origin != instance || state.privatePush
        }
    }

    /** The distributor dropped [instance] (removed, or its server gone): the forum's too. */
    suspend fun onUnregistered(instance: String) =
        lock.withLock {
            sessions.state.first().record(instance)?.push?.let { old ->
                call(instance) { deletePushRegistration(old.registrationId) }
            }
            sessions.updateForum(instance) { it.copy(push = null) }
            status(instance, if (pushOff()) PushStatus.Off else PushStatus.ChooseDistributor)
        }

    /**
     * [request] to the forum at [instance]: through its session when it is open (a refusal
     * signs it out there), else with its own token; null when the app holds none.
     */
    private suspend fun <T> call(
        instance: String,
        request: suspend org.idejo.app.core.api.ForumApi.() -> ApiResult<T>,
    ): ApiResult<T>? =
        sessions.active?.takeIf { it.origin == instance }?.call(request)
            ?: sessions.signedIn(instance, request)

    /** The status shown is the open forum's. */
    private fun status(
        instance: String,
        status: PushStatus,
    ) {
        if (sessions.active?.origin == instance) mutableStatus.value = status
    }

    /** The distributor could not register [instance], for [reason]. */
    fun onFailed(
        instance: String,
        reason: String,
    ) {
        if (sessions.active?.origin == instance) mutableStatus.value = PushStatus.Failed(reason)
    }

    /** Signed out of [origin]: its registration ended with its token; the endpoint goes too. */
    private suspend fun forget(origin: String) =
        lock.withLock {
            distributor.unregister(origin)
            sessions.updateForum(origin) { it.copy(push = null) }
            if (sessions.active?.origin == origin) mutableStatus.value = PushStatus.Off
        }

    private companion object {
        const val UNIFIED_PUSH = "unifiedpush"
    }
}
