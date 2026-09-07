import SwiftUI

// The design system's token groups, one struct each.
//
// Same shape as the Android sample's `ui/core`: a token group is a vocabulary, the values
// live in exactly one place, and no view names a colour or a size directly. The two apps
// are written twice — SwiftUI and Compose share nothing — but they are written from the
// same list.

/// The palette. Warm and paper-like: recognized text is the content, so the chrome stays
/// quiet. `heroGradient` is the only loud surface, and it appears on one screen.
struct ThemeColor {
    let ink = Color(hex: 0x141210)
    let inkMuted = Color(hex: 0x4A433C)
    let inkSubtle = Color(hex: 0x8B8177)
    let canvas = Color(hex: 0xF7F4ED)
    let surface = Color(hex: 0xFFFDF9)
    let surfaceVariant = Color(hex: 0xEFE9DE)
    let selected = Color(hex: 0xDED2B8)
    let accent = Color(hex: 0xE2561F)
    let accentMuted = Color(hex: 0xF6D7A9)
    let outline = Color(hex: 0xE4DDD0)
    let onAccent = Color.white
    let onInk = Color.white

    /// Five stops rather than two: the reference is a photograph of an ember, and a
    /// two-stop gradient reads as a colour swatch.
    let heroGradient = LinearGradient(
        stops: [
            .init(color: Color(hex: 0xF7D5CD), location: 0.00),
            .init(color: Color(hex: 0xF2B49F), location: 0.26),
            .init(color: Color(hex: 0xDD6A2C), location: 0.56),
            .init(color: Color(hex: 0x5A2A1B), location: 0.82),
            .init(color: Color(hex: 0x20110C), location: 1.00),
        ],
        startPoint: .top,
        endPoint: .bottom
    )
}

/// The type scale. Two families with one job each: a serif for display and for recognized
/// text, a sans for everything the UI says about it.
struct ThemeTypography {
    let display = Font.custom("Fraunces-SemiBold", size: 28)
    let displaySmall = Font.custom("Fraunces-SemiBold", size: 20)
    let figure = Font.custom("Fraunces-SemiBold", size: 26)
    let title = Font.custom("Inter-SemiBold", size: 14)
    let body = Font.custom("Inter-Regular", size: 13)
    let bodyStrong = Font.custom("Inter-Medium", size: 13)
    let label = Font.custom("Inter-SemiBold", size: 13)
    let caption = Font.custom("Inter-Regular", size: 11.5)
    let mono = Font.system(size: 11, design: .monospaced)

    /// Recognized text is rendered in the platform serif, not in Fraunces — which has no
    /// Cyrillic at all. A Ukrainian scan in it would be tofu, and a sample whose whole
    /// point is a Ukrainian document would be demonstrating the wrong thing.
    let recognized = Font.system(size: 15, design: .serif)
}

/// The 4 pt grid, named by role rather than by number.
struct ThemeSpacing {
    let screen: CGFloat = 20
    let section: CGFloat = 24
    let card: CGFloat = 16
    let row: CGFloat = 12
    let tight: CGFloat = 4
}

/// Corner radii. `control` is a stadium, not a rounded rectangle.
struct ThemeCorner {
    let panel: CGFloat = 26
    let card: CGFloat = 18
    let small: CGFloat = 12
}

/// Fixed sizes more than one component has to agree on.
struct ThemeSize {
    let control: CGFloat = 48
    let icon: CGFloat = 38
    let hairline: CGFloat = 1
}

/// The one way to read a design token.
///
/// Static rather than an `EnvironmentKey`: there is a single palette and no dark variant,
/// so an environment lookup would buy indirection and nothing else. The day a second
/// theme exists, this is the type that gains the switch.
enum Theme {
    static let color = ThemeColor()
    static let typography = ThemeTypography()
    static let spacing = ThemeSpacing()
    static let corner = ThemeCorner()
    static let size = ThemeSize()
}

extension Color {
    /// Hex literals, so the palette reads the same here as it does in the style guide and
    /// in the Android sample.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
