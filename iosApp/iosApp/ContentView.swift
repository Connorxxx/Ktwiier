import UIKit
import SwiftUI
import ComposeApp

// Container that uses native UITabBar for iOS visual fidelity while Compose
// remains the single source of truth for actual screen content.
final class ComposeHostViewController: UIViewController, UITabBarDelegate {
    private let composeChild: UIViewController
    private let nativeTabBar = UITabBar()
    private var tabItems: [UITabBarItem] = []

    init(composeChild: UIViewController) {
        self.composeChild = composeChild
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = .clear

        addChild(composeChild)
        composeChild.view.frame = view.bounds
        composeChild.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(composeChild.view)
        composeChild.didMove(toParent: self)

        tabItems = [
            UITabBarItem(
                title: "首页",
                image: UIImage(systemName: "house"),
                selectedImage: UIImage(systemName: "house.fill")
            ),
            UITabBarItem(
                title: "私信",
                image: UIImage(systemName: "envelope"),
                selectedImage: UIImage(systemName: "envelope.fill")
            ),
            UITabBarItem(
                title: "搜索",
                image: UIImage(systemName: "magnifyingglass"),
                selectedImage: UIImage(systemName: "magnifyingglass")
            ),
            UITabBarItem(
                title: "设置",
                image: UIImage(systemName: "gearshape"),
                selectedImage: UIImage(systemName: "gearshape.fill")
            )
        ]
        nativeTabBar.setItems(tabItems, animated: false)
        nativeTabBar.selectedItem = tabItems.first
        nativeTabBar.delegate = self
        view.addSubview(nativeTabBar)

        MainViewControllerKt.registerNativeTabBar(tabBar: nativeTabBar)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        composeChild.view.frame = view.bounds

        let fittedHeight = nativeTabBar.sizeThatFits(
            CGSize(width: view.bounds.width, height: 0.0)
        ).height
        let safeBottom = view.safeAreaInsets.bottom
        let totalHeight = fittedHeight + safeBottom

        nativeTabBar.frame = CGRect(
            x: 0.0,
            y: view.bounds.height - totalHeight,
            width: view.bounds.width,
            height: totalHeight
        )
        view.bringSubviewToFront(nativeTabBar)
    }

    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        guard let index = tabItems.firstIndex(of: item) else { return }
        MainViewControllerKt.onNativeTabSelected(index: Int32(index))
    }
}

struct ComposeView: UIViewControllerRepresentable {
    @Environment(\.colorScheme) var colorScheme

    func makeUIViewController(context: Context) -> ComposeHostViewController {
        let composeVC = MainViewControllerKt.MainViewController(isDarkTheme: colorScheme == .dark)
        return ComposeHostViewController(composeChild: composeVC)
    }

    func updateUIViewController(_ uiViewController: ComposeHostViewController, context: Context) {
        MainViewControllerKt.updateDarkTheme(
            viewController: uiViewController,
            isDarkTheme: colorScheme == .dark
        )
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
