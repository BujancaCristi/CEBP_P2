#!/usr/bin/env python3
"""
Demo-day load generator (owner: Cristi). Drives S1's public API with a few traders so that the
nightly report finds something: trends, a crash on one stock, bursts of buy-from-offer conflicts
(incidents) and heavy one-sided flow that pushes the market maker towards its inventory limit.

Standard library only. Usage:
    python load_day.py --s1 http://localhost:8081 --minutes 10 --traders 4
Traders are registered on the fly (trader-load-N / Load1234!). Ctrl-C stops it.
"""
import argparse
import json
import random
import threading
import time
import urllib.error
import urllib.request

ap = argparse.ArgumentParser()
ap.add_argument("--s1", default="http://localhost:8081")
ap.add_argument("--minutes", type=float, default=10)
ap.add_argument("--traders", type=int, default=4)
ap.add_argument("--rate", type=float, default=2.0, help="orders per second per trader")
ap.add_argument("--seed", type=int, default=None)
args = ap.parse_args()
rnd = random.Random(args.seed)


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(args.s1 + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt)
        except ValueError:
            return e.code, txt


def login(i):
    user = {"username": f"trader-load-{i}", "password": "Load1234!", "email": f"load{i}@example.com"}
    call("POST", "/api/auth/register", user)
    st, body = call("POST", "/api/auth/login", {"username": user["username"], "password": user["password"]})
    if st != 200:
        raise SystemExit(f"login failed for {user['username']}: {st} {body}")
    return body.get("token") or body.get("accessToken")


tokens = [login(i) for i in range(args.traders)]
st, stocks = call("GET", "/api/stocks", token=tokens[0])
if st != 200 or not stocks:
    raise SystemExit(f"no stocks: {st} {stocks}")
symbols = [s["symbol"] for s in stocks]
tick = {s["symbol"]: float(s.get("tickSize", 0.01)) for s in stocks}
last = {s["symbol"]: float(s.get("value") or s.get("lastPrice") or 10) for s in stocks}

# Scenario: one stock trends up, one crashes, one is noisy; the rest drift.
roles = {}
for i, sym in enumerate(symbols):
    roles[sym] = ["up", "crash", "noisy"][i] if i < 3 else "drift"
print("scenario:", roles)

stats = {"orders": 0, "rejected": 0, "buys": 0, "conflicts": 0, "errors": 0}
lock = threading.Lock()
stop = time.time() + args.minutes * 60


def bump(k):
    with lock:
        stats[k] += 1


def round_tick(sym, price):
    t = tick[sym]
    return round(round(price / t) * t, 4)


def drift(sym, elapsed_frac):
    role = roles[sym]
    if role == "up":
        return 1 + 0.004 + rnd.gauss(0, 0.004)
    if role == "crash":
        return (0.97 if 0.4 < elapsed_frac < 0.6 else 1) * (1 + rnd.gauss(0, 0.003))
    if role == "noisy":
        return 1 + rnd.gauss(0, 0.02)
    return 1 + rnd.gauss(0, 0.002)


def trader(idx, token):
    while time.time() < stop:
        frac = 1 - (stop - time.time()) / (args.minutes * 60)
        sym = rnd.choice(symbols)
        last[sym] = max(tick[sym], last[sym] * drift(sym, frac))
        price = round_tick(sym, last[sym] * (1 + rnd.gauss(0, 0.004)))
        # one-sided pressure on the trending stocks pushes the bot's inventory
        side = "BUY" if (roles[sym] == "up" and rnd.random() < 0.7) or (roles[sym] != "up" and rnd.random() < 0.5) else "SELL"
        if roles[sym] == "crash" and 0.4 < frac < 0.6:
            side = "SELL"
        qty = rnd.choice([10, 20, 50, 100])
        action = rnd.random()
        if action < 0.25:
            # buy-from-offer, deliberately with a stale version sometimes -> 409 incidents
            st, offers = call("GET", f"/api/offers?symbol={sym}", token=token)
            items = offers.get("items", offers) if isinstance(offers, dict) else offers
            if st == 200 and items:
                off = rnd.choice(items)
                seen = off["version"] - (1 if rnd.random() < 0.3 else 0)
                st, body = call("POST", f"/api/offers/{off['id']}/buy",
                                {"qty": min(qty, off["remainingQty"]), "maxPrice": off["price"], "seenVersion": seen}, token)
                bump("buys" if st == 200 else "conflicts" if st == 409 else "errors")
        else:
            otype = "MARKET" if rnd.random() < 0.15 else "LIMIT"
            body = {"symbol": sym, "side": side, "type": otype, "qty": qty}
            if otype == "LIMIT":
                body["price"] = price
            st, resp = call("POST", "/api/orders", body, token)
            if st == 202:
                bump("orders")
                if rnd.random() < 0.2 and isinstance(resp, dict) and "id" in resp:
                    call("DELETE", f"/api/orders/{resp['id']}", token=token)
            elif st == 422:
                bump("rejected")
            else:
                bump("errors")
        time.sleep(max(0.01, rnd.expovariate(args.rate)))


threads = [threading.Thread(target=trader, args=(i, t), daemon=True) for i, t in enumerate(tokens)]
for t in threads:
    t.start()
try:
    while any(t.is_alive() for t in threads):
        time.sleep(5)
        with lock:
            print(time.strftime("%H:%M:%S"), stats)
except KeyboardInterrupt:
    pass
print("done", stats)
