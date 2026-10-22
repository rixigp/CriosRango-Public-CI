import Foundation
import UIKit
import UserNotifications
import Shared

final class PushNotificationBridge: NSObject {
    static let shared = PushNotificationBridge()
    private let endpoint = URL(string: "https://criosrango.es/wp-json/criosrango/v1/push/device")!
    private let defaults = UserDefaults.standard
    private let tokenKey = "criosrango_apns_token"
    private let pendingUnregisterKey = "criosrango_push_pending_unregister_token"
    private var refreshObserver: NSObjectProtocol?
    func configure() {
        if refreshObserver == nil {
            refreshObserver = NotificationCenter.default.addObserver(
                forName: Notification.Name("criosrango.push.refresh-registration"),
                object: nil,
                queue: .main
            ) { [weak self] _ in self?.refreshRegistration() }
        }
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let status: String
            switch settings.authorizationStatus {
            case .authorized: status = "AUTORIZADO"
            case .denied: status = "DENEGADO"
            case .notDetermined: status = "NO DETERMINADO"
            case .provisional: status = "AUTORIZADO"
            case .ephemeral: status = "AUTORIZADO"
            @unknown default: status = "NO DETERMINADO"
            }
            IosPushDiagnostics.shared.recordPermission(status: status)
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional || settings.authorizationStatus == .ephemeral {
                IosPushDiagnostics.shared.recordRegistrationRequested()
                DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
            } else if settings.authorizationStatus == .notDetermined {
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert,.sound,.badge]) { granted, error in
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
            }
            // Product preferences are persisted separately from OS permission; retry sync on resume.
            self.syncStoredRegistration()
        }
    }

    func didRegister(deviceToken: Data) {
        let token=deviceToken.map{String(format:"%02x",$0)}.joined()
        let previous = defaults.string(forKey: tokenKey)
        if let previous, previous != token { defaults.set(previous, forKey: pendingUnregisterKey) }
        defaults.set(token, forKey: tokenKey)
        IosPushDiagnostics.shared.recordToken(token: token)
        syncStoredRegistration()
    }

    private func syncStoredRegistration() {
        guard let token=defaults.string(forKey: tokenKey) else { return }
        var request=URLRequest(url:endpoint)
        request.httpMethod="POST"
        request.setValue("application/json",forHTTPHeaderField:"Content-Type")
        if let accountToken=IosAccountTokenStore().load() {
            request.setValue("Bearer \(accountToken)",forHTTPHeaderField:"Authorization")
        }
        let accountToken = IosAccountTokenStore().load()
        let newProducts = defaults.object(forKey:"criosrango_push_new_products") as? Bool ?? true
        let orderUpdates = defaults.object(forKey:"criosrango_push_order_updates") as? Bool ?? true
        IosPushDiagnostics.shared.recordPostAttempt(authenticated: accountToken != nil, newProducts: newProducts, orderUpdates: orderUpdates)
        request.httpBody=try? JSONSerialization.data(withJSONObject:[
            "platform":"ios",
            "identifier_type":"apns_token",
            "identifier":token,
            "new_products":newProducts,
            "order_updates":orderUpdates
        ])
        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error {
                IosPushDiagnostics.shared.recordPostResult(result: "ERROR \(String(describing: type(of: error)))", accepted: "NO", pending: true, errorClass: String(describing: type(of: error)), errorMessage: error.localizedDescription)
                return
            }
            guard let http = response as? HTTPURLResponse else {
                IosPushDiagnostics.shared.recordPostResult(result: "ERROR HTTP", accepted: "NO", pending: true, errorClass: "HTTPError", errorMessage: "No se recibió respuesta HTTP")
                return
            }
            let accepted = (200..<300).contains(http.statusCode)
            IosPushDiagnostics.shared.recordPostResult(result: "HTTP \(http.statusCode)", accepted: accepted ? "SÍ" : "NO", pending: !accepted, errorClass: "", errorMessage: "")
            guard accepted else { return }
            guard let pending = self.defaults.string(forKey: self.pendingUnregisterKey) else { return }
            // A token re-registered after logout is now associated with the current account (or anonymous user).
            if pending == token {
                self.defaults.removeObject(forKey: self.pendingUnregisterKey)
                return
            }
            var delete = URLRequest(url: self.endpoint)
            delete.httpMethod = "DELETE"
            delete.setValue("application/json", forHTTPHeaderField: "Content-Type")
            if let accountToken = IosAccountTokenStore().load() {
                delete.setValue("Bearer \(accountToken)", forHTTPHeaderField: "Authorization")
            }
            delete.httpBody = try? JSONSerialization.data(withJSONObject: ["platform": "ios", "token": pending])
            URLSession.shared.dataTask(with: delete) { _, deleteResponse, deleteError in
                guard deleteError == nil, let deleteHttp = deleteResponse as? HTTPURLResponse,
                      (200..<300).contains(deleteHttp.statusCode),
                      self.defaults.string(forKey: self.pendingUnregisterKey) == pending else { return }
                self.defaults.removeObject(forKey: self.pendingUnregisterKey)
            }.resume()
        }.resume()
    }
}