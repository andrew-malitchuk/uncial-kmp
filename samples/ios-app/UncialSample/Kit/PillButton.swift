import SwiftUI

/// How much weight a ``PillButton`` carries on its screen.
enum PillEmphasis {
    /// The screen's single action: ink fill, white label.
    case primary
    /// A real alternative to ``primary``: paper fill, hairline border.
    case secondary
    /// An action the user is not expected to take: no fill at all.
    case ghost
}

/// The only button in this app.
///
/// Three emphases rather than three components, because they differ in colour and nothing
/// else. A screen that needs a fourth kind of button usually needs one fewer action.
struct PillButton: View {

    let title: String
    var emphasis: PillEmphasis = .primary
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.typography.label)
                .foregroundColor(enabled ? foreground : Theme.color.inkSubtle)
                .frame(maxWidth: .infinity)
                .frame(height: Theme.size.control)
                .background(enabled ? background : Theme.color.surfaceVariant)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(border, lineWidth: Theme.size.hairline))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private var background: Color {
        switch emphasis {
        case .primary: return Theme.color.ink
        case .secondary: return Theme.color.surface
        case .ghost: return .clear
        }
    }

    private var foreground: Color {
        switch emphasis {
        case .primary: return Theme.color.onInk
        case .secondary: return Theme.color.ink
        case .ghost: return Theme.color.inkMuted
        }
    }

    private var border: Color {
        emphasis == .primary ? .clear : Theme.color.outline
    }
}
