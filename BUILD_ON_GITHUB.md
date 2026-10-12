# Building Qubeauth into a JAR

This workspace cannot produce a verified JAR locally: it has Java 21, no Gradle installation, and outbound network/DNS is unavailable. Minecraft 26.3 needs Java 25 and Gradle must download Minecraft/Fabric dependencies.

The `.github/workflows/build.yml` workflow is included to run the build on a GitHub-hosted runner with Java 25 and Gradle 9.3.1.

## Steps
1. Create a new repository on GitHub (for example, `Qubeauth`).
2. Extract this ZIP and upload the contents of the `Qubeauth` folder to the repository's root. Make sure `.github/workflows/build.yml` is included.
3. Open the repository's **Actions** tab and select **Build Qubeauth**. Click **Run workflow** (or push a commit to `main`/`master`).
4. If the build succeeds, open the completed workflow run and download the `qubeauth-build` artifact. The JAR will be inside that downloaded artifact.
5. If the build fails, do not install anything on the live server; the source needs fixes based on the build log.

## Important
This is an unverified starter source project, not a release. A successful compile alone does not prove authentication is secure or all Minecraft 26.3 APIs/behaviors work correctly. Test on a separate server with a backup before using it on your main server.
