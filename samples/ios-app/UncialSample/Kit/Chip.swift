import SwiftUI

/// What a ``Chip`` is saying about the thing it is attached to.
enum ChipTone {
    /// A fact: a language, a code, a count.
    case neutral
    /// A fact the user chose, or one the engine confirmed.
    case selected
    /// Something the engine is doing — the OCR path, an active engine.
    case accent
    /// Something this device cannot do. Shown, never hidden.
    case unavailable
}

/// A small piece of state with a box around it.
///
/// ``ChipTone/unavailable`` exists because the whole point of the SDK's capability
/// reporting is that platforms differ; a UI that hides what it cannot do is the UI this
/// sample argues against.
struct Chip: View {

    let text: String
    var tone: ChipTone = .neutral

    var body: some View {
        Text(text)
            .font(Theme.typography.caption)
            .foregroundColor(foreground)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(background)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(border, lineWidth: Theme.size.hairline))
    }

    private var background: Color {
        switch tone {
        case .neutral: return Theme.color.surface
        case .selected: return Theme.color.selected
        case .accent: return Theme.color.accentMuted
        case .unavailable: return Theme.color.surfaceVariant
        }
    }

    private var foreground: Color {
        switch tone {
        case .neutral, .selected: return Theme.color.ink
        case .accent: return Theme.color.accent
        case .unavailable: return Theme.color.inkSubtle
        }
    }

    private var border: Color {
        tone == .neutral ? Theme.color.outline : .clear
    }
}
