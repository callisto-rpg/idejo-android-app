// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.notifications

import android.app.Activity
import android.content.Context
import org.idejo.app.core.data.DistributorChoice
import org.idejo.app.core.data.PushDistributor
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor

/**
 * UnifiedPush's connector (NT-02, NT-06): the distributor chosen, or the one there is. In
 * the Play build the embedded FCM distributor is one of them, the only one when no other is
 * installed; the foss build has none of its own. The connector keeps the Web Push keys in
 * the Android Keystore and decrypts the pushes (RFC 8291) itself.
 */
class UnifiedPushDistributor(
    private val context: Context,
) : PushDistributor {
    override fun choice(): DistributorChoice {
        if (UnifiedPush.getAckDistributor(context) != null) return DistributorChoice.Ready
        return when (val resolved = UnifiedPush.resolveDefaultDistributor(context)) {
            is ResolvedDistributor.Found -> {
                UnifiedPush.saveDistributor(context, resolved.packageName)
                DistributorChoice.Ready
            }

            ResolvedDistributor.ToSelect -> {
                DistributorChoice.ToSelect
            }

            ResolvedDistributor.NoneAvailable -> {
                DistributorChoice.NoneAvailable
            }
        }
    }

    override fun register(
        instance: String,
        name: String,
        vapid: String?,
    ) {
        try {
            UnifiedPush.register(context, instance, name, vapid)
        } catch (_: UnifiedPush.VapidNotValidException) {
            // A malformed key from the forum: without it, distributors that ignore VAPID work.
            UnifiedPush.register(context, instance, name, null)
        }
    }

    override fun unregister(instance: String) {
        UnifiedPush.unregister(context, instance)
    }

    /** UnifiedPush's own picker (ST-20); [done] says whether one was chosen. */
    fun pick(
        activity: Activity,
        done: (Boolean) -> Unit,
    ) {
        UnifiedPush.tryPickDistributor(activity, done)
    }
}
