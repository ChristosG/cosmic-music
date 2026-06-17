package app.cosmic.feature.download

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.YoutubeSearchedFor
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cosmic.core.db.entity.DownloadJob
import app.cosmic.core.download.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    modifier: Modifier = Modifier,
    onSearchYoutube: () -> Unit = {},
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result silent — UI never blocks on this */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                actions = {
                    IconButton(onClick = onSearchYoutube) {
                        Icon(
                            Icons.Filled.YoutubeSearchedFor,
                            contentDescription = "Search YouTube",
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        .copy(alpha = 0.85f),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            UrlInputBar(
                value = input,
                error = error,
                onChange = viewModel::onInputChange,
                onSubmit = viewModel::submit,
            )
            HorizontalDivider()
            if (jobs.isEmpty()) {
                EmptyQueue()
            } else {
                JobList(
                    jobs = jobs,
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onDelete = viewModel::delete,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlInputBar(
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.paste_url_hint)) },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
            trailingIcon = {
                if (value.isBlank()) {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onChange)
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSubmit,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Download")
                    }
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = "YouTube · YT Music · SoundCloud · Bandcamp · direct audio URL",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun EmptyQueue() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.queue_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.queue_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun JobList(
    jobs: List<DownloadJob>,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(jobs, key = { it.id }) { job ->
            JobRow(job = job, onCancel = { onCancel(job.id) }, onRetry = { onRetry(job.id) }, onDelete = { onDelete(job.id) })
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobRow(
    job: DownloadJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val (icon, tint, statusLabel) = statusVisuals(job.status)
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val resolvedTitle = job.title?.takeIf { it.isNotBlank() }
                Text(
                    text = resolvedTitle ?: job.sourceUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (resolvedTitle != null) {
                    Text(
                        text = job.sourceUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.sourceKind,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(statusLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
                if (job.error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = job.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = {
                                clipboard.setText(AnnotatedString(job.error!!))
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row {
                when (job.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.dl_action_cancel))
                        }
                    DownloadStatus.FAILED, DownloadStatus.CANCELED ->
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.dl_action_retry))
                        }
                    else -> {}
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dl_action_delete))
                }
            }
        }
        if (job.status == DownloadStatus.RUNNING) {
            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun statusVisuals(status: String): Triple<ImageVector, Color, Int> = when (status) {
    DownloadStatus.QUEUED ->
        Triple(Icons.Filled.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, R.string.dl_status_queued)
    DownloadStatus.RUNNING ->
        Triple(Icons.Filled.CloudDownload, MaterialTheme.colorScheme.primary, R.string.dl_status_running)
    DownloadStatus.COMPLETED ->
        Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, R.string.dl_status_completed)
    DownloadStatus.FAILED ->
        Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, R.string.dl_status_failed)
    DownloadStatus.CANCELED ->
        Triple(Icons.Filled.Cancel, MaterialTheme.colorScheme.onSurfaceVariant, R.string.dl_status_canceled)
    else ->
        Triple(Icons.Filled.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, R.string.dl_status_queued)
}
