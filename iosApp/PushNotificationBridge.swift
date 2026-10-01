import Foundation
import UIKit
import UserNotifications
import Shared

final class PushNotificationBridge: NSObject {
    static let shared = PushNotificationBridge()
    private let endpoint = URL(string: "https://criosrango.es/wp-json/criosrango/v1/push/device")!
    private let defaults = UserDefaults.standard
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
        defaults.set(token,forKey:"criosrango_apns_token")
        syncStoredRegistration()
    }

    private func syncStoredRegistration() {
        guard let token=defaults.string(forKey:"criosrango_apns_token") else { return }
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
        URLSession.shared.dataTask(with:request).resume()
    }
}