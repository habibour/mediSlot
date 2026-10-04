#!/usr/bin/env bash
# 20 patients try to book the same doctor and slot at the same instant; exactly one must win.
# Usage: scripts/race-demo.sh [baseUrl]    default http://localhost:8081
set -uo pipefail
BASE="${1:-http://localhost:8081}"; N=20; RUN=$(date +%s); PW='Passw0rd1'
json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }
login() { curl -s -XPOST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"$2\"}" | json "['token']"; }

AT=$(login admin@medislot.local 'Admin@12345')
DID=$(curl -s -XPOST "$BASE/api/doctors" -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"email\":\"race-doc-$RUN@demo.test\",\"password\":\"$PW\",\"profile\":{\"fullName\":\"Dr Race\",\"specialty\":\"Cardiology\",\"workingStart\":\"09:00\",\"workingEnd\":\"17:00\",\"slotMinutes\":30}}" | json "['id']")
SLOT=$(python3 -c "
import datetime as d
print((d.datetime.now(d.timezone.utc).replace(hour=5,minute=0,second=0,microsecond=0)+d.timedelta(days=3)).strftime('%Y-%m-%dT%H:%M:%SZ'))")

echo "Registering $N patients..."
mkdir -p /tmp/medislot-race && rm -f /tmp/medislot-race/*
for i in $(seq 1 $N); do
  curl -s -o /dev/null -XPOST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"race-$RUN-$i@demo.test\",\"password\":\"$PW\",\"fullName\":\"Racer $i\"}"
  login "race-$RUN-$i@demo.test" "$PW" > /tmp/medislot-race/token-$i
done

echo "Firing $N simultaneous bookings for doctor $DID at $SLOT"
seq 1 $N | xargs -P $N -I{} sh -c \
  "curl -s -o /dev/null -w '%{http_code}\n' -XPOST '$BASE/api/appointments' -H 'Content-Type: application/json' \
   -H \"Authorization: Bearer \$(cat /tmp/medislot-race/token-{})\" \
   -d '{\"doctorId\":$DID,\"startTime\":\"$SLOT\"}'" | sort | uniq -c | awk '{printf "  %s x HTTP %s\n", $1, $2}'

echo "Expected: 1 x HTTP 201 and 19 x HTTP 409 (one booking, no double-booking)."
