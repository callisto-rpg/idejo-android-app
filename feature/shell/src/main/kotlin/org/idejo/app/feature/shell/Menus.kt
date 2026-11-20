// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.theme.onColorFor
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.model.Role as MemberRole

/** Who the member is, for the user menu's head (SH-20). */
data class MemberCard(
    val name: String,
    val tag: String?,
    val roles: List<MemberRole>,
)

/** What the member does in the user menu. */
interface UserMenuActions {
    fun themeMode(mode: ThemeMode)

    fun tutorialBubbles(choice: TutorialBubbles)

    fun profileAndAccount()

    fun appSettings()

    fun admin()

    fun signIn()

    fun signUp()

    fun signOut()

    fun remove()
}

/**
 * The user menu (SH-20 to SH-25): the member's picture, name, tag and roles; **Theme**,
 * **Tutorial bubbles**, **App settings**, **Accounts**, **Admin** for administrators and
 * **Sign out**; **Profile and account** first, for a member signed in. A visitor gets
 * **Sign in** and **Sign up** instead.
 */
@Suppress("LongParameterList") // The menu's state, as the web's menu shows it.
@Composable
fun UserMenu(
    forumName: String,
    member: MemberCard?,
    administrator: Boolean,
    canSignUp: Boolean,
    themeMode: ThemeMode,
    tutorialBubbles: TutorialBubbles,
    actions: UserMenuActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = {},
    logo: String? = null,
    /** **Profile and account** (SH-21): for a member signed in to this forum. */
    profileAndAccount: Boolean = member != null,
) {
    var accounts by rememberSaveable { mutableStateOf(false) }
    if (accounts) {
        Accounts(forumName, member, actions, onBack = { accounts = false }, modifier, logo)
        return
    }
    MenuPage(onClose, modifier) {
        MemberHead(member, avatar)
        if (member == null) {
            Item(R.drawable.ic_chevron, stringResource(R.string.user_menu_sign_in), actions::signIn)
            if (canSignUp) {
                Item(R.drawable.ic_add, stringResource(R.string.user_menu_sign_up), actions::signUp)
            }
        }
        if (profileAndAccount) {
            Item(
                R.drawable.ic_person,
                stringResource(R.string.user_menu_profile_and_account),
                actions::profileAndAccount,
            )
        }
        Preferences(themeMode, tutorialBubbles, actions)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Item(
            R.drawable.ic_settings,
            stringResource(R.string.user_menu_app_settings),
            actions::appSettings,
        )
        Item(
            R.drawable.ic_accounts,
            stringResource(R.string.user_menu_accounts),
            onClick = { accounts = true },
        )
        if (administrator) {
            Item(R.drawable.ic_admin, stringResource(R.string.user_menu_admin), actions::admin)
        }
        if (member != null) {
            Item(
                R.drawable.ic_sign_out,
                stringResource(R.string.user_menu_sign_out),
                actions::signOut,
            )
        }
    }
}

/** SH-20: the member's picture, display name, user tag and roles; a visitor's line. */
@Composable
private fun MemberHead(
    member: MemberCard?,
    avatar: @Composable () -> Unit,
) {
    Row(
        Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        avatar()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                member?.name ?: stringResource(R.string.user_menu_visitor),
                style = MaterialTheme.typography.headlineSmall,
            )
            member?.tag?.let {
                Text(
                    stringResource(R.string.user_menu_tag, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!member?.roles.isNullOrEmpty()) Roles(member.roles)
        }
    }
}

/** SH-21: **Theme** (TH-03), kept per forum, and **Tutorial bubbles**, for the whole app. */
@Composable
private fun Preferences(
    themeMode: ThemeMode,
    tutorialBubbles: TutorialBubbles,
    actions: UserMenuActions,
) {
    Column {
        Heading(stringResource(R.string.user_menu_theme))
        Choices(
            listOf(
                ThemeMode.System to R.string.theme_system,
                ThemeMode.Light to R.string.theme_light,
                ThemeMode.Dark to R.string.theme_dark,
            ),
            themeMode,
            actions::themeMode,
        )
        Heading(stringResource(R.string.user_menu_tutorial))
        Choices(
            listOf(
                TutorialBubbles.All to R.string.tutorial_all,
                TutorialBubbles.Simple to R.string.tutorial_simple,
                TutorialBubbles.Off to R.string.tutorial_off,
            ),
            tutorialBubbles,
            actions::tutorialBubbles,
        )
    }
}

/**
 * **Accounts** (SH-23): every forum the app holds with who is signed in there — in
 * single-forum mode, this one — with **Sign out** and **Remove from app**.
 */
@Composable
private fun Accounts(
    forumName: String,
    member: MemberCard?,
    actions: UserMenuActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    logo: String? = null,
) {
    var confirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onBack)
    MenuPage(onBack, modifier, close = stringResource(R.string.user_menu_back)) {
        Heading(stringResource(R.string.user_menu_accounts))
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ForumLogo(forumName, size = 48.dp, logoUrl = logo)
            Column(Modifier.weight(1f)) {
                Text(forumName, style = MaterialTheme.typography.titleMedium)
                Text(
                    member?.let { stringResource(R.string.accounts_signed_in_as, it.name) }
                        ?: stringResource(R.string.accounts_signed_out),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (member != null) {
                OutlinedButton(onClick = actions::signOut) {
                    Text(stringResource(R.string.user_menu_sign_out))
                }
            }
            TextButton(onClick = { confirm = true }) {
                Text(stringResource(R.string.accounts_remove))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.accounts_remove_title, forumName)) },
            text = { Text(stringResource(R.string.accounts_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    actions.remove()
                }) { Text(stringResource(R.string.accounts_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Roles(roles: List<MemberRole>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        roles.forEach { role ->
            val color = parseHexColor(role.colour) ?: MaterialTheme.colorScheme.secondaryContainer
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color,
                contentColor = onColorFor(color),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    role.name,
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun <T> Choices(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(
        text,
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun Item(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    /** A number after the label: conversations with something new (SH-13). */
    count: Int? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * An entry the member cannot use yet: greyed, read as unavailable, with a small [action]
 * button at its right that makes it available ([actionLabel] what it says to TalkBack).
 */
@Composable
internal fun UnavailableItem(
    icon: Int,
    label: String,
    action: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val greyed = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { disabled() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(icon), null, tint = greyed)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = greyed)
        }
        OutlinedButton(
            onClick = onAction,
            modifier = Modifier.semantics { contentDescription = actionLabel },
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}

/** Material's opacity for content that is disabled. */
private const val DISABLED_ALPHA = 0.38f

/**
 * A menu filling the screen (SH-10, SH-20): a close button at the top, its content
 * scrolling; a swipe up past the end closes it, as system back does.
 */
@Composable
internal fun MenuPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    close: String = stringResource(R.string.close),
    content: @Composable ColumnScope.() -> Unit,
) {
    val threshold = with(LocalDensity.current) { SWIPE_TO_CLOSE.toPx() }
    val swipe =
        remember(onClose, threshold) {
            object : NestedScrollConnection {
                var pulled by mutableFloatStateOf(0f)

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    pulled = if (available.y < 0) pulled + available.y else 0f
                    if (pulled < -threshold) {
                        pulled = 0f
                        onClose()
                    }
                    return Offset.Zero
                }
            }
        }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(
                        painterResource(org.idejo.app.core.designsystem.R.drawable.ic_close),
                        close,
                    )
                }
            }
            Column(
                Modifier
                    .nestedScroll(swipe)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}

private val SWIPE_TO_CLOSE = 96.dp
