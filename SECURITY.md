# Security policy

Security fixes target the current 26.2 release.

Use GitHub's **Security → Report a vulnerability** for sensitive findings when private reporting is available. Do not place live proxy credentials, access tokens, private configuration or raw crash logs in a public issue. If private reporting is unavailable, open an issue asking the maintainer for a private reporting channel without including exploit details or secrets.

If a credential is accidentally published, revoke or rotate it immediately. Removing the file in a later commit does not remove it from Git history.

The kill switch covers the server TCP paths described in the README. It is not an operating-system firewall or a guarantee of anonymity. The preserved legacy credential encryption is not a replacement for secure secret storage.
