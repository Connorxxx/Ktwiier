import UIKit
import SwiftUI
import ComposeApp

// Transparent overlay that sits on top of the entire view hierarchy.
// Routes touches to either the tab bar or the compose view,
// completely bypassing UITabBarController's internal container views.
class TouchRoutingOverlay: UIView {
    weak var tabBar: UITabBar?
    weak var composeView: UIView?

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        // 1) If touch is in the tab bar area → forward to tab bar
        if let tabBar = tabBar,
           !tabBar.isHidden,
           tabBar.alpha > 0.01,
           tabBar.isUserInteractionEnabled {
            let tabBarPoint = convert(point, to: tabBar)
            if tabBar.point(inside: tabBarPoint, with: event),
               let hit = tabBar.hitTest(tabBarPoint, with: event) {
                return hit
            }
        }

        // 2) Everything else → forward to compose view
        if let composeView = composeView {
            let composePoint = convert(point, to: composeView)
            if let hit = composeView.hitTest(composePoint, with: event) {
                return hit
            }
        }

        return nil
    }
}

// UITabBarController that hosts a Compose child VC and uses a
// touch-routing overlay to resolve the view hierarchy conflict.
class ComposeHostTabBarController: UITabBarController {
    private(set) var composeChild: UIViewController?
    private let touchOverlay = TouchRoutingOverlay()

    func installComposeChild(_ vc: UIViewController) {
        composeChild = vc
        addChild(vc)

        vc.view.frame = view.bounds.isEmpty
            ? UIScreen.main.bounds
            : view.bounds
        vc.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]

        // Compose view at the very bottom — tab bar renders on top
        view.insertSubview(vc.view, at: 0)

        // Transparent overlay on top of everything — routes all touches
        touchOverlay.composeView = vc.view
        touchOverlay.tabBar = tabBar
        touchOverlay.frame = view.bounds
        touchOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        touchOverlay.backgroundColor = .clear
        view.addSubview(touchOverlay)

        vc.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        composeChild?.view.frame = view.bounds
        // Ensure overlay stays on top after any layout pass
        view.bringSubviewToFront(touchOverlay)
    }

    // UITabBarController only forwards appearance to its selected
    // viewControllers child — not manually-added children.
    // We forward explicitly so ComposeUIViewController's lifecycle
    // reaches STARTED and collectAsStateWithLifecycle works.

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        composeChild?.beginAppearanceTransition(true, animated: animated)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        composeChild?.endAppearanceTransition()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        composeChild?.beginAppearanceTransition(false, animated: animated)
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        composeChild?.endAppearanceTransition()
    }
}

class TabBarDelegate: NSObject, UITabBarControllerDelegate {
    func tabBarController(_ tabBarController: UITabBarController, didSelect viewController: UIViewController) {
        guard let index = tabBarController.viewControllers?.firstIndex(of: viewController) else { return }
        MainViewControllerKt.onNativeTabSelected(index: Int32(index))
    }
}

struct ComposeView: UIViewControllerRepresentable {
    @Environment(\.colorScheme) var colorScheme

    func makeCoordinator() -> TabBarDelegate {
        TabBarDelegate()
    }

    func makeUIViewController(context: Context) -> ComposeHostTabBarController {
        let composeVC = MainViewControllerKt.MainViewController(isDarkTheme: colorScheme == .dark)

        let tabBarController = ComposeHostTabBarController()

        // Tab items with SF Symbols
        let homeVC = UIViewController()
        homeVC.tabBarItem = UITabBarItem(
            title: "首页",
            image: UIImage(systemName: "house"),
            selectedImage: UIImage(systemName: "house.fill")
        )

        let messagesVC = UIViewController()
        messagesVC.tabBarItem = UITabBarItem(
            title: "私信",
            image: UIImage(systemName: "envelope"),
            selectedImage: UIImage(systemName: "envelope.fill")
        )

        let searchVC = UIViewController()
        searchVC.tabBarItem = UITabBarItem(
            title: "搜索",
            image: UIImage(systemName: "magnifyingglass"),
            selectedImage: UIImage(systemName: "magnifyingglass")
        )

        let settingsVC = UIViewController()
        settingsVC.tabBarItem = UITabBarItem(
            title: "设置",
            image: UIImage(systemName: "gearshape"),
            selectedImage: UIImage(systemName: "gearshape.fill")
        )

        tabBarController.viewControllers = [homeVC, messagesVC, searchVC, settingsVC]
        tabBarController.delegate = context.coordinator

        // Overlay ComposeVC (handles appearance forwarding internally)
        tabBarController.installComposeChild(composeVC)

        // Register with Kotlin bridge for bidirectional sync
        MainViewControllerKt.registerTabBarController(controller: tabBarController)

        return tabBarController
    }

    func updateUIViewController(_ tabBarController: ComposeHostTabBarController, context: Context) {
        MainViewControllerKt.updateDarkTheme(viewController: tabBarController, isDarkTheme: colorScheme == .dark)
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
