# Privacy

RotProxy stores profiles only in the local Minecraft instance's `config/rotproxy_profiles.json` file. The file contains proxy hosts, ports, names and encrypted username/password fields. Encryption follows the original machine-derived format and does not protect against someone who has access to your computer. Do not publish this file or its backups.

On TEST, APPLY and every 15 seconds while a proxy is active, the mod requests `http://api.ipify.org/` through that proxy. The response supplies the displayed exit IP. There is no direct fallback, developer telemetry or developer-operated backend. The proxy and the IP-check service can observe the request; the HTTP response is not authenticated end to end.

The proxy receives the destination of proxied connections and, if configured, proxy credentials. SOCKS5 and HTTP proxy authentication can be visible on the network between your computer and proxy. HTTPS encrypts that leg using TLS with certificate and hostname validation.

The mod does not change Minecraft account authentication or the traffic of other applications. Local DNS, LAN discovery, resource-pack downloads, Realms APIs and networking implemented independently by other mods are outside RotProxy's scope.

Error diagnostics redact configured usernames and passwords, but Minecraft and other components may still log connection addresses or unrelated sensitive information. Review and sanitize reports before sharing. The repository ignores game directories, profiles, credentials, logs, certificates, dumps and local build/recovery material.
