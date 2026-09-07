import SwiftUI

/// A surface that lies on the canvas rather than floating above it.
///
/// No shadows anywhere in this design: the separation is a hairline and a lighter fill.
/// On a paper palette a shadow reads as dirt.
struct AppCard<Content: View>: View {

    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.spacing.tight) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.spacing.card)
        .background(Theme.color.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.corner.card, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.corner.card, style: .continuous)
                .stroke(Theme.color.outline, lineWidth: Theme.size.hairline)
        )
    }
}
