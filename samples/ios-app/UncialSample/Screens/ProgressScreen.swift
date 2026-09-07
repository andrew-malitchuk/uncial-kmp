import SwiftUI

/// What the SDK is doing right now, page by page.
///
/// The count is the honest unit of progress: the pipeline reports once per page, and a
/// page is also the granularity at which cancellation lands. The ring spins until the page
/// count is known — an image knows immediately, a PDF does not.
struct ProgressScreen: View {

    let run: OcrModel.Run
    let onCancel: () -> Void

    @State private var spin = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            ZStack {
                Circle()
                    .stroke(Theme.color.surfaceVariant, lineWidth: 6)
                if let fraction = run.fraction {
                    Circle()
                        .trim(from: 0, to: CGFloat(fraction))
                        .stroke(
                            Theme.color.accent,
                            style: StrokeStyle(lineWidth: 6, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .animation(.easeOut(duration: 0.3), value: fraction)
                } else {
                    // Indeterminate: drawn rather than a ProgressView, because the
                    // determinate and indeterminate states have to be the same ring.
                    Circle()
                        .trim(from: 0, to: 0.22)
                        .stroke(
                            Theme.color.accent,
                            style: StrokeStyle(lineWidth: 6, lineCap: .round)
                        )
                        .rotationEffect(.degrees(spin ? 360 : 0))
                        .animation(
                            .linear(duration: 1).repeatForever(autoreverses: false),
                            value: spin
                        )
                        .onAppear { spin = true }
                }
                VStack(spacing: Theme.spacing.tight) {
                    Text(run.total > 0 ? "\(run.completed)" : "…")
                        .font(Theme.typography.display)
                        .foregroundColor(Theme.color.ink)
                    Text(run.total > 0 ? "of \(run.total) pages" : "opening…")
                        .font(Theme.typography.caption)
                        .foregroundColor(Theme.color.inkSubtle)
                }
            }
            .frame(width: 132, height: 132)

            Text(run.label)
                .font(Theme.typography.bodyStrong)
                .foregroundColor(Theme.color.ink)
                .padding(.top, Theme.spacing.section)
            if let source = run.source {
                Text(sourceLabel(source))
                    .font(Theme.typography.mono)
                    .foregroundColor(Theme.color.inkSubtle)
            }

            PillButton(title: "Cancel", emphasis: .ghost, action: onCancel)
                .frame(width: 160)
                .padding(.top, Theme.spacing.section)
            Text("Cancellation is cooperative: the engine finishes the page it is on.")
                .font(Theme.typography.caption)
                .foregroundColor(Theme.color.inkSubtle)
                .multilineTextAlignment(.center)
                .padding(.top, Theme.spacing.tight)
            Spacer()
        }
        .padding(.horizontal, Theme.spacing.screen)
    }
}

/// Which path produced a result, in words rather than as an enum constant.
///
/// `Ocr` and `DigitalTextLayer` are names for a compiler; what a reader needs to know is
/// that one of them is a guess and the other is not.
func sourceLabel(_ source: String) -> String {
    switch source {
    case "Ocr": return "recognized by the engine"
    case "DigitalTextLayer": return "read from the text layer"
    case "Mixed": return "mixed: some pages read, some recognized"
    default: return source
    }
}
