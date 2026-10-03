#!/usr/bin/env bash
# Rough p50/p95 latency for search and booking against a running, seeded instance (profiles: dev,seed).
# Usage: [PATIENT=seed-patient-N@seed.local] scripts/latency.sh [baseUrl]   (default http://localhost:8081)
# Patient and doctor offset are random per run so repeated runs do not collide on the same slots.
set -euo pipefail
BASE="${1:-http://localhost:8081}"
N_SEARCH=200
N_BOOK=64

token() { curl -s -XPOST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'; }

stats() { # reads seconds from stdin, prints n / p50 / p95 / max in ms
  sort -n | awk '{a[NR]=$1*1000} END {printf "n=%d  p50=%.0f ms  p95=%.0f ms  max=%.0f ms\n", NR, a[int(NR*0.50+0.5)], a[int(NR*0.95+0.5)], a[NR]}'
}

OFFSET="${OFFSET:-$((RANDOM % 900 + 1))}"
PT=$(token "${PATIENT:-seed-patient-$((RANDOM % 9000 + 2))@seed.local}" Passw0rd1)
QUERIES=(cardiolgy neurolgy Rahim Aisha pediatrcs Hossain dermatology Nusrat)

echo "== search doctors (typo-tolerant), $N_SEARCH requests"
for i in $(seq 1 $N_SEARCH); do
  q=${QUERIES[$((i % ${#QUERIES[@]}))]}
  curl -s -o /dev/null -w '%{time_total}\n' -H "Authorization: Bearer $PT" "$BASE/api/search/doctors?q=$q"
done | stats

echo "== book appointments, $N_BOOK requests (one patient, distinct slots)"
for i in $(seq 0 $((N_BOOK - 1))); do
  day=$(date -v+$((3 + i / 16))d +%Y-%m-%d 2>/dev/null || date -d "+$((3 + i / 16)) day" +%Y-%m-%d)
  hh=$(printf '%02d' $((9 + (i % 16) / 2))); mm=$(printf '%02d' $(( (i % 2) * 30 )))
  curl -s -o /tmp/medislot-last.json -w '%{http_code} %{time_total}\n' -XPOST "$BASE/api/appointments" \
    -H "Authorization: Bearer $PT" -H 'Content-Type: application/json' \
    -d "{\"doctorId\":$((OFFSET + i)),\"startTime\":\"${day}T${hh}:${mm}:00+06:00\",\"reason\":\"perf\"}" \
    | tee -a /tmp/medislot-book.txt | { read -r line; [ "${line%% *}" = 201 ] || echo "  non-201 at i=$i: $line $(cat /tmp/medislot-last.json)" >&2; echo "$line"; }
done > /tmp/medislot-book-$$.txt; mv /tmp/medislot-book-$$.txt /tmp/medislot-book.txt
echo "status codes: $(cut -d' ' -f1 /tmp/medislot-book.txt | sort | uniq -c | tr '\n' ' ')"
cut -d' ' -f2 /tmp/medislot-book.txt | stats
