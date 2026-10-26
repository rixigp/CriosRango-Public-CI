import Foundation
import UIKit
import UserNotifications
import Shared

/// Native APNs/notification bridge only. All HTTP is owned by Shared PushDeviceRegistration.
final class PushNotificationBridge: NSObject {
    static let shared = PushNotificationBridge()
    private let defaults = UserDefaults.standard
    private let tokenKey = "criosrango_apns_token"
    private let pendingUnregisterKey = "criosrango_push_pending_unregister_token"
    private var refreshObserver: NSObjectProtocol?

    func configure() {
        if refreshObserver == nil {
            refreshObserver = NotificationCenter.default.addObserver(
                forName: Notification.Name("criosrango.push.refresh-registration"),
                object: nil, queue: .main
            ) { [weak self] _ in self?.refreshRegistration() }
        }
        requestOrRegisterForNotifications()
    }

    private func requestOrRegisterForNotifications() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let status: String
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral: status = "AUTORIZADO"
            case .denied: status = "DENEGADO"
            case .notDetermined: status = "NO DETERMINADO"
            @unknown default: status = "NO DETERMINADO"
            }
            IosPushDiagnostics.shared.recordPermission(status: status)
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional || settings.authorizationStatus == .ephemeral {
                IosPushDiagnostics.shared.recordRegistrationRequested()
                DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
            } else if settings.authorizationStatus == .notDetermined {
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
                    IosPushDiagnostics.shared.recordPermission(status: granted ? "AUTORIZADO" : "DENEGADO")
                    if let error {
                        IosPushDiagnostics.shared.recordRegistrationFailure(errorClass: String(describing: type(of: error)), message: error.localizedDescription)
                    }
                    if granted {
                        IosPushDiagnostics.shared.recordRegistrationRequested()
                        DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
                    }
                }
            }
        }
    }

    func refreshRegistration() {
        requestOrRegisterForNotifications()
        // A lifecycle trigger enters the same app-scoped Kotlin coordinator.
        IosPushSyncBridgeKt.requestIosPushSync()
    }

    func didRegister(deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        let previous = defaults.string(forKey: tokenKey)
        if let previous, previous != token { defaults.set(previous, forKey: pendingUnregisterKey) }
        let changed = previous != token
        defaults.set(token, forKey: tokenKey)
        IosPushDiagnostics.shared.recordToken(token: token)
        if changed {
            IosPushSyncBridgeKt.requestIosPushTokenSync()
        } else {
            IosPushSyncBridgeKt.requestIosPushSync()
        }
    }
}
