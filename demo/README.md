# Demo data for screenshots

`deployment.yaml` — `orders-api` has no `resources:` block (flagged),
`orders-worker` has both `requests` and `limits` (not flagged).

## How to get the screenshot

1. `./gradlew runIde` from `k8s-resource-limit-companion`, open this
   `demo/` folder as the project.
2. Full Screen, open `deployment.yaml` — a warning should appear on
   `orders-api`'s container name line but not on `orders-worker`'s.
3. Screenshot with both containers visible, save into
   `k8s-resource-limit-companion/docs/screenshots/`. Close the sandbox.
