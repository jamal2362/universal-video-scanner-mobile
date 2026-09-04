@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.model.HdrMetadata
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.ui.LocalPosterServer
import com.jamal2367.uvsmobile.ui.components.EntryChipRow
import com.jamal2367.uvsmobile.ui.components.ErrorState
import com.jamal2367.uvsmobile.ui.components.InfoRow
import com.jamal2367.uvsmobile.ui.components.LoadingState
import com.jamal2367.uvsmobile.ui.components.MetaChip
import com.jamal2367.uvsmobile.ui.components.PosterImage
import com.jamal2367.uvsmobile.ui.components.SectionCard
import com.jamal2367.uvsmobile.ui.theme.PillShape
import com.jamal2367.uvsmobile.util.Artwork
import com.jamal2367.uvsmobile.util.Formatters
import com.jamal2367.uvsmobile.util.PosterUrls
import kotlinx.coroutines.launch

/**
 * Everything the scanner knows about one title.
 *
 * The list asks for a dozen fields; this asks for the record, because this is
 * where the other twenty are worth showing - the enhancement layer, the CM
 * version, the mastering display the grade was made on, and every rating the
 * lookups came back with.
 */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val rescanDone = stringResource(R.string.detail_rescan_done)
    val deletedMessage = stringResource(R.string.detail_deleted)
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        if (state.entry != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    // The entry is gone - deleted here, or the file left the media directory
    // while this screen was open. There is nothing left to show.
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // One line, but all of it: without the ellipsis the line
                    // breaks at the last whole word that fits and the rest is
                    // dropped, which leaves a gap and no sign that a title went
                    // missing. "Grand Theft Auto VI: Ein aus…" says both.
                    Text(
                        text = state.entry?.displayTitle.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isWorking) {
                        LoadingIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(28.dp),
                        )
                    }
                    // The one thing on this screen that cannot be undone, in
                    // the corner every screen keeps its actions in - and out of
                    // the way of the button that is pressed often.
                    IconButton(
                        onClick = { confirmDelete = true },
                        enabled = state.entry != null && !state.isWorking,
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val entry = state.entry
        when {
            state.isLoading && entry == null -> LoadingState(Modifier.padding(padding))

            entry == null -> ErrorState(
                message = state.error.orEmpty(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            else -> DetailContent(
                entry = entry,
                posterWidth = state.posterWidth,
                isWorking = state.isWorking,
                onRescan = { viewModel.rescan(rescanDone) },
                onCopyPath = {
                    context.copyToClipboard(it)
                    // Android 13 and newer show their own confirmation, so this
                    // is the one for the versions that do not.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    }
                },
                contentPadding = padding,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(deletedMessage)
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    entry: LibraryEntry,
    posterWidth: Int,
    isWorking: Boolean,
    onRescan: () -> Unit,
    onCopyPath: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        DetailHeader(entry = entry, posterWidth = posterWidth)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        // Outlined rather than filled: reading a title is what this screen is
        // for, and a solid accent button at the top of it draws the eye to a
        // job that is done once in a while.
        OutlinedButton(
            onClick = onRescan,
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(
                    if (isWorking) R.string.detail_rescan_running else R.string.detail_rescan
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        entry.tmdbPlot?.takeIf { it.isNotBlank() }?.let { plot ->
            SectionCard(title = stringResource(R.string.detail_overview)) {
                Text(text = plot, style = MaterialTheme.typography.bodyMedium)
                if (entry.tmdbGenres.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entry.tmdbGenres.forEach { MetaChip(text = it, outlined = true) }
                    }
                }
            }
        }

        if (entry.tmdbDirectors.isNotEmpty() || entry.tmdbCast.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.detail_people)) {
                if (entry.tmdbDirectors.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.field_directors),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entry.tmdbDirectors.forEach { MetaChip(text = it, outlined = true) }
                    }
                }
                if (entry.tmdbCast.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.field_cast),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // A name is read as one thing, so the gap between two of
                    // them has to be wider than the gap inside either - and a
                    // wrapped line of them needs the same room downwards.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entry.tmdbCast.forEach { MetaChip(text = it, outlined = true) }
                    }
                }
            }
        }

        RatingsCard(entry)

        SectionCard(title = stringResource(R.string.detail_video)) {
            InfoRow(stringResource(R.string.field_hdr_format), entry.hdrFormat)
            InfoRow(stringResource(R.string.field_hdr_detail), entry.hdrDetail)
            InfoRow(stringResource(R.string.field_el_type), entry.elType)
            InfoRow(stringResource(R.string.field_dv_cm_version), entry.dvCmVersion)
            InfoRow(stringResource(R.string.field_resolution), entry.resolution)
            InfoRow(stringResource(R.string.field_resolution_class), entry.resolutionClass)
            InfoRow(stringResource(R.string.field_video_codec), entry.videoCodec)
            InfoRow(stringResource(R.string.field_video_codec_profile), entry.videoCodecProfile)
            InfoRow(stringResource(R.string.field_video_encoder), entry.videoEncoder)
            InfoRow(
                stringResource(R.string.field_video_bitrate),
                Formatters.videoBitrate(entry.videoBitrate),
            )
        }

        SectionCard(title = stringResource(R.string.detail_audio)) {
            InfoRow(stringResource(R.string.field_audio_codec), entry.audioCodec)
            InfoRow(
                stringResource(R.string.field_audio_bitrate),
                Formatters.audioBitrate(entry.audioBitrate),
            )
        }

        HdrMetadataCard(entry.hdrMetadata)

        SectionCard(title = stringResource(R.string.detail_file)) {
            InfoRow(
                stringResource(R.string.field_duration),
                Formatters.durationExact(entry.duration),
            )
            InfoRow(stringResource(R.string.field_file_size), Formatters.fileSize(entry.fileSize))
            InfoRow(
                stringResource(R.string.field_mtime),
                Formatters.timestamp(context, entry.mtime),
            )
            InfoRow(
                stringResource(R.string.field_updated_at),
                Formatters.timestamp(context, entry.updatedAt),
            )
            InfoRow(stringResource(R.string.field_tmdb_id), entry.tmdbId)
            InfoRow(stringResource(R.string.field_imdb_id), entry.imdbId)
            // The path ends in the file name, so the row that carries it is
            // named for both: a "File name" row above it said the same thing
            // twice, once cut short.
            InfoRow(
                label = stringResource(R.string.field_path),
                value = entry.path,
                onClick = { onCopyPath(entry.path) },
            )
        }

            Box(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The top of a title's screen: its backdrop, its cover and what it is called.
 *
 * Both images earn their place here. The 16:9 backdrop is the header it was
 * made to be, faded into the page so the text over it stays readable; the
 * upright cover sits on top of it, which is the one place in the app where the
 * two are shown together rather than one standing in for the other.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(entry: LibraryEntry, posterWidth: Int) {
    val hasBackdrop = PosterUrls.forEntry(
        entry = entry,
        server = LocalPosterServer.current,
        width = null,
        artwork = Artwork.LANDSCAPE,
    ) != null

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Half the backdrop stays clear of the cover, which is what makes it
        // read as a header rather than as a second poster.
        val overlap = if (hasBackdrop) this.maxWidth * 9f / 16f * 0.55f else 0.dp

        if (hasBackdrop) {
            PosterImage(
                entry = entry,
                width = 640,
                artwork = Artwork.LANDSCAPE,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            0.8f to MaterialTheme.colorScheme.surface,
                        )
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = overlap)
                .padding(horizontal = 16.dp),
        ) {
            PosterImage(
                entry = entry,
                width = maxOf(posterWidth, 480),
                contentDescription = entry.displayTitle,
                modifier = Modifier
                    .width(130.dp)
                    .height(195.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                )
                entry.tmdbYear?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.tmdbTagline?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EntryChipRow(entry = entry, modifier = Modifier.padding(top = 2.dp))
                entry.top250Rank?.let { rank ->
                    MetaChip(
                        text = stringResource(R.string.rating_top250, rank),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingsCard(entry: LibraryEntry) {
    SectionCard(title = stringResource(R.string.detail_ratings)) {
        if (!entry.hasAnyRating) {
            Text(
                text = stringResource(R.string.rating_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingChip(stringResource(R.string.rating_imdb), Formatters.ratingOutOfTen(entry.imdbRating))
            RatingChip(stringResource(R.string.rating_tmdb), Formatters.ratingOutOfTen(entry.tmdbRating))
            RatingChip(stringResource(R.string.rating_rt), Formatters.percentage(entry.rtRating))
            RatingChip(
                stringResource(R.string.rating_rt_audience),
                Formatters.percentage(entry.rtAudience),
            )
            RatingChip(stringResource(R.string.rating_trakt), Formatters.percentage(entry.traktRating))
            RatingChip(
                stringResource(R.string.rating_metacritic),
                Formatters.percentage(entry.metacritic),
            )
        }
    }
}

/**
 * One source's score.
 *
 * Drawn rather than borrowed from a chip: it was an AssistChip held disabled
 * to stop it being pressed, and a disabled chip is drawn at 38% - which says
 * "unavailable" about a number that is simply not a button. Full strength on
 * a tonal ground now, which is what it always meant to say.
 */
@Composable
private fun RatingChip(label: String, value: String?) {
    if (value == null) return
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * The static HDR metadata, split the way the stream carries it: the base
 * layer's own mastering display and light levels, the Dolby Vision grade's, and
 * the active area its L5 describes.
 *
 * Named and paired as the web interface names and pairs them - HDR10 MDL, HDR10
 * MaxCLL/FALL, and the RPU's two - so that a title read on a phone and the same
 * title read in a browser say the same words in the same order.
 */
@Composable
private fun HdrMetadataCard(metadata: HdrMetadata?) {
    if (metadata == null || metadata.isEmpty) return

    SectionCard(title = stringResource(R.string.detail_hdr_metadata)) {
        // Both rows of a layer or neither: a grade that carries a mastering
        // display but no content light levels is a real answer, and leaving
        // that row out would read as a missing row rather than a missing
        // measurement. A layer the stream has none of at all stays out.
        if (metadata.hasBaseLayer) {
            InfoRow(
                stringResource(R.string.hdr_hdr10_mdl),
                Formatters.luminancePair(metadata.hdr10MdlMax, metadata.hdr10MdlMin),
            )
            InfoRow(
                stringResource(R.string.hdr_hdr10_max_cll_fall),
                Formatters.nitsPair(metadata.hdr10MaxCll, metadata.hdr10MaxFall),
            )
        }
        if (metadata.hasRpu) {
            InfoRow(
                stringResource(R.string.hdr_rpu_mdl),
                Formatters.luminancePair(metadata.rpuMdlMax, metadata.rpuMdlMin),
            )
            InfoRow(
                stringResource(R.string.hdr_rpu_max_cll_fall),
                Formatters.nitsPair(metadata.rpuMaxCll, metadata.rpuMaxFall),
            )
        }
        // Always, even at four zeros: "no crop" is what an L5 that reads
        // nothing means, and that is an answer rather than a missing row.
        InfoRow(
            stringResource(R.string.hdr_l5_crop),
            "L: ${metadata.l5Left.toInt()} | R: ${metadata.l5Right.toInt()} | " +
                "T: ${metadata.l5Top.toInt()} | B: ${metadata.l5Bottom.toInt()}",
        )
    }
}

private fun Context.copyToClipboard(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("path", text))
}
