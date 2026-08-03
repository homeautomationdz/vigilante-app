package com.vigilante.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vigilante.app.ui.theme.AppColors
import kotlinx.coroutines.launch

/*
 * Shared design-system components.
 *
 * House rules (from the "Soft UI Evolution" + "Inclusive Design" entries of
 * the UI UX Pro Max database): 14dp radius on containers, borders instead of
 * heavy drop shadows, 200–300ms motion, tap targets ≥ 48dp, and status is
 * never colour-only — always colour + text (and an icon where it helps).
 */

private val CardRadius = RoundedCornerShape(14.dp)

/** Standard content container: hairline border, whisper of elevation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val border = BorderStroke(1.dp, AppColors.current.cardBorder)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = CardRadius,
            border = border,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            shape = CardRadius,
            border = border,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) { content() }
    }
}

/**
 * The signature navy panel at the top of a screen. Rounded on its bottom
 * edge so content appears to slide underneath it.
 */
@Composable
fun HeaderPanel(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    val c = AppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(Brush.linearGradient(listOf(c.headerStart, c.headerEnd)))
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = c.onHeader
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.onHeader
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onHeaderMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                trailing?.invoke()
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** Numeric dashboard tile: big accent number over a quiet label. */
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    val c = AppColors.current
    VCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.tileIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = c.tileIcon
                    )
                }
                Spacer(Modifier.height(9.dp))
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent ?: c.statValue
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** Big tappable section entry used on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    container: Color? = null,
    onClick: () -> Unit
) {
    val c = AppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "tileScale"
    )
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.scale(scale),
        shape = CardRadius,
        border = BorderStroke(1.dp, c.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(container ?: c.tileIconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = tint ?: c.tileIcon
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Status pill — always paired with a word, never colour alone. */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone = ChipTone.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val c = AppColors.current
    val (bg, fg) = when (tone) {
        ChipTone.SUCCESS -> c.successContainer to c.onSuccessContainer
        ChipTone.WARNING -> c.warningContainer to c.onWarningContainer
        ChipTone.DANGER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ChipTone.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

enum class ChipTone { NEUTRAL, SUCCESS, WARNING, DANGER, INFO }

/** Generic confirmation dialog used before every sensitive action. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "نعم",
    dismissLabel: String = "إلغاء",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/** Shown when a list has no content. */
@Composable
fun EmptyState(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val c = AppColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.tileIconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = c.tileIcon
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** Section title inside a screen. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)
    )
}

/** Full-size centered loading indicator. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    }
}

/** Default wording for the one-time reveal shown right after a code is minted. */
internal const val RECOVERY_REVEAL_TITLE = "احفظ رمز الاسترجاع"
internal const val RECOVERY_REVEAL_BODY =
    "هذا الرمز هو الوسيلة الوحيدة لاستعادة الحساب إذا نسيت كلمة المرور. " +
        "اكتبه في مكان آمن الآن — لن يظهر مرة أخرى."

/**
 * One-time reveal of a recovery code (SRS ch. 3).
 *
 * The code is stored only as a hash, so this is the single moment it can ever
 * be read. The dialog therefore refuses back-press and outside taps, and the
 * "متابعة" button stays disabled until the owner ticks the confirmation box.
 */
@Composable
internal fun RecoveryCodeReveal(
    code: String,
    onDone: () -> Unit,
    title: String = RECOVERY_REVEAL_TITLE,
    body: String = RECOVERY_REVEAL_BODY
) {
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmed by rememberSaveable(code) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VCard(modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(
                                code,
                                style = MaterialTheme.typography.headlineSmall,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 18.dp)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            scope.launch { snackbarHostState.showSnackbar("تم نسخ الرمز") }
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("نسخ الرمز")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmed = !confirmed }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text(
                            "حفظت الرمز في مكان آمن",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = onDone,
                        enabled = confirmed,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) { Text("متابعة", style = MaterialTheme.typography.titleMedium) }
                }
            }
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** App top bar; the back arrow auto-mirrors in RTL. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VigilanteTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = AppColors.current
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.headerStart,
            titleContentColor = c.onHeader,
            navigationIconContentColor = c.onHeader,
            actionIconContentColor = c.onHeader
        )
    )
}
