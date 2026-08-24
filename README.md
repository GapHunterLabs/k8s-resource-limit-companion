# K8s Resource Limit Companion

Warning on any container entry in a Kubernetes workload manifest
(Deployment, Pod, StatefulSet, DaemonSet, Job, CronJob) with no
`resources:` block, or one missing `requests`/`limits`. Without
requests, the scheduler can't make good placement decisions; without
limits, a single container can consume unlimited CPU/memory and starve
its neighbors on the same node — a real, common, and documented
Kubernetes footgun.

## Why it exists

Kubernetes never requires `resources:` — a manifest with none is
perfectly valid YAML and deploys without a single warning, but it's a
well-documented anti-pattern with real production consequences. Nothing
in the IDE flags it today.

## Why built this way

- **100% static text analysis** — an indentation-based line scanner,
  not a real YAML parser, so it works whether the real Kubernetes/YAML
  plugin is installed or not.
- **Scoped to workload kinds that actually run containers** — a raw
  ConfigMap/Service/List manifest is never scanned, keeping false
  positives at zero for non-workload files.

## v0.1 scope — stated honestly, not exhaustively

Handles the common single-document, `containers:`/`- name: x` shape —
multi-doc files (`---` separators) and YAML anchors/aliases aren't
specially resolved.

## Usage

Open any `.yml`/`.yaml` file with a workload `kind:`. A container with
no resource declaration, or a partial one, shows a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
