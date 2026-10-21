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
    func configure() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert,.sound,.badge]) { granted,_ in
            if granted { DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() } }
        }
    }
    func refreshRegistration() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                    self.syncStoredRegistration()
                }
            }
        }
    }

    func didRegister(deviceToken: Data) {
        let token=deviceToken.map{String(format:"%02x",$0)}.joined()
        let previous = defaults.string(forKey: tokenKey)
        if let previous, previous != token { defaults.set(previous, forKey: pendingUnregisterKey) }
        defaults.set(token, forKey: tokenKey)
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
        request.httpBody=try? JSONSerialization.data(withJSONObject:[
            "platform":"ios",
            "token":token,
            "new_products":defaults.object(forKey:"criosrango_push_new_products") as? Bool ?? true,
            "order_updates":defaults.object(forKey:"criosrango_push_order_updates") as? Bool ?? true
        ])
        URLSession.shared.dataTask(with: request) { _, response, error in
            guard error == nil, let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { return }
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