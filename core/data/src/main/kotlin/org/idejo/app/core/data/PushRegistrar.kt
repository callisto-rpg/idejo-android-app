// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    /** One is chosen, or the only one there is (Play's embedded FCM included). */
    Ready,

    /** Several are installed and none is chosen: the member picks one (ST-20). */
    ToSelect,

    /** None is installed (the foss build without ntfy or the like). */
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

    /** The distributors installed, by package; the app's own for Play's embedded FCM. */
    fun installed(): List<String> = emptyList()

    /** The distributor in use, by package; null when none is chosen. */
    fun current(): String? = null

    /** Uses [distributor] from now on (ST-20); the one before ends its registrations. */
    fun use(distributor: String) = Unit
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

    /** NT-02: the forum's push, if it has any, through the distributor there is. */
    private suspend fun ensure(session: ForumSession) {
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
     * forum unless it already has it with these keys; a replaced one is ended first.
     */
    suspend fun onEndpoint(
        instance: String,
        endpoint: String,
        publicKey: String,
        auth: String,
    ) = lock.withLock {
        val session = sessions.active?.takeIf { it.origin == instance }
        if (session == null || session.access.value != Access.SignedIn) {
            distributor.unregister(instance)
            return@withLock
        }
        val vapid =
            session
                .call { server() }
                .getOrNull()
                ?.settings
                ?.pushVapidKey
        val kept = session.record.value.push
        if (kept != null && kept.endpoint == endpoint && kept.publicKey == publicKey) {
            if (kept.vapidKey != vapid) {
                sessions.updateForum(instance) { it.copy(push = kept.copy(vapidKey = vapid)) }
            }
            mutableStatus.value = PushStatus.Registered
            return@withLock
        }
        kept?.let { old -> session.call { deletePushRegistration(old.registrationId) } }
        val subscription = UnifiedPushSubscription(endpoint, publicKey, auth, deviceName)
        when (val result = session.call { registerPush(subscription) }) {
            is ApiResult.Success -> {
                val push = PushRecord(result.value.id, endpoint, publicKey, vapid)
                sessions.updateForum(instance) { it.copy(push = push) }
                mutableStatus.value = PushStatus.Registered
            }

            is ApiResult.Failure -> {
                sessions.updateForum(instance) { it.copy(push = null) }
                mutableStatus.value = PushStatus.Failed(problem = result.problem)
            }
        }
    }

    /** The distributor dropped [instance] (removed, or its server gone): the forum's too. */
    suspend fun onUnregistered(instance: String) =
        lock.withLock {
            val session = sessions.active?.takeIf { it.origin == instance }
            session?.record?.value?.push?.let { old ->
                session.call { deletePushRegistration(old.registrationId) }
            }
            sessions.updateForum(instance) { it.copy(push = null) }
            if (session != null) mutableStatus.value = PushStatus.ChooseDistributor
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
