# How can I create and publish a release

Releases are created from the main branch and driven by git version tags.

## Before you begin

* Switch to the main branch
* Ensure the main branch in your local repository doesn's has any uncommited changes and is in a clean state

## Create the release

The `release` goal in the Makefile can be used to set version numbers in the repository accordingly.
The versioning follows the semver.org pattern.

> [!NOTE]
> Replace x.y.z with you version number, e.g. 1.2.3.

You can create a release with the following command from the root directory of the repository:

```shell
make release RELEASE_VERSION=x.y.z
```

The release goal does the following things for you:

1. Sets the version number as you described inthe RELEASE_VERSION variable
2. Creates a git version tag prefixed with `v`
3. Sets a new SNAPSHOT version for the next development iteration

All changes are in your local repository and nothing is pushed or published immediately.

## Publish a release

You can push the version changes in the main branch to the GitHub repository with:

```shell
git push
```

GitHub actions will create a GitHub release and publish artifacts by pushing the git tag to the remote repository with:

```shell
git push origin vx.y.z
```
