import UIKit
import SwiftUI
import ComposeApp

// Container that uses native UITabBar for iOS visual fidelity while Compose
// remains the single source of truth for actual screen content.
final class ComposeHostViewController: UIViewController, UITabBarDelegate {
    private let composeChild: UIViewController
    private let nativeTopBar = MainViewControllerKt.createNativeTopBarView()
    private let nativeTabBar = UITabBar()
    private var tabItems: [UITabBarItem] = []
    private let tabForegroundColor = UIColor { traitCollection in
        traitCollection.userInterfaceStyle == .dark ? .white : .black
    }

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
        view.addSubview(nativeTopBar)

        tabItems = [
            UITabBarItem(
                title: "",
                image: UIImage(systemName: "house"),
                selectedImage: UIImage(systemName: "house.fill")
            ),
            UITabBarItem(
                title: "",
                image: UIImage(systemName: "envelope"),
                selectedImage: UIImage(systemName: "envelope.fill")
            ),
            UITabBarItem(
                title: "",
                image: UIImage(systemName: "magnifyingglass"),
                selectedImage: UIImage(systemName: "magnifyingglass")
            ),
            UITabBarItem(
                title: "",
                image: UIImage(systemName: "gearshape"),
                selectedImage: UIImage(systemName: "gearshape.fill")
            )
        ]

        applyTabBarAppearance()

        tabItems.forEach { item in
            item.imageInsets = .zero
        }
        nativeTabBar.setItems(tabItems, animated: false)
        nativeTabBar.selectedItem = tabItems.first
        nativeTabBar.delegate = self
        view.addSubview(nativeTabBar)

        MainViewControllerKt.registerNativeTabBar(tabBar: nativeTabBar)
        loadLocalizedTabTitles()
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)

        guard previousTraitCollection?.userInterfaceStyle != traitCollection.userInterfaceStyle else {
            return
        }
        applyTabBarAppearance()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        composeChild.view.frame = view.bounds

        let nativeTopBarHeight: CGFloat = 116.0
        nativeTopBar.frame = CGRect(
            x: 0.0,
            y: 0.0,
            width: view.bounds.width,
            height: nativeTopBarHeight
        )

        let fittedHeight = nativeTabBar.sizeThatFits(
            CGSize(width: view.bounds.width, height: 0.0)
        ).height
        MainViewControllerKt.updateNativeTabBarHeight(height: Double(fittedHeight))
        nativeTabBar.frame = CGRect(
            x: 0.0,
            y: view.bounds.height - fittedHeight,
            width: view.bounds.width,
            height: fittedHeight
        )
        view.bringSubviewToFront(nativeTopBar)
        view.bringSubviewToFront(nativeTabBar)
    }

    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        guard let index = tabItems.firstIndex(of: item) else { return }
        MainViewControllerKt.onNativeTabSelected(index: Int32(index))
    }

    private func applyTabBarAppearance() {
        let appearance = nativeTabBar.standardAppearance
        let titleOffset = UIOffset(horizontal: 0.0, vertical: 0.0)
        let itemAppearances = [
            appearance.stackedLayoutAppearance,
            appearance.inlineLayoutAppearance,
            appearance.compactInlineLayoutAppearance
        ]

        itemAppearances.forEach { itemAppearance in
            itemAppearance.normal.titlePositionAdjustment = titleOffset
            itemAppearance.selected.titlePositionAdjustment = titleOffset
            itemAppearance.normal.iconColor = tabForegroundColor
            itemAppearance.selected.iconColor = tabForegroundColor
            itemAppearance.normal.titleTextAttributes = [.foregroundColor: tabForegroundColor]
            itemAppearance.selected.titleTextAttributes = [.foregroundColor: tabForegroundColor]
        }

        nativeTabBar.standardAppearance = appearance
        nativeTabBar.tintColor = tabForegroundColor
        nativeTabBar.unselectedItemTintColor = tabForegroundColor
        if #available(iOS 15.0, *) {
            nativeTabBar.scrollEdgeAppearance = appearance
        }
    }

    private func loadLocalizedTabTitles() {
        MainViewControllerKt.loadLocalizedMainTabTitles { [weak self] home, messages, search, settings in
            guard let self = self else { return }

            DispatchQueue.main.async {
                let titles = [
                    self.toSwiftString(home),
                    self.toSwiftString(messages),
                    self.toSwiftString(search),
                    self.toSwiftString(settings)
                ]

                for (index, item) in self.tabItems.enumerated() where index < titles.count {
                    item.title = titles[index]
                }
                self.nativeTabBar.setItems(self.tabItems, animated: false)
            }
        }
    }

    private func toSwiftString(_ value: Any?) -> String {
        if let text = value as? String {
            return text
        }
        if let text = value as? NSString {
            return text as String
        }
        return ""
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
