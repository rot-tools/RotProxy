# Changelog

## 1.2.1+mc26.2

- Port to Minecraft 26.2, Java 25, unobfuscated Mojang class names and the 26.2 GUI extraction API.
- Update bundled Netty SOCKS/proxy modules to 4.2.15.Final and use Minecraft's own HTTP codec.
- Add the ROT PROXY icon and a compact black, crimson and violet interface that fits the default 427 × 240 GUI.
- Preserve profile editing through window resizing and ignore outdated draft-test callbacks.
- Use the same tunnel implementation for probes and game connections; support TLS and certificate validation for the HTTPS proxy type.
- Fail closed at the network pipeline, disable direct legacy ping fallback, reject stale connection attempts and ignore obsolete probe results.
- Close tracked multiplayer sockets on heartbeat failure, profile changes and disable.
- Close probe sockets after success, error or timeout; shut down workers when Minecraft stops.
- Require a valid IP response before reporting a healthy proxy.
- Preserve the existing configuration format, avoid plaintext fallback if credential encryption fails, save through a temporary file and handle invalid profile indexes.
- Add local network integration tests, Minecraft client game tests and public-repository checks.
