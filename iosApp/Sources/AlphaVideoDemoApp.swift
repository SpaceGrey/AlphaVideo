import SwiftUI
import AlphaVideoDemo

@main
struct AlphaVideoDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeScreen().ignoresSafeArea()
        }
    }
}

private struct ComposeScreen: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
