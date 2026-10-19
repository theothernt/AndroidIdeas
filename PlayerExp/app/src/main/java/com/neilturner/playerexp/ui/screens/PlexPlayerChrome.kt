package com.neilturner.playerexp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.ui.modifiers.catchBackPress
import com.neilturner.playerexp.ui.theme.PlexAmber

/**
 * One button in the rail that hangs off the right edge of the player.
 *
 * The rail is the whole of the player's chrome: there are no transport controls in the middle of
 * the frame and nothing down the left-hand side, so the rail is the only place the user can go
 * from watching to choosing.
 */
@Immutable
internal data class RailAction(
    val key: String,
    val icon: ImageVector,
    val contentDescription: String,
    /** Tinted amber when the thing this button opens is the thing currently in effect. */
    val active: Boolean = false
)

/**
 * One row of the side drawer.
 *
 * [subLabel] is the state the row is in — the current speed, the codec being played — and
 * [trailing] is what sits at the right-hand end of it.
 */
@Immutable
internal data class DrawerItem(
    val key: String,
    val label: String,
    val subLabel: String? = null,
    val icon: ImageVector? = null,
    val trailing: DrawerTrailing = DrawerTrailing.None,
    val selected: Boolean = false,
    /** True for a row that opens another page rather than doing something itself. */
    val expandable: Boolean = false,
    /** True for a row that only tells the user something and does nothing when it is chosen. */
    val informational: Boolean = false
)

/** What sits at the right-hand end of a drawer row. */
internal sealed interface DrawerTrailing {
    data object None : DrawerTrailing
    data object Chevron : DrawerTrailing
    data object Check : DrawerTrailing
}

/**
 * The rail of buttons along the bottom right of the player.
 *
 * It is YouTube's action pill: a row of plain outline icons on a translucent slab, with the
 * focused one becoming a solid white disc so that it reads against whatever the video is doing
 * underneath it. It sits at the bottom right of the bar, on the same line as the rest of the
 * controls, and there is nothing in the middle of the picture and nothing down the left-hand
 * side: the video is the thing.
 */
@Composable
internal fun PlayerActionRail(
    actions: List<RailAction>,
    focusRequesters: List<FocusRequester>,
    onSelect: (RailAction) -> Unit,
    onFocused: (RailAction) -> Unit,
    onUserActivity: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Which button has the focus, so the rail knows when the user has run off either end of it.
    var focusedIndex by remember { mutableIntStateOf(-1) }

    Row(
        modifier = modifier
            .clip(RAIL_SHAPE)
            .background(RAIL_COLOR)
            .padding(horizontal = RAIL_PADDING, vertical = RAIL_PADDING)
            // Back has to be caught on the rail itself. A press travels up the focus chain
            // looking for something to consume it, and a button that has the focus swallows it
            // on the way past, so the screen's back handler would never hear about it and the
            // bar would stay on screen with nothing in it focused.
            .catchBackPress(onBack)
            // A press that would take the focus off the rail leaves it where it is. The rail
            // holds the focus until Back takes the bar away or its timeout does, so that a
            // stray press while stepping along the buttons cannot leave the focus with
            // nothing at all.
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionDown -> true
                        // Off the end of the row there is nowhere to go either.
                        Key.DirectionLeft -> focusedIndex == 0
                        Key.DirectionRight -> focusedIndex == actions.lastIndex
                        else -> false
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(RAIL_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEachIndexed { index, action ->
            RailActionButton(
                action = action,
                onClick = { onSelect(action) },
                onFocused = {
                    focusedIndex = index
                    onFocused(action)
                },
                onUserActivity = onUserActivity,
                modifier = focusRequesters.getOrNull(index)
                    ?.let { Modifier.focusRequester(it) }
                    ?: Modifier
            )
        }
    }
}

/**
 * One button of the rail.
 *
 * Idle it is a bare outline icon, because the slab behind it is already the container. Focused it
 * becomes a white disc with a dark icon, which is the only way to show focus over live video
 * without a border that the video would cut through.
 *
 * The button is drawn here rather than taken from the TV library, because the library's button
 * puts a squircle and a glow on it, and YouTube's is a plain white circle.
 */
@Composable
private fun RailActionButton(
    action: RailAction,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The focus is read from the interaction source rather than from an onFocusChanged callback.
    // A callback only fires on a change, so a button that is focused before it is drawn for the
    // first time never reports itself, and the rail cannot tell which of its buttons has the
    // focus.
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) RAIL_FOCUSED_SCALE else 1f,
        animationSpec = tween(RAIL_SCALE_ANIMATION_MILLIS),
        label = "railButtonScale"
    )

    Box(
        modifier = modifier
            .size(RAIL_BUTTON_SIZE)
            .scale(scale)
            .clip(CircleShape)
            .background(if (focused) RAIL_FOCUSED_COLOR else Color.Transparent)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        onUserActivity()
                        false
                    }
                    KeyEventType.KeyUp -> {
                        // The click happens on the key up, the way the TV library's own buttons
                        // do it, and the key up is consumed here. It matters that it is: the
                        // drawer that opens takes the focus, and a key up left for it would
                        // activate the row that had just been given the focus — choosing a
                        // track and closing the drawer again in the same breath.
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                onClick()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
            tint = when {
                focused -> RAIL_FOCUSED_ICON_COLOR
                action.active -> PlexAmber
                else -> Color.White
            },
            modifier = Modifier.size(RAIL_ICON_SIZE)
        )
    }
}

