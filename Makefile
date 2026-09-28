.DEFAULT_GOAL := pagerduty-plugin

SHELL               := /bin/bash -o nounset -o pipefail -o errexit
VERSION             ?= $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
GIT_BRANCH          := $(shell git branch --show-current)
GIT_SHORT_HASH      := $(shell git rev-parse --short HEAD)
DATE                := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ") # Date format RFC3339
JAVA_MAJOR_VERSION  := 17

ARTIFACTS_DIR       := ./target/artifacts
RELEASE_VERSION     := UNSET.0.0
RELEASE_BRANCH      := master
MAJOR_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f1)
MINOR_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f2)
PATCH_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f3)
SNAPSHOT_VERSION    := $(MAJOR_VERSION).$(MINOR_VERSION).$(shell expr $(PATCH_VERSION) + 1)-SNAPSHOT
RELEASE_LOG         := $(ARTIFACTS_DIR)/release.log
ANTORA_YML          := ./docs/antora.yml
OK                  := "[ 👍 ]"

.PHONY help:
help:
	@echo ""
	@echo "Build PagerDuty Plugin from source"
	@echo "Goals:"
	@echo "  help:              Show this help with explaining the build goals"
	@echo "  pagerduty-plugin:  Compile, assemble and run test suite"
	@echo "  clean:             Clean the build artifacts"
	@echo "  release:           Create a release in the local repository, e.g. make release RELEASE_VERSION=x.y.z"
	@echo ""

.PHONY deps-build:
deps-build:
	@echo -n "👮‍♀️ Create artifact directory:   "
	@mkdir -p $(ARTIFACTS_DIR)
	@echo $(OK)
	@echo -n "👮‍♀️ Check Java runtime:          "
	@command -v java > /dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Check Java compiler:         "
	@command -v javac > /dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Check Maven binary:          "
	@command -v mvn > /dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Check Java version $(JAVA_MAJOR_VERSION):       "
	@java --version | grep '$(JAVA_MAJOR_VERSION)\.[[:digit:]]*\.[[:digit:]]*' >/dev/null
	@echo $(OK)
	@echo -n "👮‍♀️ Validate Maven project:      "
	@mvn validate > /dev/null
	@echo $(OK)

.PHONY pagerduty-plugin:
pagerduty-plugin: deps-build
	mvn install

.PHONY clean:
clean: deps-build
	mvn clean

.PHONY collect-artifacts:
collect-artifacts:
	find . -type f -regex ".*\/assembly\/kar\/target\/opennms-pagerduty-plugin-.*\.kar" -exec cp {} $(ARTIFACTS_DIR) \;
	echo $(VERSION) > $(ARTIFACTS_DIR)/pom-version.txt
	shasum -a 256 -b $(ARTIFACTS_DIR)/opennms-pagerduty-plugin-*.kar > $(ARTIFACTS_DIR)/shasum256.txt
	cd $(ARTIFACTS_DIR); tar czf opennms-pagerduty-plugin.tar.gz opennms-pagerduty-plugin-*.kar shasum256.txt
	shasum -a 256 -b $(ARTIFACTS_DIR)/opennms-pagerduty-plugin.tar.gz > $(ARTIFACTS_DIR)/opennms-pagerduty-plugin.sha256

.PHONY: release
release: deps-build
	@mkdir -p target
	@echo ""
	@echo "Release version:                $(RELEASE_VERSION)"
	@echo "New snapshot version:           $(SNAPSHOT_VERSION)"
	@echo "Git version tag:                v$(RELEASE_VERSION)"
	@echo "Current branch:                 $(GIT_BRANCH)"
	@echo "Release branch:                 $(RELEASE_BRANCH)"
	@echo "Release log file:               $(RELEASE_LOG)"
	@echo ""
	@echo -n "👮‍♀️ Check release branch:        "
	@if [ "$(GIT_BRANCH)" != "$(RELEASE_BRANCH)" ]; then echo "Releases are made from the $(RELEASE_BRANCH) branch, your branch is $(GIT_BRANCH)."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check uncommited changes     "
	@if git status --porcelain | grep -q .; then echo "There are uncommited changes in your repository."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check branch in sync         "
	@if [ "$(git rev-parse HEAD)" != "$(git rev-parse @{u})" ]; then echo "$(RELEASE_BRANCH) branch not in sync with remote origin."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check release version:       "
	@if [ "$(RELEASE_VERSION)" = "UNSET.0.0" ]; then echo "Set a release version, e.g. make release RELEASE_VERSION=1.0.0"; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮‍♀️ Check version tag available: "
	@if git rev-parse v$(RELEASE_VERSION) >$(RELEASE_LOG) 2>&1; then echo "Tag v$(RELEASE_VERSION) already exists"; exit 1; fi
	@echo "$(OK)"
	@echo -n "💅 Set Maven release version:   "
	@mvn versions:set -DnewVersion=$(RELEASE_VERSION) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "📖 Set Antora release version:  "
	@sed -i.bak -E "s/^version: .*/version: '$(RELEASE_VERSION)'/; s/^( *full-display-version:).*/\1 '$(RELEASE_VERSION)'/; s/^( *release-version:).*/\1 '$(RELEASE_VERSION)'/" $(ANTORA_YML) && rm $(ANTORA_YML).bak
	@echo "$(OK)"
	@echo -n "👮‍♀️ Validate build:              "
	@$(MAKE) pagerduty-plugin >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🎁 Git commit new release       "
	@git commit --signoff -am "release: PagerDuty Plugin version $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🦄 Set Git version tag:         "
	@git tag -a "v$(RELEASE_VERSION)" -m "Release PagerDuty Plugin version $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "⬆️ Set Maven snapshot version:  "
	@mvn versions:set -DnewVersion=$(SNAPSHOT_VERSION) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "📖 Set Antora snapshot version: "
	@sed -i.bak -E "s/^version: .*/version: '$(SNAPSHOT_VERSION)'/; s/^( *full-display-version:).*/\1 '$(SNAPSHOT_VERSION)'/" $(ANTORA_YML) && rm $(ANTORA_YML).bak
	@echo "$(OK)"
	@echo -n "🎁 Git commit snapshot release: "
	@git commit --signoff -am "release: PagerDuty Plugin version $(SNAPSHOT_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo ""
	@echo "🦄 Congratulations! ✨"
	@echo "You made a release in your local repository."
	@echo "Publish the release by pushing the version tag"
	@echo "and the new snapshot version to the remote repo"
	@echo "with the following commands:"
	@echo ""
	@echo "  git push"
	@echo "  git push origin v$(RELEASE_VERSION)"
	@echo ""
	@echo "Thank you for computing with us."
	@echo ""

deps-docs:
	@command -v antora

docs: deps-docs
	@echo "Build Antora docs..."
	antora --stacktrace local-site.yml
