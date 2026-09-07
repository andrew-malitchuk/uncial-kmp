import SwiftUI

/// One line of the honest matrix: what the engine does, and whether it does it here.
struct Capability: Identifiable {
    let id = UUID()
    let name: String
    let supported: Bool
    var note: String? = nil
}

/// The capability matrix, rendered as rows rather than as a grid.
///
/// `no` is written out, not left blank. An empty cell reads as "the designer ran out of
/// room"; the word reads as the answer, which is the entire reason `OcrCapabilities`
/// exists. The answer sits on the name's line and the note runs underneath — beside a
/// two-line note it would centre between the lines and read as a word inside the sentence.
struct CapabilityList: View {

    let capabilities: [Capability]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(capabilities.enumerated()), id: \.element.id) { index, capability in
                if index > 0 {
                    Rectangle()
                        .fill(Theme.color.outline)
                        .frame(height: Theme.size.hairline)
                }
                VStack(alignment: .leading, spacing: Theme.spacing.tight) {
                    HStack {
                        Text(capability.name)
                            .font(Theme.typography.bodyStrong)
                            .foregroundColor(Theme.color.ink)
                        Spacer()
                        Text(capability.supported ? "yes" : "no")
                            .font(Theme.typography.label)
                            .foregroundColor(
                                capability.supported ? Theme.color.ink : Theme.color.inkSubtle
                            )
                    }
                    if let note = capability.note {
                        Text(note)
                            .font(Theme.typography.caption)
                            .foregroundColor(Theme.color.inkSubtle)
                    }
                }
                .padding(.vertical, Theme.spacing.row)
            }
        }
    }
}
