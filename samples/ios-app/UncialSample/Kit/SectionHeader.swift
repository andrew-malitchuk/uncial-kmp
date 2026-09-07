import SwiftUI

/// The label that names a group of cards, with an optional fact on the right.
///
/// The trailing slot is usually where a screen admits something — how many pages, which
/// engine, what was dropped.
struct SectionHeader: View {

    let title: String
    var trailing: String? = nil

    var body: some View {
        HStack(alignment: .lastTextBaseline) {
            Text(title)
                .font(Theme.typography.title)
                .foregroundColor(Theme.color.ink)
            Spacer()
            if let trailing {
                Text(trailing)
                    .font(Theme.typography.mono)
                    .foregroundColor(Theme.color.inkSubtle)
            }
        }
    }
}
