import SwiftUI
import UniformTypeIdentifiers

/// The five screens, as a destination each.
enum SampleDestination {
    case welcome
    case home
    case progress
    case document
    case device
}

/// The whole app: five destinations and the rules for moving between them.
///
/// A stack, not tabs. `home` is the root and everything else is entered from it and backed
/// out of. The depth is never more than one, so the "stack" is a single destination plus
/// the rule that back means home — and on iOS 15 there is no `NavigationStack` to do it
/// with anyway, which is the same platform floor that costs Vision its Ukrainian.
struct ContentView: View {

    @StateObject private var model = OcrModel()
    @State private var destination: SampleDestination = .welcome
    @State private var isPickingFile = false
    @State private var isTakingPhoto = false
    @State private var isPickingImage = false

    var body: some View {
        Group {
            if destination == .welcome {
                WelcomeScreen { destination = .home }
            } else {
                VStack(spacing: 0) {
                    topBar
                    content
                }
                .background(Theme.color.canvas.ignoresSafeArea())
            }
        }
        .task { model.describeCapabilities() }
        // Starting an extraction is what navigates, and finishing one navigates again.
        // Keeping the rule here rather than in six callbacks means every entry point --
        // including a run that starts without a tap -- obeys it.
        .onChange(of: model.isRunning) { isRunning in
            if isRunning {
                destination = .progress
            } else if destination == .progress {
                // A finished run has a result; a cancelled one does not, and sending the
                // user to an empty result screen would read as a failure.
                destination = model.result == nil ? .home : .document
            }
        }
        // The counterpart of Android's ACTION_OPEN_DOCUMENT: any PDF from Files, iCloud
        // Drive or another provider, not just the two bundled fixtures.
        .fileImporter(
            isPresented: $isPickingFile,
            allowedContentTypes: [.pdf],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first { model.run(pickedFile: url) }
            case .failure(let error):
                model.error = "could not pick a file: \(error.localizedDescription)"
            }
        }
        .sheet(isPresented: $isTakingPhoto) {
            CameraPicker { image in
                isTakingPhoto = false
                model.run(image: image, label: "camera photo")
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $isPickingImage) {
            PhotoLibraryPicker { image in
                isPickingImage = false
                model.run(image: image, label: "library image")
            }
            .ignoresSafeArea()
        }
        // An alert rather than an inline error: the failure belongs to the run that just
        // ended, not to the screen the user is now looking at.
        .alert("Extraction failed", isPresented: Binding(
            get: { model.error != nil },
            set: { if !$0 { model.error = nil } }
        )) {
            Button("OK", role: .cancel) { model.error = nil }
        } message: {
            Text(model.error ?? "")
        }
    }

    @ViewBuilder
    private var topBar: some View {
        switch destination {
        case .home:
            AppTopBar(title: "Uncial", action: "Device") { destination = .device }
        case .progress:
            AppTopBar(title: "Recognizing")
        case .document:
            // The document's own name is the screen's display title; repeating it here
            // would be the same sentence twice, one of them truncated.
            AppTopBar(title: "Result", onBack: { destination = .home })
        case .device:
            AppTopBar(title: "Device", onBack: { destination = .home })
        case .welcome:
            EmptyView()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch destination {
        case .home:
            HomeScreen(
                languages: model.capabilities?
                    .languages
                    .map { $0.bcp47 ?? $0.tesseractCode }
                    .joined(separator: "+") ?? "",
                lastResult: model.result,
                enabled: !model.isRunning,
                onOpenResult: { destination = .document },
                onScanned: { model.run(resource: "sample-scan") },
                onDigital: { model.run(resource: "sample-digital") },
                onPickPdf: { isPickingFile = true },
                onPhoto: { isTakingPhoto = true },
                onGallery: { isPickingImage = true }
            )
        case .progress:
            if let run = model.run {
                ProgressScreen(run: run) {
                    model.cancel()
                    destination = .home
                }
            }
        case .document:
            DocumentScreen(result: model.result)
        case .device:
            DeviceScreen(
                capabilities: model.capabilities,
                hasDigitalTextLayer: model.hasDigitalTextLayer,
                note: model.engineNote
            )
        case .welcome:
            EmptyView()
        }
    }
}
