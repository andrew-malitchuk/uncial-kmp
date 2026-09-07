import SwiftUI

/// The bar every screen below the welcome wears.
///
/// It carries the navigation: a back affordance on the left when there is somewhere to go
/// back to, and at most one action on the right. There is no tab bar in this app — the
/// screens form a stack, and this is the only control that moves through it.
///
/// Hand-rolled rather than a `NavigationBar`, for the same reason the app has no
/// `NavigationStack`: that is iOS 16, and the deployment target here is 15.
struct AppTopBar: View {

    let title: String
    var onBack: (() -> Void)? = nil
    var action: String? = nil
    var onAction: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: Theme.spacing.row) {
            if let onBack {
                Button(action: onBack) {
                    Text("←")
                        .font(Theme.typography.displaySmall)
                        .foregroundColor(Theme.color.ink)
                }
                .buttonStyle(.plain)
            }
            Text(title)
                .font(Theme.typography.title)
                .foregroundColor(Theme.color.ink)
                .lineLimit(1)
            Spacer()
            if let action, let onAction {
                Button(action: onAction) {
                    Text(action)
                        .font(Theme.typography.label)
                        .foregroundColor(Theme.color.inkMuted)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, Theme.spacing.screen)
        .frame(height: 52)
    }
}
