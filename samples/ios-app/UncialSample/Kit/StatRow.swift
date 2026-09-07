import SwiftUI

/// One figure and what it counts.
struct Stat: Identifiable {
    let id = UUID()
    let value: String
    let label: String
}

/// The three numbers a recognized document is judged by.
///
/// Figures use the display family: they are the result, not a caption about it. A stat
/// whose value is unknown is rendered as `—` by the caller rather than dropped — a missing
/// tile would read as "this engine has no confidence" instead of "this engine does not
/// report one".
struct StatRow: View {

    let stats: [Stat]

    var body: some View {
        HStack(spacing: Theme.spacing.row) {
            ForEach(stats) { stat in
                VStack(spacing: Theme.spacing.tight) {
                    Text(stat.value)
                        .font(Theme.typography.figure)
                        .foregroundColor(Theme.color.ink)
                    Text(stat.label)
                        .font(Theme.typography.caption)
                        .foregroundColor(Theme.color.inkSubtle)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, Theme.spacing.card)
                .background(Theme.color.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.corner.card, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.corner.card, style: .continuous)
                        .stroke(Theme.color.outline, lineWidth: Theme.size.hairline)
                )
            }
        }
    }
}
