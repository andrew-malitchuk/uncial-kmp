import Foundation
import UIKit
import Uncial

/// Drives Uncial and exposes the result to the views.
///
/// Everything crossing the Kotlin boundary happens here, so the screens stay free of
/// generated Obj-C types. The state is the same shape as the Android sample's
/// `OcrUiState`: the two apps are written twice, from one list.
@MainActor
final class OcrModel: ObservableObject {

    /// One reconstructed block, reduced to what `BlockRow` renders.
    struct Block: Identifiable {
        let id = UUID()
        let text: String
        /// 0 for a paragraph, 1 or 2 for a heading level.
        let headingLevel: Int
    }

    /// An extraction in flight.
    struct Run {
        var label: String
        var completed: Int = 0
        var total: Int = 0
        /// `nil` until `OcrProgress.Started` says — whether the engine runs at all is
        /// decided after the document has been opened.
        var source: String? = nil

        /// `nil` while the page count is unknown, so the ring can spin instead of lying.
        var fraction: Double? { total > 0 ? Double(completed) / Double(total) : nil }
    }

    /// A finished extraction.
    struct Result {
        let label: String
        let source: String
        let pageCount: Int
        let lines: Int
        let words: Int
        let confidence: Float?
        let elapsedMillis: Int
        let pages: [PageConfidence]
        let blocks: [Block]

        var isEmpty: Bool { lines == 0 }
    }

    @Published var capabilities: OcrCapabilities?
    @Published var hasDigitalTextLayer = false
    @Published var engineNote: String?
    @Published var run: Run?
    @Published var result: Result?
    @Published var error: String?

    var isRunning: Bool { run != nil }

    /// The extraction in flight, so a second request replaces it instead of queueing
    /// behind it. The engine serialises internally anyway, so without this a handful of
    /// impatient taps become a handful of sequential runs.
    private var running: Task<Void, Never>?

    /// One client for the app's lifetime: it holds engine state, and rebuilding it per
    /// document would throw that away.
    private lazy var client: UncialClient = UncialBootstrap.shared.createClient(
        languages: [OcrLanguage.Companion.shared.Ukrainian, OcrLanguage.Companion.shared.English],
        renderDpi: 200,
        // On, because the result screen reports a word count. It costs an extra pass
        // inside the engine, which is the trade the capability matrix describes when it
        // says `words: yes`.
        includeWords: true,
        preferDigitalLayer: true,
        onLog: { message in print("uncial \(message)") }
    )

    func describeCapabilities() {
        capabilities = client.capabilities
        hasDigitalTextLayer = client.hasDigitalTextLayerSupport
        guard let capabilities else {
            engineNote = "no engine registered (engines: \(UncialBootstrap.shared.registeredEngines()))"
            return
        }
        if !capabilities.languages.contains(where: { $0.tesseractCode == "ukr" }) {
            engineNote = "Vision has no Ukrainian before iOS 16, so this device will "
                + "recognize a Ukrainian page as Latin rather than failing."
        }
    }

    /// Runs one of the PDFs bundled in the app.
    func run(resource: String) {
        guard let url = Bundle.main.url(forResource: resource, withExtension: "pdf"),
              let data = try? Data(contentsOf: url) else {
            error = "could not read \(resource).pdf from the bundle"
            return
        }
        run(label: "\(resource).pdf") { client, onProgress in
            // `extractOrThrow` under the hood: Kotlin's Result does not survive the trip
            // to Obj-C, so the iOS-facing API throws instead.
            try await UncialBootstrap.shared.extract(client: client, pdf: data, onProgress: onProgress)
        }
    }

    /// Runs a PDF the user picked from Files.
    ///
    /// A document handed over by the picker lives outside the app's sandbox, so reading it
    /// requires holding a security-scoped resource for the duration of the read.
    func run(pickedFile url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            run(label: url.lastPathComponent) { client, onProgress in
                try await UncialBootstrap.shared.extract(client: client, pdf: data, onProgress: onProgress)
            }
        } catch {
            self.error = "could not read \(url.lastPathComponent): \(error.localizedDescription)"
        }
    }

    /// Recognizes a photo from the camera or the photo library.
    ///
    /// The PDF path and this one differ in one call: `extract(client:image:)` wraps the
    /// image in a `Raster` and goes straight to the recognizer, because rasterization and
    /// recognition are separate roles in the SDK. Everything after it is shared.
    func run(image: UIImage, label: String) {
        run(label: label) { client, onProgress in
            try await UncialBootstrap.shared.extract(client: client, image: image, onProgress: onProgress)
        }
    }

    /// Stops the extraction in flight.
    ///
    /// Cooperative, like every cancellation here: Vision finishes the page it is on.
    func cancel() {
        running?.cancel()
        running = nil
        run = nil
    }

    private func run(
        label: String,
        extract: @escaping (UncialClient, @escaping (UncialRunProgress) -> Void) async throws -> OcrDocument
    ) {
        running?.cancel()
        run = Run(label: label)
        result = nil
        error = nil

        running = Task {
            let started = Date()
            do {
                let document = try await extract(client) { [weak self] progress in
                    // The callback arrives on whichever thread the pipeline is on, and
                    // this model is @MainActor: hopping is the caller's job, which is what
                    // the Kotlin side's KDoc says.
                    Task { @MainActor [weak self] in
                        guard var current = self?.run else { return }
                        current.completed = Int(progress.completed)
                        current.total = Int(progress.total)
                        current.source = progress.source
                        self?.run = current
                    }
                }
                try Task.checkCancellation()

                // Off the main actor: the summary walks every line of the document, and a
                // 300-page scan is exactly the case this SDK exists for.
                let summary = await Self.summarize(document)
                try Task.checkCancellation()

                result = Result(
                    label: label,
                    source: summary.source,
                    pageCount: Int(summary.pageCount),
                    lines: Int(summary.lines),
                    words: Int(summary.words),
                    confidence: summary.confidence?.floatValue,
                    elapsedMillis: Int(Date().timeIntervalSince(started) * 1000),
                    pages: summary.pages.map {
                        PageConfidence(pageNumber: Int($0.number), confidence: $0.confidence?.floatValue)
                    },
                    blocks: summary.blocks.map { block in
                        if let heading = block as? DocBlockHeading {
                            return Block(text: heading.text, headingLevel: Int(heading.level))
                        }
                        return Block(text: block.text, headingLevel: 0)
                    }
                )
            } catch is CancellationError {
                // Superseded by a newer request; the newer one owns the UI now.
                return
            } catch {
                // Cancellation does not always surface as CancellationError: work already
                // inside the engine can fail on its way out instead. Without this guard a
                // superseded run would overwrite the newer one's state.
                if Task.isCancelled { return }
                self.error = "extraction failed: \(error.localizedDescription)"
            }
            run = nil
        }
    }

    /// Reduces the document away from the main actor.
    ///
    /// `nonisolated` is what does it: a nonisolated `async` method of a `@MainActor` type
    /// runs on the concurrent executor instead of inheriting the actor.
    private nonisolated static func summarize(_ document: OcrDocument) async -> UncialDocumentSummary {
        UncialBootstrap.shared.summarize(document: document)
    }
}
