import SwiftUI

/// One bar: a page number and the confidence the engine reported for it.
struct PageConfidence: Identifiable {
    let id = UUID()
    let pageNumber: Int
    /// `nil` when the engine reported none — a digital text layer does not guess.
    let confidence: Float?
}

/// Per-page confidence, as bars.
///
/// A page with no reported confidence draws an empty track rather than a zero-height bar:
/// "not reported" and "reported as nothing" are different claims, and only one of them is
/// the engine's fault.
struct PageConfidenceChart: View {

    let pages: [PageConfidence]

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            ForEach(pages) { page in
                VStack(spacing: Theme.spacing.tight) {
                    GeometryReader { geometry in
                        ZStack(alignment: .bottom) {
                            RoundedRectangle(
                                cornerRadius: Theme.corner.small,
                                style: .continuous
                            )
                            .fill(Theme.color.surfaceVariant)
                            if let confidence = page.confidence {
                                RoundedRectangle(
                                    cornerRadius: Theme.corner.small,
                                    style: .continuous
                                )
                                .fill(Theme.color.accentMuted)
                                .frame(
                                    height: geometry.size.height
                                        * CGFloat(min(max(confidence, 0.05), 1))
                                )
                            }
                        }
                    }
                    .frame(height: 52)
                    Text("p\(page.pageNumber)")
                        .font(Theme.typography.mono)
                        .foregroundColor(Theme.color.inkSubtle)
                }
            }
        }
    }
}
