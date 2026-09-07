import SwiftUI
import Uncial

/// What this device can actually do — the screen the whole design exists to justify.
///
/// Uncial runs Apple Vision here and Tesseract on Android, and the two disagree about more
/// than speed. `OcrCapabilities` is the SDK refusing to paper over that, so the sample
/// gives it a screen rather than a footnote.
struct DeviceScreen: View {

    let capabilities: OcrCapabilities?
    let hasDigitalTextLayer: Bool
    let note: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.spacing.row) {
                Text("What this device can actually do")
                    .font(Theme.typography.display)
                    .foregroundColor(Theme.color.ink)
                    .padding(.top, Theme.spacing.card)
                Text(
                    "Reported by UncialClient.capabilities, never assumed. The same code on "
                        + "Android reports a different set."
                )
                .font(Theme.typography.body)
                .foregroundColor(Theme.color.inkMuted)

                if let capabilities {
                    SectionHeader(title: "Engine").padding(.top, Theme.spacing.card)
                    AppCard {
                        Text(capabilities.engineId)
                            .font(Theme.typography.displaySmall)
                            .foregroundColor(Theme.color.ink)
                        Text("version \(capabilities.engineVersion ?? "unknown") · installed at launch")
                            .font(Theme.typography.caption)
                            .foregroundColor(Theme.color.inkSubtle)
                        HStack(spacing: Theme.spacing.tight) {
                            ForEach(capabilities.languages, id: \.tesseractCode) { language in
                                Chip(text: language.bcp47 ?? language.tesseractCode, tone: .selected)
                            }
                            Chip(
                                text: hasDigitalTextLayer ? "digital text layer" : "no digital text layer",
                                tone: hasDigitalTextLayer ? .accent : .unavailable
                            )
                        }
                        .padding(.top, Theme.spacing.row)
                        Text(
                            "Queried per request, not once: Apple's header says a language "
                                + "offered at the accurate level may be absent at the fast one, "
                                + "so the probe is configured exactly as the real request is."
                        )
                        .font(Theme.typography.caption)
                        .foregroundColor(Theme.color.inkSubtle)
                        .padding(.top, Theme.spacing.row)
                    }

                    SectionHeader(title: "Capabilities").padding(.top, Theme.spacing.card)
                    AppCard {
                        CapabilityList(capabilities: [
                            Capability(
                                name: "Word boxes",
                                supported: capabilities.wordLevel,
                                note: "Derived from boundingBoxForRange, not native."
                            ),
                            Capability(name: "Confidence", supported: capabilities.confidence),
                            Capability(
                                name: "Per-line language",
                                supported: capabilities.perLineLanguage,
                                note: "Use OcrLine.script instead: derived from the text, so always available."
                            ),
                            Capability(name: "Orientation", supported: capabilities.orientationDetection),
                            Capability(name: "Skew", supported: capabilities.skewDetection),
                        ])
                    }
                    Text(
                        "The JVM binding of Tesseract reports all five. Platform asymmetry is "
                            + "reported here rather than hidden — which is why the capability type exists."
                    )
                    .font(Theme.typography.caption)
                    .foregroundColor(Theme.color.inkSubtle)
                } else {
                    AppCard {
                        Text("No OCR engine available on this device.")
                            .font(Theme.typography.bodyStrong)
                            .foregroundColor(Theme.color.accent)
                        Text("UncialBootstrap.start() installs the Vision engine at launch; iOS has no androidx.startup, so that step cannot be automatic.")
                            .font(Theme.typography.caption)
                            .foregroundColor(Theme.color.inkSubtle)
                    }
                }

                if let note {
                    AppCard {
                        Text(note)
                            .font(Theme.typography.caption)
                            .foregroundColor(Theme.color.inkSubtle)
                    }
                }
            }
            .padding(.horizontal, Theme.spacing.screen)
            .padding(.bottom, Theme.spacing.section)
        }
    }
}
