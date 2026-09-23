import UIKit
import Shared

@main
final class CriosRangoIOSApp: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = CriosRangoSceneDelegate.self
        return configuration
    }
}

final class CriosRangoSceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = MainViewControllerKt.MainViewController()
        self.window = window
        window.makeKeyAndVisible()

        for context in connectionOptions.urlContexts {
            handlePaymentURL(context.url)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        for context in URLContexts {
            handlePaymentURL(context.url)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        MainViewControllerKt.handleIosPaymentForeground()
    }

    private func handlePaymentURL(_ url: URL) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }
        let scheme = components.scheme?.lowercased()
        let host = components.host?.lowercased()
        let path = components.path
        let custom = scheme == "criosrango" && host == "payment-return"
        let https = scheme == "https" &&
            (host == "criosrango.es" || host == "www.criosrango.es") &&
            path.hasPrefix("/app-payment-return")
        guard custom || https else { return }

        let result = components.queryItems?.first(where: { $0.name == "result" })?.value
        let orderId = components.queryItems?
            .first(where: { $0.name == "order_id" })?
            .value
            .flatMap(Int.init)
        MainViewControllerKt.handleIosPaymentReturn(result: result, orderId: orderId)
    }
}
