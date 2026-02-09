# Releasing Francis

This document describes how to create a new release of Francis.

## Prerequisites

Before starting a release, ensure you have:

1. **Clean git working directory** - All changes must be committed, must be on `main` branch
2. **GitHub CLI authenticated** - Install: `brew install gh`, Login: `gh auth login`
3. **Appropriate permissions** - Push access to this repo and `block/homebrew-tap`

## Release Process

The release script automates the entire release process. Run:

```bash
./release.sh
```

### What the Script Does

The script will:

1. **Validate and confirm** the release version
   - Shows current version (e.g., `0.0.7-SNAPSHOT`)
   - Shows release version (e.g., `0.0.7`)
   - Shows post-release version (e.g., `0.0.8-SNAPSHOT`)
   - Asks for confirmation before proceeding

2. **Create release branch** (`release/X.Y.Z`)
   - Updates `gradle.properties` to remove `-SNAPSHOT`
   - Commits and pushes the branch

3. **Wait for GitHub Actions build**
   - Monitors the `build` workflow for the release branch
   - Downloads the built artifacts when complete

4. **Extract artifacts**
   - Extracts the release tarball

5. **Tag the release** (`vX.Y.Z`)
   - Creates an annotated git tag
   - Pushes the tag to GitHub

6. **Wait for GitHub Actions release workflow**
   - Publishes to Maven Central (host-sdk)
   - Creates GitHub release with tarball
   - Prints verification URLs:
     - GitHub Release: `https://github.com/squareup/francis/releases/tag/vX.Y.Z`
     - Maven Central: `https://central.sonatype.com/artifact/com.squareup.francis/host-sdk/X.Y.Z`
   - Note: Maven Central artifacts may take up to 30 minutes to become available

7. **Merge release branch to main**
   - Fast-forward merge only

8. **Bump version for next development cycle**
   - Updates `gradle.properties` to `X.Y.Z+1-SNAPSHOT`
   - Commits and pushes to main

9. **Update Homebrew formula**
   - Triggers `update-francis.yaml` workflow in `block/homebrew-tap`
   - Waits for the workflow to complete

## Resuming Failed Releases

The release script supports automatic resumption if something fails partway through. The script tracks progress in `releases/X.Y.Z/.release_state`.

### How Resumption Works

Each major step in the release process is tracked:
- `PROMPT` - Initial confirmation
- `CREATE_BRANCH` - Release branch creation
- `WAIT_BUILD` - GitHub Actions build
- `EXTRACT` - Artifact extraction
- `TAG_RELEASE` - Git tag creation
- `WAIT_RELEASE` - GitHub Actions release
- `MERGE_MAIN` - Merge to main
- `BUMP_SNAPSHOT` - Version bump
- `TRIGGER_FORMULA_BUMP` - Homebrew formula update

If the script fails or is interrupted:
1. Fix the underlying issue
2. Run `./release.sh` again
3. The script will skip already-completed steps

### To Restart from Scratch

```bash
# Delete artifacts directory
rm -r releases/X.Y.Z

# Delete release branch if created
git checkout main && git branch -D release/X.Y.Z && git push origin --delete release/X.Y.Z

# Delete tag if created
git tag -d vX.Y.Z && git push origin --delete vX.Y.Z
```

## Version Numbering

Francis uses semantic versioning (MAJOR.MINOR.PATCH):
- Development versions end with `-SNAPSHOT` (e.g., `0.0.7-SNAPSHOT`)
- Release versions have no suffix (e.g., `0.0.7`)

## Release Artifacts

Each release produces:

1. **GitHub Release** (`https://github.com/squareup/francis/releases/tag/vX.Y.Z`)
   - `francis-release.tar.gz` - Complete release bundle with:
     - JAR files (francis.jar, francis-demo.jar, francis-host-sdk.jar)
     - Demo APKs
     - Wrapper scripts

2. **Maven Central Artifact**
   - `com.squareup.francis:host-sdk:X.Y.Z` - Host SDK JAR

3. **Homebrew Formula**
   - Updated in `block/homebrew-tap` repository
   - Users can install with: `brew install block/tap/francis`

## Repository Secrets Required

The following secrets must be configured in the repository settings:

- `SONATYPE_CENTRAL_USERNAME` - Sonatype Central Portal username
- `SONATYPE_CENTRAL_PASSWORD` - Sonatype Central Portal password
- `GPG_SECRET_KEY` - GPG private key for signing
- `GPG_SECRET_PASSPHRASE` - GPG key passphrase

## Troubleshooting

### Build workflow doesn't start

GitHub Actions may be delayed. Wait a few minutes and the script will find it.

### Release workflow fails

Check the workflow logs: `gh run view --repo squareup/francis`

Common issues:
- Maven Central credentials not configured (repository secrets)
- GPG signing key issues (repository secrets)

### Homebrew update times out

Check the workflow manually: `https://github.com/block/homebrew-tap/actions`

You can manually trigger it:
```bash
gh workflow run update-francis.yaml --repo block/homebrew-tap --field tag=vX.Y.Z
```
