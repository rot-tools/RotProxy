<p align="center"><img src="src/main/resources/assets/rotproxy/icon.png" width="180" alt="ROT PROXY"></p>

# RotProxy

Client-side Fabric proxy mod by **rot-tools / OgRudolf**, ported to **Minecraft Java 26.2**.

SOCKS5, HTTP CONNECT and HTTPS proxy tunnels, saved profiles, a multiplayer shortcut, and a fail-closed kill switch with a health check every 15 seconds. The interface uses the black, crimson and violet rot-tools identity.

## Download and install

1. Install **Minecraft Java 26.2**, **Java 25** and **Fabric Loader 0.19.5 or newer**.
2. Install [Fabric API for 26.2](https://modrinth.com/mod/fabric-api/versions?g=26.2).
3. Download `rotproxy-1.2.2+mc26.2.jar` from [Modrinth](https://modrinth.com/mod/rotproxy) or [GitHub Releases](https://github.com/rot-tools/RotProxy/releases/latest) and place it in your instance's `mods` folder. Remove the old RotProxy JAR from that instance.
4. Open **Multiplayer → RotProxy: Blocked**. Enter your proxy host, port, protocol and optional credentials.
5. Use **TEST** to check a draft, then **APPLY** to activate it. Wait for **PROTECTED / CONNECTED** before joining.

`TEST` does not activate a profile. **DISABLE deliberately blocks multiplayer**; it does not switch to your direct connection. Singleplayer remains available.

| Component | Build version |
| --- | --- |
| Minecraft | 26.2 (exact target) |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| Fabric Loom | 1.17.20 |
| Gradle | 9.5.1 |
| Netty proxy modules | 4.2.15.Final, matching Minecraft |

## Profiles and controls

Use **< / >** to switch the profile being edited. **NEW**, **CLEAR**, **DELETE** and **RESET ALL** manage saved profiles. **APPLY** selects the active profile; merely browsing profiles leaves the active connection unchanged. Editing survives a window resize. Passwords are masked.

The status area shows the active profile, exit IP, probe latency and latest result. Hover over a shortened diagnostic to read it in full. The latency is the proxy health-check round trip, not the game server's ping.

## Connection behavior

- Multiplayer joins and server-list TCP pings are blocked until the active proxy passes its check.
- The health check requests `http://api.ipify.org/` **through the selected proxy**. It never retries directly. A successful HTTP response must contain a valid IP address.
- SOCKS5 supports optional username/password authentication. HTTP uses CONNECT, including for the probe. HTTPS uses CONNECT inside a TLS connection to the proxy and validates the certificate and hostname. A provider that calls ordinary CONNECT service an “HTTPS proxy” may actually require the **HTTP** option; select according to its transport documentation.
- Changing or disabling the active proxy closes existing tracked multiplayer connections. A failed periodic check also closes them and blocks new connections. Checks continue so the proxy can recover; reconnect to the game server manually after recovery.
- The vanilla legacy server-ping fallback is disabled because it would otherwise create a separate direct socket.

RotProxy is **not a system VPN**. It covers vanilla Minecraft server TCP connections and server-list pings. Minecraft authentication, Realms HTTP APIs, resource-pack downloads, LAN discovery, other mods' network requests and local DNS resolution are outside that coverage. Server names can be resolved locally by Minecraft before its connection reaches the proxy. The IP check uses HTTP, so the proxy can see and modify that response. A healthy probe does not guarantee that a particular server or port is reachable.

## Configuration and privacy

Profiles are stored locally in `config/rotproxy_profiles.json`, using the original mod's credential format for compatibility with 1.2.0. That legacy encryption is machine-derived and is **not a password vault**. Anyone with access to the same machine and files may be able to recover the credentials. Never upload the configuration, game directory, raw logs or crash reports to this repository. See [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md).

Existing profiles can be copied into a separate 26.2 instance on the same machine. Keep a private backup first. If the old credentials cannot be decrypted, enter them again. Do not share the backup.

## Build and test

With JDK 25 selected as `JAVA_HOME`:

```sh
./gradlew build
./gradlew runClientGameTest
```

On Windows use `gradlew.bat`. Outputs are in `build/libs/`. Install the ordinary JAR, not the `-sources.jar`.

Unit tests perform actual loopback SOCKS5, HTTP CONNECT and TLS handshakes without real credentials or external proxy accounts. Client game tests start Minecraft 26.2 and exercise the UI, connection mixins, local/remote connection separation, a proxy tunnel and the heartbeat kill switch. Client tests require a graphics-capable environment; on Linux CI they run under Xvfb.

Use an ASCII-only Gradle cache path if Java on Windows cannot load `GradleWorkerMain` from a cache path containing accented characters:

```sh
./gradlew --gradle-user-home C:/GradleCache build
```

See [docs/VALIDATION.md](docs/VALIDATION.md) for the tested scope and [CHANGELOG.md](CHANGELOG.md) for changes. CI builds and tests every push and pull request. Runtime settings and local recovery tools are excluded from Git.

## Source and license

This source tree was recovered from the owner's `rotproxy-1.2.0+mc1.21.11.jar` and adapted to 26.2. Original comments and build history could not be recovered. The original MIT license is retained. Minecraft, Fabric API, development tools and the original binary are not committed. See [THIRD_PARTY.md](THIRD_PARTY.md).

This 26.2 build continues the existing [RotProxy Modrinth project](https://modrinth.com/mod/rotproxy). Source and releases live at [rot-tools/RotProxy](https://github.com/rot-tools/RotProxy).

- [Modrinth](https://modrinth.com/mod/rotproxy)
- [GitHub](https://github.com/rot-tools/RotProxy)

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
