import SwiftUI

/// The only screen that spends the gradient.
///
/// It says one thing — everything happens on the device — and then gets out of the way.
/// Every screen after it is paper, because from there on the content is someone's document
/// and the chrome should not compete with it.
struct WelcomeScreen: View {

    let onStart: () -> Void

    var body: some View {
        ZStack {
            Theme.color.heroGradient.ignoresSafeArea()
            VStack(alignment: .leading, spacing: Theme.spacing.row) {
                Spacer()
                Text("Welcome to Uncial")
                    .font(Theme.typography.display)
                    .foregroundColor(.white)
                Text(
                    "On-device OCR that gives you pages, lines and words — with boxes and "
                        + "confidence. Nothing leaves the device."
                )
                .font(Theme.typography.body)
                .foregroundColor(.white.opacity(0.86))
                PillButton(title: "Get started", emphasis: .secondary, action: onStart)
                    .padding(.top, Theme.spacing.row)
            }
            .padding(.horizontal, Theme.spacing.screen)
            .padding(.bottom, Theme.spacing.section)
        }
    }
}
