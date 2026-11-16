# Demo-day load generator (owner: Cristi)

Generates a busy, volatile trading day through S1's public API so the nightly report has findings:

* one stock trends up with one-sided buying (pushes the market maker towards `max_inventory`),
* one stock crashes in the middle of the run (a > 5 % move),
* one stock is noisy, the others drift,
* a quarter of the actions are buy-from-offer, 30 % of them with a stale `seenVersion`, which
  produces `409` conflicts and `CONCURRENCY_CONFLICT` incidents,
* 15 % market orders and 20 % cancels, so order counters are non-trivial.

```bash
python infra/load/load_day.py --s1 http://localhost:8081 --minutes 10 --traders 4
```

Then `POST /api/agent/run?date=<today>` on S2 (or Admin > Reports in the web app) and open Mailpit.
Tune the prompt in `s2-insights/.../agent/ReportAgent.java` until the recommendations are sensible.
