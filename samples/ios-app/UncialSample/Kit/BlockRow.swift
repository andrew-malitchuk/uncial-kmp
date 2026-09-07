import SwiftUI

/// One reconstructed block, labelled with what `DocumentStructure` decided it was.
///
/// The `H1` / `¶` glyph is the point of the screen: the SDK returns structure, and a
/// sample that rendered the blocks as undifferentiated paragraphs would be hiding its own
/// result. The text itself is in the serif — it is the document speaking, not the app.
struct BlockRow: View {

    let glyph: String
    let text: String
    let meta: String
    var isHeading: Bool = false

    var body: some View {
        AppCard {
            HStack(alignment: .top, spacing: Theme.spacing.row) {
                Text(glyph)
                    .font(Theme.typography.mono)
                    .foregroundColor(Theme.color.inkMuted)
                    .frame(width: 28, height: 28)
                    .background(isHeading ? Theme.color.selected : Theme.color.surfaceVariant)
                    .clipShape(
                        RoundedRectangle(cornerRadius: Theme.corner.small, style: .continuous)
                    )
                VStack(alignment: .leading, spacing: Theme.spacing.tight) {
                    Text(text)
                        .font(Theme.typography.recognized)
                        .fontWeight(isHeading ? .semibold : .regular)
                        .foregroundColor(Theme.color.ink)
                        .textSelection(.enabled)
                    Text(meta)
                        .font(Theme.typography.mono)
                        .foregroundColor(Theme.color.inkSubtle)
                }
            }
        }
    }
}
