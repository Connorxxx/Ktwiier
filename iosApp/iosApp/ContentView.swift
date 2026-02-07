import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    @Environment(\.colorScheme) var colorScheme

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(isDarkTheme: colorScheme == .dark)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        MainViewControllerKt.updateDarkTheme(viewController: uiViewController, isDarkTheme: colorScheme == .dark)
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