/**
 * The width the drawer is drawn at.
 *
 * The bar keeps out of the drawer's way while it is open, and it has to know how wide that is,
 * so both of them measure it the same way.
 */
@Composable
internal fun drawerWidth(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return minOf(screenWidth * DRAWER_WIDTH_FRACTION, DRAWER_MAX_WIDTH)
}

/**
 * The side drawer: a panel that comes in from the right of the player and holds a list of rows.
 *
 * The shape is YouTube's settings panel — a near-black panel, a title, and rounded rows that
 * flip to white when they are focused. The video keeps playing underneath and to the left of it,
 * which is why the panel is opaque: a track label has to stay readable wherever the drawer lands.
 */
@Composable
internal fun PlayerDrawer(
    title: String,
    items: List<DrawerItem>,
    focusRequesters: List<FocusRequester>,
    landingIndex: Int,
    onSelect: (DrawerItem) -> Unit,
    onBack: () -> Unit,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The row to open on is settled here, inside the drawer, so that the requester has a composed
    // row to sit on before it asks for focus: a track further down the list is not composed until
    // it is scrolled near.
    LaunchedEffect(landingIndex) {
        focusRequesters.getOrNull(landingIndex)?.requestFocus()
    }

    // Which row has the focus, so the drawer knows when the user has run off either end of it.
    var focusedIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(drawerWidth())
            .background(DRAWER_COLOR)
            .padding(horizontal = DRAWER_HORIZONTAL_PADDING)
            // Back has to be caught on the drawer itself. Focus is on one of the rows inside it,
            // and a press from there would otherwise be swallowed on the way past the focusable
            // player surface and never reach the screen's back handler.
            .catchBackPress(onBack)
    ) {
        Spacer(modifier = Modifier.height(DRAWER_TOP_PADDING))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = DRAWER_TITLE_GAP)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // Off either end of the list, the focus stays where it is. Letting it run on would
                // take it out of the drawer and back to the video, which is not where the user
                // was when they opened it.
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (keyEvent.key) {
                            Key.DirectionDown -> focusedIndex == items.lastIndex
                            Key.DirectionUp -> focusedIndex == 0
                            // Left and right mean nothing in a list of rows, and the rail is
                            // behind the drawer: taking the focus there would put it on a button
                            // the user cannot see.
                            Key.DirectionLeft, Key.DirectionRight -> true
                            else -> false
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(DRAWER_ROW_SPACING)
        ) {
            items.forEachIndexed { index, item ->
                DrawerRow(
                    item = item,
                    onClick = { onSelect(item) },
                    onUserActivity = onUserActivity,
                    onFocused = { focusedIndex = index },
                    modifier = focusRequesters.getOrNull(index)
                        ?.let { Modifier.focusRequester(it) }
                        ?: Modifier
                )
            }
        }
        Spacer(modifier = Modifier.height(DRAWER_BOTTOM_PADDING))
    }
}

/**
 * One row of the drawer.
 *
 * The focused row turns white and its text turns black, which is the read the whole drawer
 * exists to give. A row that is only telling the user something carries no trailing mark and
 * does nothing when it is chosen.
 */
