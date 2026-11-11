import SwiftUI
import Shared

/// The iPhone app: the shared screens, full screen. While the app isn't in front, a plain
/// cover hides it, so the app switcher's preview shows nothing private.
@main
struct ChecklistApp: App {
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            ZStack {
                SharedScreens().ignoresSafeArea()
                if phase != .active {
                    Color(UIColor.systemBackground).ignoresSafeArea()
                }
            }
        }
    }
}

/// The shared screens (Kotlin, Compose Multiplatform) as a SwiftUI view.
struct SharedScreens: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
