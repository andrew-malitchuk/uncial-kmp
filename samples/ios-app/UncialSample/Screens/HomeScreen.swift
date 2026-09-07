import SwiftUI

/// Everything the SDK can be handed, on one screen.
///
/// Two sections, because the SDK has two entry points and they are genuinely different:
/// one opens a PDF, the other skips the PDF machinery entirely. The badges name the path
/// each card will take before it is taken.
struct HomeScreen: View {

    let languages: String
    let lastResult: OcrModel.Result?
    let enabled: Bool
    let onOpenResult: () -> Void
    let onScanned: () -> Void
    let onDigital: () -> Void
    let onPickPdf: () -> Void
    let onPhoto: () -> Void
    let onGallery: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.spacing.row) {
                Text("What should we read today?")
                    .font(Theme.typography.display)
                    .foregroundColor(Theme.color.ink)
                    .padding(.top, Theme.spacing.card)
                Text("Pick a bundled sample, open a PDF, or point the camera at a page.")
                    .font(Theme.typography.body)
                    .foregroundColor(Theme.color.inkMuted)

                SectionHeader(title: "Documents", trailing: languages)
                    .padding(.top, Theme.spacing.card)
                SourceCard(
                    glyph: "▦",
                    title: "Scanned fixture",
                    subtitle: "Image-only Ukrainian PDF · bundled",
                    badge: "OCR",
                    badgeTone: .accent,
                    enabled: enabled,
                    action: onScanned
                )
                SourceCard(
                    glyph: "≣",
                    title: "Digital fixture",
                    subtitle: "PDF with a text layer · bundled",
                    badge: "fast path",
                    enabled: enabled,
                    action: onDigital
                )
                SourceCard(
                    glyph: "＋",
                    title: "Open a PDF…",
                    subtitle: "Anything Files can reach",
                    enabled: enabled,
                    action: onPickPdf
                )

                SectionHeader(title: "Images", trailing: "no PDF involved")
                    .padding(.top, Theme.spacing.card)
                HStack(spacing: Theme.spacing.row) {
                    PillButton(
                        title: "Photo…",
                        emphasis: .secondary,
                        enabled: enabled && CameraPicker.isAvailable,
                        action: onPhoto
                    )
                    PillButton(
                        title: "Gallery…",
                        emphasis: .secondary,
                        enabled: enabled,
                        action: onGallery
                    )
                }
                Text(
                    CameraPicker.isAvailable
                        ? "A photo goes straight to the recognizer as a Raster. Orientation is "
                            + "normalized before Vision sees the CGImage, which does not carry it."
                        : "No camera on this device — the Simulator has none. The gallery still works."
                )
                .font(Theme.typography.caption)
                .foregroundColor(Theme.color.inkSubtle)

                // Where a second tab would have gone. A result is reachable from the screen
                // that produced it, which is also the only place it makes sense before
                // there is one.
                if let lastResult {
                    SectionHeader(title: "Last result", trailing: "\(lastResult.elapsedMillis) ms")
                        .padding(.top, Theme.spacing.card)
                    SourceCard(
                        glyph: "▥",
                        title: lastResult.label,
                        subtitle: "\(lastResult.pageCount) pages · \(lastResult.lines) lines "
                            + "· \(lastResult.blocks.count) blocks",
                        action: onOpenResult
                    )
                }
            }
            .padding(.horizontal, Theme.spacing.screen)
            .padding(.bottom, Theme.spacing.section)
        }
    }
}