@Composable
private fun DrawerRow(
    item: DrawerItem,
    onClick: () -> Unit,
    onUserActivity: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val labelColour = if (focused) DRAWER_FOCUSED_TEXT else Color.White
    val subLabelColour = if (focused) DRAWER_FOCUSED_SUB_TEXT else DRAWER_SUB_TEXT
    val iconColour = if (focused) DRAWER_FOCUSED_TEXT else Color.White

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DRAWER_ROW_HEIGHT)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    onUserActivity()
                }
            },
        shape = ButtonDefaults.shape(shape = DRAWER_ROW_SHAPE),
        colors = ButtonDefaults.colors(
            containerColor = DRAWER_ROW_COLOR,
            contentColor = labelColour,
            focusedContainerColor = DRAWER_ROW_FOCUSED_COLOR,
            focusedContentColor = labelColour
        ),
        contentPadding = PaddingValues(horizontal = DRAWER_ROW_PADDING),
        // No growth on focus. The rows are a list, and a row growing would shove the rows around
        // it along while the user is stepping through them with the D-pad.
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = DRAWER_ROW_PRESSED_SCALE),
        border = ButtonDefaults.border(border = Border.None)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = iconColour,
                    modifier = Modifier.size(DRAWER_ROW_ICON_SIZE)
                )
                Spacer(modifier = Modifier.width(DRAWER_ROW_ICON_GAP))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = DRAWER_LABEL_SIZE
                    ),
                    color = labelColour,
                    // The name of a track can be long — the longer name Plex gives it goes in
                    // brackets beside it — so it is given the room to wrap rather than being cut.
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subLabel != null) {
                    Text(
                        text = item.subLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = DRAWER_SUB_LABEL_SIZE),
                        color = subLabelColour,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.trailing != DrawerTrailing.None) {
                Spacer(modifier = Modifier.width(DRAWER_ROW_TRAILING_GAP))
                Icon(
                    imageVector = when (item.trailing) {
                        DrawerTrailing.Chevron -> Icons.Rounded.ChevronRight
                        DrawerTrailing.Check -> Icons.Rounded.Check
                        DrawerTrailing.None -> Icons.Rounded.ChevronRight
                    },
                    contentDescription = null,
                    tint = if (item.selected) DRAWER_ROW_SELECTED else iconColour,
                    modifier = Modifier.size(DRAWER_ROW_TRAILING_SIZE)
                )
            }
        }
    }
}

/** The rail's slab: a translucent white over the video, the way YouTube's action pill is. */
private val RAIL_COLOR = Color(0x33FFFFFF)
private val RAIL_SHAPE = RoundedCornerShape(28.dp)
private val RAIL_PADDING = 4.dp
private val RAIL_SPACING = 2.dp
private val RAIL_BUTTON_SIZE = 48.dp
private val RAIL_ICON_SIZE = 24.dp
private val RAIL_FOCUSED_COLOR = Color(0xFFF1F1F1)
private val RAIL_FOCUSED_ICON_COLOR = Color(0xFF0F0F0F)
private const val RAIL_FOCUSED_SCALE = 1.08f
private const val RAIL_SCALE_ANIMATION_MILLIS = 150

/** The drawer: a near-black panel, the width YouTube's settings panel is on a 1080p screen. */
private val DRAWER_COLOR = Color(0xFF0F0F0F)
private const val DRAWER_WIDTH_FRACTION = 0.32f
private val DRAWER_MAX_WIDTH = 420.dp
private val DRAWER_HORIZONTAL_PADDING = 16.dp
private val DRAWER_TOP_PADDING = 24.dp
private val DRAWER_BOTTOM_PADDING = 24.dp
private val DRAWER_TITLE_GAP = 16.dp

private val DRAWER_ROW_COLOR = Color(0xFF272727)
private val DRAWER_ROW_FOCUSED_COLOR = Color(0xFFF1F1F1)
private val DRAWER_FOCUSED_TEXT = Color(0xFF0F0F0F)
private val DRAWER_FOCUSED_SUB_TEXT = Color(0xFF3C3C3C)
private val DRAWER_SUB_TEXT = Color(0xFFB3B3B3)
private val DRAWER_ROW_SELECTED = PlexAmber
private val DRAWER_ROW_SHAPE = RoundedCornerShape(8.dp)
private val DRAWER_ROW_HEIGHT = 52.dp
private val DRAWER_ROW_SPACING = 6.dp
private val DRAWER_ROW_PADDING = 16.dp
private val DRAWER_ROW_ICON_SIZE = 24.dp
private val DRAWER_ROW_ICON_GAP = 16.dp
private val DRAWER_ROW_TRAILING_GAP = 12.dp
private val DRAWER_ROW_TRAILING_SIZE = 24.dp
private val DRAWER_LABEL_SIZE = 15.sp
private val DRAWER_SUB_LABEL_SIZE = 12.sp
private const val DRAWER_ROW_PRESSED_SCALE = 0.98f
