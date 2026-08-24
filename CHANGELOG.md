<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# K8s Resource Limit Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Warning on any container in a Kubernetes workload manifest with no
  `resources:` block, or one missing `requests`/`limits`.
- 100% static text analysis, no network calls, no telemetry. Free.

[Unreleased]: https://github.com/GapHunterLabs/k8s-resource-limit-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/k8s-resource-limit-companion/commits/0.1.0
