import PhotosUI
import SwiftUI
import UIKit

/// The two ways a photo gets into this app.
///
/// Both are UIKit controllers wrapped for SwiftUI: as of iOS 15 there is no native SwiftUI
/// camera at all, and `PhotosPicker` — the SwiftUI face of `PHPickerViewController` — is
/// iOS 16. The deployment target here is 15, which is the same product decision that costs
/// this sample `NavigationStack` and costs Vision its Ukrainian.

/// The system photo library picker.
///
/// Needs no photo-library permission and no `NSPhotoLibraryUsageDescription`: the picker
/// runs out of process and this app only ever receives the one image the user chose.
struct PhotoLibraryPicker: UIViewControllerRepresentable {

    let onPicked: (UIImage) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var configuration = PHPickerConfiguration()
        configuration.filter = .images
        configuration.selectionLimit = 1
        let controller = PHPickerViewController(configuration: configuration)
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {

        private let onPicked: (UIImage) -> Void

        init(onPicked: @escaping (UIImage) -> Void) {
            self.onPicked = onPicked
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard let provider = results.first?.itemProvider,
                  provider.canLoadObject(ofClass: UIImage.self) else { return }
            // Asynchronous, and on an arbitrary queue: the image may have to come down from
            // iCloud Photos first, which is why the picker cannot just hand one over.
            provider.loadObject(ofClass: UIImage.self) { [onPicked] object, _ in
                guard let image = object as? UIImage else { return }
                DispatchQueue.main.async { onPicked(image) }
            }
        }
    }
}

/// The system camera, via `UIImagePickerController`.
///
/// Requires `NSCameraUsageDescription` in Info.plist — unlike the library picker, this one
/// runs in-process and does need the permission. Missing it is not a denied prompt but an
/// immediate crash.
struct CameraPicker: UIViewControllerRepresentable {

    let onPicked: (UIImage) -> Void

    /// Whether this device has a camera at all. The Simulator does not.
    static var isAvailable: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = .camera
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

        private let onPicked: (UIImage) -> Void

        init(onPicked: @escaping (UIImage) -> Void) {
            self.onPicked = onPicked
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            picker.dismiss(animated: true)
            // `.originalImage`, never `.editedImage`: nothing was edited, and the edited
            // key is absent unless allowsEditing is set. The image arrives with its
            // orientation in `imageOrientation` rather than in the pixels — Uncial
            // normalizes that on the Kotlin side, since the CGImage Vision sees would
            // otherwise be sideways.
            guard let image = info[.originalImage] as? UIImage else { return }
            onPicked(image)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}
