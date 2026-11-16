#!/usr/bin/env bash
# Rebuild the insights database from Kafka offset 0.
# Usage (docker compose): ./scripts/replay.sh            (stops s2-insights, resets, starts it again)
# Env: KAFKA_CONTAINER (default kafka), PG_CONTAINER (default postgres-insights), S2_CONTAINER (default s2-insights)
set -euo pipefail
KAFKA_CONTAINER=${KAFKA_CONTAINER:-kafka}
PG_CONTAINER=${PG_CONTAINER:-postgres-insights}
S2_CONTAINER=${S2_CONTAINER:-s2-insights}
DB_USER=${S2_DB_USER:-insights}
DB_NAME=${S2_DB_NAME:-insights}

echo "1/4 stopping $S2_CONTAINER (the consumer group must be inactive to reset offsets)"
docker stop "$S2_CONTAINER" >/dev/null

echo "2/4 truncating the read model"
docker exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c \
  "TRUNCATE processed_events, trades_view, daily_stock_stats, bot_daily_stats, incidents_view, bot_configs_view;"

echo "3/4 resetting consumer group s2-insights to the earliest offset"
docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group s2-insights --reset-offsets --to-earliest --all-topics --execute

echo "4/4 starting $S2_CONTAINER"
docker start "$S2_CONTAINER" >/dev/null
echo "done - watch GET /api/stats/daily converge to the same numbers as before"
