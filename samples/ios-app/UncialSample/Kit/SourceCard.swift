import SwiftUI

/// One thing the user can hand to the SDK.
///
/// The badge is the card's whole argument: `OCR` and `fast path` are not decoration, they
/// are which code path the tap will take, stated before the tap rather than explained
/// after it.
struct SourceCard: View {

    let glyph: String
    let title: String
    let subtitle: String
    var badge: String? = nil
    var badgeTone: ChipTone = .neutral
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            AppCard {
                HStack(spacing: Theme.spacing.row) {
                    Text(glyph)
                        .font(Theme.typography.title)
                        .foregroundColor(Theme.color.inkMuted)
                        .frame(width: Theme.size.icon, height: Theme.size.icon)
                        .background(Theme.color.surfaceVariant)
                        .clipShape(
                            RoundedRectangle(cornerRadius: Theme.corner.small, style: .continuous)
                        )
                    VStack(alignment: .leading, spacing: Theme.spacing.tight) {
                        Text(title)
                            .font(Theme.typography.bodyStrong)
                            .foregroundColor(enabled ? Theme.color.ink : Theme.color.inkSubtle)
                        Text(subtitle)
                            .font(Theme.typography.caption)
                            .foregroundColor(Theme.color.inkSubtle)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer(minLength: Theme.spacing.tight)
                    if let badge {
                        Chip(text: badge, tone: badgeTone)
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
