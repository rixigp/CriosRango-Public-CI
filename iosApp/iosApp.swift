import UIKit
import FirebaseCore
import Shared
import UserNotifications

private func parsePushOrderId(_ rawValue: Any?) -> Int32 {
    if let value = rawValue as? String,
       let orderId = Int32(value.trimmingCharacters(in: .whitespacesAndNewlines)),
       orderId > 0 {
        return orderId
    }
    if let number = rawValue as? NSNumber {
        let value = number.doubleValue
        guard value.isFinite,
              value > 0,
              value.rounded(.towardZero) == value,
              value <= Double(Int32.max) else {
            return 0
        }
        return number.int32Value
    }
    return 0
}

@main
final class CriosRangoIOSApp: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        UNUserNotificationCenter.current().delegate = self
        PushNotificationBridge.shared.configure()
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions { [.banner,.sound,.badge] }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info=response.notification.request.content.userInfo
        let type = info["type"] as? String
        let orderId = parsePushOrderId(info["order_id"])
        MainViewControllerKt.handleIosPushNotification(type: type, orderId: orderId)
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) { PushNotificationBridge.shared.didRegister(deviceToken: deviceToken) }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        IosPushDiagnostics.shared.recordRegistrationFailure(
            errorClass: String(describing: type(of: error)),
            message: error.localizedDescription
        )
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

        for context in connectionOptions.urlContexts { handlePaymentURL(context.url) }
        if let response=connectionOptions.notificationResponse {
            let info=response.notification.request.content.userInfo
            let type = info["type"] as? String
            let orderId = parsePushOrderId(info["order_id"])
            MainViewControllerKt.handleIosPushNotification(type: type, orderId: orderId)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        for context in URLContexts {
            handlePaymentURL(context.url)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        PushNotificationBridge.shared.refreshRegistration()
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
            .map { KotlinInt(value: Int32($0)) }
        MainViewControllerKt.handleIosPaymentReturn(result: result, orderId: orderId)
    }
}
