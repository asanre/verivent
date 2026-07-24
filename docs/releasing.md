# Releasing

Snoop publishes to Maven Central under `io.github.asanre` via
[com.vanniktech.maven.publish](https://vanniktech.github.io/gradle-maven-publish-plugin/). The
`publish` workflow runs on every published GitHub Release; signing and Central credentials come from
repo secrets. Signing is key-driven, so `./gradlew publishToMavenLocal` runs unsigned for local
checks.

## One-time setup (manual, outside the repo)

1. **Central Portal account** — sign in at [central.sonatype.com](https://central.sonatype.com) with
   GitHub. The `io.github.asanre` namespace is verified automatically from that login.
2. **GPG signing key** — generate one and export it:
   ```bash
   gpg --gen-key
   gpg --armor --export-secret-keys <KEY_ID>   # the in-memory key value
   ```
3. **Generate a Central Portal user token** (Account → Generate User Token).
4. **Add four repository secrets** (Settings → Secrets and variables → Actions):
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — the Central Portal token pair.
   - `ORG_GRADLE_PROJECT_signingInMemoryKey` — the ASCII-armored secret key from step 2.
   - `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` — that key's passphrase.

## Cutting a release

1. Bump `VERSION_NAME` in `gradle.properties`, commit, tag.
2. Create a GitHub Release for the tag. The `publish` workflow runs
   `./gradlew publishAndReleaseToMavenCentral` on a macOS runner (so the iOS klibs are built and
   published) and releases the deployment on Central.

## Verifying locally

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/io/github/asanre/
```

Produces the six publishable artifacts: `snoop-core`, `snoop-ktor`, `snoop-analytics` and their
`-no-op` mirrors (each with its Android and iOS variants).
