# Third-party components and provenance

- **Original RotProxy 1.2.0 for Minecraft 1.21.11** — supplied by the project owner. Java sources were recovered from that binary and adapted. MIT copyright notice retained in `LICENSE`.
- **Netty `netty-codec-socks` and `netty-handler-proxy` 4.2.15.Final** — Apache License 2.0. Embedded by Fabric Loom as nested libraries, with their upstream license notices. Other Netty components come from Minecraft's runtime.
- **Fabric Loader / Fabric API / Loom** — used from their official Maven repository; installed or resolved separately. See the respective [FabricMC repositories](https://github.com/FabricMC) for licenses.
- **Gradle wrapper 9.5.1** — Apache License 2.0. The wrapper bootstrap is committed; its distribution is downloaded and checksum-verified when used.
- **JUnit 5** — test-only dependency, not included in the mod.
- **CFR 0.152** — used locally for source recovery; not shipped or committed.
- **ROT PROXY artwork** — generated from the owner's rot-tools GitHub avatar style reference using the built-in image generation tool. See `docs/BRANDING.md`.

No Minecraft game JAR, Minecraft decompiled sources, third-party source recovery tools or private configuration is part of this source repository.
