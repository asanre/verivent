# Releasing

Snoop publishes to Maven Central under `io.github.asanre` via
[com.vanniktech.maven.publish](https://vanniktech.github.io/gradle-maven-publish-plugin/). The
`publish` workflow runs on every published GitHub Release; signing and Central credentials come from
repo secrets. Signing is key-driven, so `./gradlew publishToMavenLocal` runs unsigned for local
checks.

## One-time setup (manual, outside the repo)

1. **Central Portal account** — sign in at [central.sonatype.com](https://central.sonatype.com) with
   GitHub. The `io.github.asanre` namespace is verified automatically from that login.
2. **GPG signing key** — generate an RSA 4096 key that never expires:
   ```bash
   gpg --full-generate-key
   ```
   Use `--full-generate-key`, not `--gen-key`: the short form defaults to ed25519 on GnuPG 2.5+, and
   Central's signature validation is only reliably happy with RSA. Note the key ID from
   `gpg --list-secret-keys --keyid-format=long`.
3. **Distribute the public key** — Central verifies signatures by fetching the public half from a
   keyserver, so an unpublished key fails validation:
   ```bash
   gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <KEY_ID>
   ```
   If this fails with `No route to host`, the keyserver's AAAA records are unreachable from your
   network; force IPv4 with `echo disable-ipv6 >> ~/.gnupg/dirmngr.conf && gpgconf --kill dirmngr`
   and retry. Propagation takes a few minutes — confirm with `--recv-keys` before releasing.
4. **Generate a Central Portal user token** (Account → Generate User Token).
5. **Add four repository secrets** (Settings → Secrets and variables → Actions):
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — the Central Portal token pair. The
     username is the token's own short identifier, not the account email.
   - `ORG_GRADLE_PROJECT_signingInMemoryKey` — the ASCII-armored secret key, piped straight in so it
     never touches disk: `gpg --armor --export-secret-keys <KEY_ID> | gh secret set ORG_GRADLE_PROJECT_signingInMemoryKey`
   - `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` — that key's passphrase.

## Cutting a release

1. Set `VERSION_NAME` in `gradle.properties` to the version being cut, and commit. The first release
   needs no bump — the property already holds the initial version.
2. Merge to `main` and push. This also triggers the `docs` workflow, which only deploys the site on
   pushes to `main`.
   ```bash
   git checkout main && git merge <branch> && git push
   ```
3. Tag the release commit and push the tag:
   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```
4. Create the GitHub Release for that tag, which triggers `publish`:
   ```bash
   gh release create v0.1.0 --generate-notes
   ```
   The workflow runs `./gradlew publishToMavenCentral` on a macOS runner, so the iOS klibs are built
   and published alongside the Android ones.
5. Release the deployment from [central.sonatype.com](https://central.sonatype.com) → Deployments,
   once it reaches *VALIDATED*. The workflow deliberately stops short of releasing so the upload can
   be inspected first; the job can go green before Central finishes validating, so the portal is the
   source of truth. Once every release is routine, switching the workflow back to
   `publishAndReleaseToMavenCentral` collapses this step into the previous one.

**Released versions are permanent.** A version released to Central cannot be deleted, replaced or
re-uploaded. If a release fails after the artifacts are uploaded, the fix is to bump to the next
patch — never to retry the same version.

## Verifying locally

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/io/github/asanre/
```

Six modules are published: `snoop-core`, `snoop-ktor`, `snoop-analytics` and their `-no-op` mirrors.
Those are the six coordinates a consumer declares, but the `ls` lists **24** directories, because
each Kotlin Multiplatform module publishes a root coordinate plus one per target:

```
snoop-core/                     root: POM, Gradle module metadata, common klib
snoop-core-android/
snoop-core-iosarm64/
snoop-core-iossimulatorarm64/
```

Nobody depends on the per-target coordinates directly. Declaring `io.github.asanre:snoop-core` is
enough — the root's `.module` metadata resolves the right variant for whichever target is compiling.
