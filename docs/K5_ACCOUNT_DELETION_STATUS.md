# K.5 — Account deletion status

Status: **APP STORE BLOCKER PENDIENTE**

The current application has no user-authenticated account-deletion endpoint/flow.

K.5 deliberately does **not** implement account deletion through:
- WooCommerce administrative `wc/v3/customers` APIs;
- embedded consumer/admin credentials;
- WordPress `wp/v2/users` operations requiring administrative privileges.

No deletion UI or backend flow is added in K.5. A dedicated secure endpoint authenticated as the current user must be provided and audited before publication.

This item remains a separate block after K.5.
