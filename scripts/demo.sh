#!/usr/bin/env bash
# Narrated end-to-end demo against a running stack (docker compose up).
# Usage: scripts/demo.sh [baseUrl]    default http://localhost:8081
# Creates fresh demo users and a doctor on every run.
set -uo pipefail
BASE="${1:-http://localhost:8081}"
RUN=$(date +%s)
LETTERS=$(python3 -c "print(''.join(chr(97+int(c)) for c in '$RUN'))")
PW='Passw0rd1'
FAILS=0

# req METHOD PATH TOKEN BODY  -> sets CODE and BODY_OUT
req() {
  local args=(-s -o /tmp/medislot-demo.json -w '%{http_code}' -X "$1" "$BASE$2" -H 'Content-Type: application/json')
  [ -n "${3:-}" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-d "$4")
  CODE=$(curl "${args[@]}"); BODY_OUT=$(cat /tmp/medislot-demo.json)
}
# jget PYEXPR  -> evaluates against last response JSON as d
jget() { python3 -c "import json,sys; d=json.load(open('/tmp/medislot-demo.json')); print($1)" 2>/dev/null; }
# step LABEL EXPECTED [NOTE_PYEXPR]
step() {
  local mark="ok"; [ "$CODE" = "$2" ] || { mark="FAIL (expected $2)"; FAILS=$((FAILS+1)); }
  local note=""; [ -n "${3:-}" ] && note="  -> $(jget "$3")"
  printf '%-52s %s  [%s]%s\n' "$1" "$CODE" "$mark" "$note"
}
tok() { req POST /api/auth/login "" "{\"email\":\"$1\",\"password\":\"$PW\"}"; jget "d['token']"; }
slot() { python3 -c "
import datetime as d
t=d.datetime.now(d.timezone.utc).replace(hour=$1,minute=0,second=0,microsecond=0)+d.timedelta(days=2)
print(t.strftime('%Y-%m-%dT%H:%M:%SZ'))"; }

echo "MediSlot demo against $BASE"
echo
req GET /actuator/health; step "Health check" 200 "d['status']"

req POST /api/auth/register "" "{\"email\":\"pat-$RUN@demo.test\",\"password\":\"$PW\",\"fullName\":\"Demo Patient $LETTERS\",\"phone\":\"01712345678\",\"nationalId\":\"NID55667788\"}"
step "Register patient (with national ID)" 201
req POST /api/auth/register "" "{\"email\":\"PAT-$RUN@demo.test\",\"password\":\"$PW\",\"fullName\":\"Dup\"}"
step "Register same email again" 409 "d['detail']"
req POST /api/auth/register "" '{"email":"nope","password":"x","fullName":""}'
step "Register with invalid fields" 400 "sorted(d['errors'])"

PT=$(tok "pat-$RUN@demo.test")
req GET /api/patients/me "$PT"; step "Patient reads own profile" 200 "'national ID shown as ' + d['nationalId']"
PID=$(jget "d['id']")
req GET /api/patients "$PT"; step "Patient tries to list all patients" 403 "d['title']"
req GET /api/doctors ""; step "Anonymous request" 401 "d['title']"

PW_SAVE=$PW; PW='Admin@12345'; AT=$(tok admin@medislot.local); PW=$PW_SAVE   # seeded dev admin
req POST /api/doctors "$AT" "{\"email\":\"doc-$RUN@demo.test\",\"password\":\"$PW\",\"profile\":{\"fullName\":\"Dr Zq${LETTERS}ovich\",\"specialty\":\"Cardiology\",\"workingStart\":\"09:00\",\"workingEnd\":\"17:00\",\"slotMinutes\":30}}"
step "Admin creates a doctor (09:00-17:00, 30 min slots)" 201 "'doctor id ' + str(d['id'])"
DID=$(jget "d['id']"); DT=$(tok "doc-$RUN@demo.test")

sleep 2.5
req GET "/api/search/doctors?q=Zq${LETTERS}ovch" "$PT"
step "Typo-tolerant search ('ovch' for 'ovich')" 200 "'found ' + ', '.join(x['fullName'] for x in d)"

S=$(slot 4)
req POST /api/appointments "$PT" "{\"doctorId\":$DID,\"startTime\":\"$S\",\"reason\":\"checkup\"}"
step "Patient books 10:00 (clinic time)" 201 "d['status'] + ', ends ' + d['endTime']"
AID=$(jget "d['id']")
STRANGER="str-$RUN@demo.test"
req POST /api/auth/register "" "{\"email\":\"$STRANGER\",\"password\":\"$PW\",\"fullName\":\"Other Patient\"}"; ST=$(tok "$STRANGER")
req POST /api/appointments "$ST" "{\"doctorId\":$DID,\"startTime\":\"$S\"}"; step "Another patient books the same slot" 409 "d['detail']"
req POST /api/appointments "$ST" "{\"doctorId\":$DID,\"startTime\":\"$(slot 0)\"}"; step "Booking at 06:00, before opening" 422 "d['policy']"

req PATCH "/api/appointments/$AID/cancel" "$ST"; step "Other patient tries to cancel it" 403 "d['title']"
req PATCH "/api/appointments/$AID/complete" "$DT"; step "Doctor completes the appointment" 200 "d['status']"
req POST "/api/appointments/$AID/prescriptions" "$DT" '{"medication":"Aspirin","dosage":"75mg"}'; step "Doctor prescribes" 201
req GET "/api/patients/$PID/prescriptions" "$PT"; step "Patient reads own prescriptions" 200 "[p['medication'] for p in d['content']]"
req GET "/api/patients/$PID/prescriptions" "$ST"; step "Other patient reads them" 403 "d['title']"

req GET "/api/admin/audit-logs?resourceType=PATIENT&resourceId=$PID" "$AT"
step "Admin reads the audit trail for this patient" 200 "[r['actorRole'] + ':' + r['action'] for r in d['content']]"

echo
if [ "$FAILS" -eq 0 ]; then echo "All steps behaved as expected."; else echo "$FAILS step(s) did not behave as expected"; exit 1; fi
