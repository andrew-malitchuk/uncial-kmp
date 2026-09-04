package io.github.andrewmalitchuk.uncial.samples.android.source.screen.home

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.andrewmalitchuk.uncial.samples.android.R
import io.github.andrewmalitchuk.uncial.samples.android.core.image.newCameraOutput
import io.github.andrewmalitchuk.uncial.samples.android.source.model.DocumentSummary
import io.github.andrewmalitchuk.uncial.samples.android.ui.core.Theme
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillButton
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.button.PillEmphasis
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip.ChipTone
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.text.SectionHeader
import io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.molecule.item.SourceCard

/**
 * Everything the SDK can be handed, on one screen.
 *
 * Two sections, because the SDK has two entry points and they are genuinely different:
 * `extractAsFlow(bytes)` opens a PDF, `extractAsFlow(raster)` skips the PDF machinery
 * entirely. The badges name the path each card will take before it is taken.
 */
@Composable
fun HomeScreen(
    languages: String,
    lastResult: DocumentSummary?,
    onOpenDocument: () -> Unit,
    onExtractScan: () -> Unit,
    onExtractDigital: () -> Unit,
    onExtractPdf: (Uri) -> Unit,
    onExtractImage: (Uri) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onExtractPdf) }

    // The photo picker, not OpenDocument with an image filter: on Android 13+ it is the
    // system component, on older versions the AndroidX contract falls back for us, and
    // either way it needs no READ_MEDIA_IMAGES permission because the user chooses what to
    // hand over.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onExtractImage) }

    // Where the camera app was told to write. rememberSaveable because taking a photo can
    // outlive this process: the camera is another app, and a low-memory device is free to
    // kill ours while it is in the foreground. Losing the URI loses the photo.
    var cameraOutput by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val photo = cameraOutput
        cameraOutput = null
        // `saved` is false when the user backed out of the camera, and the URI then points
        // at an empty file that would decode to nothing.
        if (saved && photo != null) onExtractImage(photo)
    }

    // A device with no camera app at all -- an emulator without one, a kiosk device. The
    // <queries> element in the manifest is what makes this resolve on API 30+.
    val hasCamera = remember(context) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.row),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = Theme.typography.display,
            color = Theme.color.ink,
            modifier = Modifier.padding(top = Theme.spacing.card),
        )
        Text(
            text = stringResource(R.string.home_body),
            style = Theme.typography.body,
            color = Theme.color.inkMuted,
        )

        SectionHeader(
            text = stringResource(R.string.section_documents),
            trailing = languages,
            modifier = Modifier.padding(top = Theme.spacing.card),
        )
        SourceCard(
            glyph = "▦",
            title = stringResource(R.string.source_scanned_title),
            subtitle = stringResource(R.string.source_scanned_subtitle),
            badge = stringResource(R.string.badge_ocr),
            badgeTone = ChipTone.Accent,
            enabled = enabled,
            onClick = onExtractScan,
        )
        SourceCard(
            glyph = "≣",
            title = stringResource(R.string.source_digital_title),
            subtitle = stringResource(R.string.source_digital_subtitle),
            badge = stringResource(R.string.badge_fast_path),
            enabled = enabled,
            onClick = onExtractDigital,
        )
        SourceCard(
            glyph = "＋",
            title = stringResource(R.string.source_pick_title),
            subtitle = stringResource(R.string.source_pick_subtitle),
            enabled = enabled,
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
        )

        SectionHeader(
            text = stringResource(R.string.section_images),
            trailing = stringResource(R.string.section_images_trailing),
            modifier = Modifier.padding(top = Theme.spacing.card),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.row)) {
            PillButton(
                text = stringResource(R.string.action_photo),
                onClick = {
                    val destination = newCameraOutput(context)
                    cameraOutput = destination
                    takePhoto.launch(destination)
                },
                emphasis = PillEmphasis.Secondary,
                enabled = enabled && hasCamera,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.action_gallery),
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                emphasis = PillEmphasis.Secondary,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(
                if (hasCamera) R.string.images_note else R.string.no_camera,
            ),
            style = Theme.typography.caption,
            color = Theme.color.inkSubtle,
            modifier = Modifier.fillMaxWidth(),
        )

        // Where the removed second tab went. A result is reachable from the screen that
        // produced it, which is also the only place it makes sense before there is one.
        if (lastResult != null) {
            SectionHeader(
                text = stringResource(R.string.section_result),
                trailing = stringResource(R.string.section_result_trailing, lastResult.elapsedMillis),
                modifier = Modifier.padding(top = Theme.spacing.card),
            )
            SourceCard(
                glyph = "▥",
                title = lastResult.label,
                subtitle = stringResource(
                    R.string.result_subtitle,
                    lastResult.pageCount,
                    lastResult.lines,
                    lastResult.blocks.size,
                ),
                onClick = onOpenDocument,
            )
        }

        Column(modifier = Modifier.padding(bottom = Theme.spacing.section)) {}
    }
}
