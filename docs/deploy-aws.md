# Deploying MediSlot on AWS EC2 (runbook)

> **Status: not deployed.** This runbook has not been executed on AWS. Everything it relies on (`docker compose up --build`, health checks, the Postman collection) is verified locally; the AWS-specific console steps are the part to double-check as you go.

## What you get
One EC2 instance running the same `docker-compose.yml` as local development: the API, PostgreSQL and a single-node Elasticsearch. Only the API port is published; the database and Elasticsearch are reachable only on the Docker network.

## 1. Launch the instance
- AMI: Ubuntu Server 24.04 LTS. Type: **t3.medium** (4 GB RAM). Elasticsearch is configured with a 512 MB heap, but app + database + search need headroom; t3.small (2 GB) is likely to swap.
- Storage: 20 GB gp3.
- Key pair: create one and keep the `.pem` safe.
- Security group (inbound): `22` from **your IP only**, `80` and `443` from anywhere (`8081` only if you skip the proxy in step 4). Never open `5432` or `9200`.

## 2. Install Docker
```bash
ssh -i your-key.pem ubuntu@<public-ip>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && exit        # log in again so the group applies
```

## 3. Get the code and generate secrets
```bash
git clone https://github.com/habibour/mediSlot.git && cd mediSlot
./scripts/gen-env.sh            # writes .env (mode 600) with random DB password, JWT secret, encryption key
docker compose up -d --build --wait
curl -s localhost:8081/actuator/health          # {"status":"UP",...}
```
**Back up `.env`.** `FIELD_ENCRYPTION_KEY` encrypts patients' national IDs; losing it makes them unreadable.

## 4. Put HTTPS in front (needs a domain pointing at the instance)
The simplest option is Caddy, which obtains and renews certificates automatically. Add to `docker-compose.yml` (or a compose override), replacing `api.example.com`:
```yaml
  caddy:
    image: caddy:2
    ports: ["80:80", "443:443"]
    command: caddy reverse-proxy --from api.example.com --to app:8081
    depends_on: [app]
```
and remove the `ports:` mapping from the `app` service so the API is only reachable through Caddy. Without a domain, serve plain HTTP only for a short demo; JWTs and patient data should not travel unencrypted.

## 5. Rotate the seeded admin (required)
The dev migration creates `admin@medislot.local` with a **publicly documented password**. There is no password-change endpoint yet (see Known limitations in the README), so replace the hash directly:
```bash
sudo apt-get install -y apache2-utils
HASH=$(htpasswd -bnBC 10 "" 'A-long-unique-password' | tr -d ':\n' | sed 's/^\$2y/$2a/')
docker compose exec postgres psql -U medislot -d medislot \
  -c "update users set password_hash = '$HASH' where email = 'admin@medislot.local'"
```
Then confirm the old password is rejected: `POST /api/auth/login` with `Admin@12345` must return 401.

## 6. Smoke test
```bash
npx --yes newman@6 run postman/MediSlot.postman_collection.json \
  -e postman/MediSlot.local.postman_environment.json \
  --env-var baseUrl=https://api.example.com --env-var adminPassword='A-long-unique-password'
```
All 40 requests / 56 assertions should pass. Each run creates new demo users and a doctor, so run it against a demo instance, not against real data.

## 7. Operate
- Logs: `docker compose logs -f app`. Update: `git pull && docker compose up -d --build`.
- Cost control: stop the instance when not demoing and set an AWS billing alarm. Elastic IPs and EBS volumes are billed while stopped.
- Backups: `docker compose exec postgres pg_dump -U medislot medislot > backup.sql`. Elasticsearch can always be rebuilt with `POST /api/admin/search/reindex`.
