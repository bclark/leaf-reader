import SwiftUI
import UIKit

extension View {
    /// Apply at each presentation boundary. Explicitly clearing the native override also
    /// handles switching back to System while a full-screen cover remains presented.
    func leafAppearance(_ theme: AppTheme) -> some View {
        background(PresentationAppearance(theme: theme).frame(width: 0, height: 0))
    }
}

private struct PresentationAppearance: UIViewControllerRepresentable {
    let theme: AppTheme

    func makeUIViewController(context: Context) -> AppearanceController {
        AppearanceController(style: interfaceStyle)
    }

    func updateUIViewController(_ controller: AppearanceController, context: Context) {
        controller.style = interfaceStyle
        controller.applyStyle()
    }

    private var interfaceStyle: UIUserInterfaceStyle {
        switch theme {
        case .system: return .unspecified
        case .light: return .light
        case .dark: return .dark
        }
    }

    final class AppearanceController: UIViewController {
        var style: UIUserInterfaceStyle

        init(style: UIUserInterfaceStyle) {
            self.style = style
            super.init(nibName: nil, bundle: nil)
        }

        required init?(coder: NSCoder) { return nil }

        override func loadView() {
            let view = UIView()
            view.isUserInteractionEnabled = false
            view.backgroundColor = .clear
            self.view = view
        }

        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            applyStyle()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            applyStyle()
        }

        func applyStyle() {
            // UIKit propagates this override to the hosting controller's view hierarchy.
            // Unspecified inherits the system instead of retaining the previous forced mode.
            parent?.overrideUserInterfaceStyle = style
        }
    }
}
