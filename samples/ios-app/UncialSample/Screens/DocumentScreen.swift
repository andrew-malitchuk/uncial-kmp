import SwiftUI

/// The result, argued rather than dumped.
///
/// A sample that printed `document.text` would prove nothing: the reason this SDK returns
/// pages of lines of words with boxes and confidences is that someone downstream needs
/// them. So the screen leads with the counts, then the per-page confidence, then the
/// reconstructed blocks — three views of the same object, none of which is a string.
struct DocumentScreen: View {

    let result: OcrModel.Result?

    var body: some View {
        if let result {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Theme.spacing.row) {
                    VStack(alignment: .leading, spacing: Theme.spacing.tight) {
                        Text(result.label)
                            .font(Theme.typography.display)
                            .foregroundColor(Theme.color.ink)
                        Text(
                            "\(result.pageCount) pages · \(sourceLabel(result.source)) "
                                + "· \(result.elapsedMillis) ms"
                        )
                        .font(Theme.typography.caption)
                        .foregroundColor(Theme.color.inkSubtle)
                    }
                    .padding(.top, Theme.spacing.card)

                    StatRow(stats: [
                        Stat(value: "\(result.lines)", label: "LINES"),
                        // `—` rather than `0`: no words means the option was off or the
                        // engine cannot do it, which is not "none found".
                        Stat(value: result.words > 0 ? "\(result.words)" : "—", label: "WORDS"),
                        Stat(
                            value: result.confidence.map { "\(Int($0 * 100))%" } ?? "—",
                            label: "CONF"
                        ),
                    ])

                    SectionHeader(title: "Confidence by page", trailing: "empty = not reported")
                        .padding(.top, Theme.spacing.card)
                    AppCard {
                        PageConfidenceChart(pages: result.pages)
                    }

                    SectionHeader(title: "Structure", trailing: "\(result.blocks.count) blocks")
                        .padding(.top, Theme.spacing.card)

                    if result.isEmpty {
                        EmptyResultCard()
                    }

                    ForEach(result.blocks) { block in
                        BlockRow(
                            glyph: block.headingLevel > 0 ? "H\(block.headingLevel)" : "¶",
                            text: block.text,
                            meta: block.headingLevel > 0
                                ? "DocBlock.Heading · level \(block.headingLevel)"
                                : "DocBlock.Paragraph",
                            isHeading: block.headingLevel > 0
                        )
                    }
                }
                .padding(.horizontal, Theme.spacing.screen)
                .padding(.bottom, Theme.spacing.section)
            }
        } else {
            VStack(alignment: .leading, spacing: Theme.spacing.row) {
                Text("Nothing recognized yet")
                    .font(Theme.typography.display)
                    .foregroundColor(Theme.color.ink)
                Text(
                    "Pick a source on the home screen. The result lands here: counts, "
                        + "per-page confidence, and the headings and paragraphs "
                        + "DocumentStructure reconstructed."
                )
                .font(Theme.typography.body)
                .foregroundColor(Theme.color.inkMuted)
                Spacer()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Theme.spacing.screen)
        }
    }
}

/// The empty result, stated as a result.
///
/// A recognized document with no lines is a valid answer, and not the same thing as a
/// failure. On the Simulator it is also the *expected* answer: Vision text recognition
/// needs the Neural Engine, so `performRequests` succeeds and returns nothing.
private struct EmptyResultCard: View {
    var body: some View {
        AppCard {
            Text("No text on any page")
                .font(Theme.typography.bodyStrong)
                .foregroundColor(Theme.color.ink)
            Text(
                "A valid result, not a failure. On the Simulator this is expected: Vision "
                    + "text recognition needs the Neural Engine, so it succeeds and returns "
                    + "nothing. On hardware this page has text."
            )
            .font(Theme.typography.caption)
            .foregroundColor(Theme.color.inkSubtle)
        }
    }
}
