import SwiftUI
import Uncial

@main
struct UncialSampleApp: App {

    init() {
        // iOS has no androidx.startup, so the Vision engine is registered explicitly.
        // Idempotent, and cheap: it stores a factory and loads nothing.
        UncialBootstrap.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
