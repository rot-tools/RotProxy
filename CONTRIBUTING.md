# Contributing

Use JDK 25 and the checked-in Gradle wrapper. Run `./gradlew build` and, for client/UI/network hook changes, `./gradlew runClientGameTest`.

Use loopback fixtures and documentation-only addresses in tests. Never add real proxy credentials, personal Minecraft directories, logs, private keys or account tokens. Run `python scripts/check-public-files.py` before a pull request. Do not disable required network mixins to get a version update to load; adjust the hook and test the blocked and protected paths.

Keep the original MIT notice. Explain user-visible changes, the tested Minecraft version and any limits in the pull request.
