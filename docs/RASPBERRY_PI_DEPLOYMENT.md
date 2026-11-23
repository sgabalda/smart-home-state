# Raspberry Pi Deployment Guide

The GitHub release workflow builds and publishes two multi-platform images:

- `ghcr.io/sgabalda/smart-home-state:<release-tag>` (backend)
- `ghcr.io/sgabalda/smart-home-state-frontend:<release-tag>` (frontend)

Both application images are built from the same commit and published with the same tag. The release workflow promotes both `stable` tags only after publishing both versioned images. Compose selects both using the single `SHS_VERSION` value in `.env` (default: `stable`), and Watchtower monitors both application services. The Raspberry Pi only needs Docker and the Compose file; it does not need the repository source or a local build toolchain.

Release version tags must not be overwritten. Releases are automatically deployed when Watchtower detects the updated `stable` tags (polling every five minutes).

## Prerequisites

- Raspberry Pi 3B+ or newer with Docker Engine and Docker Compose v2
- An OpenHAB instance accessible to the Pi
- An OpenHAB API token
- A published release with both images available in GHCR

If GHCR requires authentication for either package, log in on the Pi with a GitHub token that has `read:packages`:

```sh
echo "$GHCR_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

For unauthenticated pulls, make both container packages public in their GitHub Packages settings.

## Initial deployment

Create a deployment directory and download the Compose file and environment template:

```sh
mkdir -p ~/smart-home-state
cd ~/smart-home-state
curl -fsSLO https://raw.githubusercontent.com/sgabalda/smart-home-state/main/docker-compose.yml
curl -fsSLO https://raw.githubusercontent.com/sgabalda/smart-home-state/main/.env.template
mkdir -p mosquitto
curl -fsSL \
  https://raw.githubusercontent.com/sgabalda/smart-home-state/main/mosquitto/mosquitto.conf \
  -o mosquitto/mosquitto.conf
cp .env.template .env
mkdir -p data logs
```

Configure the MQTT and OpenHAB values in `.env` for your installation, especially `MQTT_BROKER_HOST`, `OPENHAB_HOST`, and `OPENHAB_API_TOKEN`. Leave `MQTT_BROKER_HOST=localhost` to use the bundled Mosquitto broker. Leave `SHS_VERSION=stable` for automatic updates. Optionally set it to a published release tag to pin both application services.

Pull the images and start the services:

```sh
docker compose pull
docker compose up -d --remove-orphans
docker compose ps
```

Compose starts an anonymous, non-TLS MQTT listener on port 1883 and advertises
the host as `mqtt-broker.local` over mDNS. Restrict access to the trusted LAN;
do not expose port 1883 to the internet. The Nano and Uno Ethernet sketches do
not support mDNS, so they continue to use `192.168.2.113`; assign that address
to the Raspberry Pi (or update their broker IPs) for those boards to connect.
The ESP32 sketch resolves `mqtt-broker.local` using mDNS.

The Nginx frontend serves HTTP on port 8888 and proxies `/api` requests to the backend on port 8081. Both containers use host networking to preserve the backend's existing access to MQTT/OpenHAB and allow the local Nginx proxy. Forward router port 80 to the Pi's port 8888 for HTTP access.

HTTPS and Let's Encrypt are not configured by this Compose setup. Do not expose the HTTP service publicly until HTTPS termination and the appropriate router/firewall rules are configured.

## Deploying a release

1. Publish a GitHub release with a new tag.
2. The **Release** workflow publishes both versioned images, then promotes both `stable` tags.
3. Watchtower detects the updated images and deploys them automatically.

Watchtower updates containers sequentially, not as an atomic pair. Keep API changes backward-compatible across the short transition while one service may be on the new version and the other on the old version.

## Rollback

For an immediate rollback, pin both services to the previous release by setting `SHS_VERSION` to that release tag, then run:

```sh
docker compose pull
docker compose up -d
```

The application state remains in the `data/` bind mount. Keep backups of `data/`, `.env`, and `docker-compose.yml`.

To resume automatic deployments after pinning, set `SHS_VERSION=stable`.

The **Manual Deploy** GitHub Actions workflow also promotes a matching pair to `stable`. For a Git tag, it reuses the already-published backend and frontend images; for a branch or commit, it builds both images first. After both source images exist, it promotes both `stable` tags and Watchtower deploys them. No Raspberry Pi login is needed.

Versioned tags are immutable by deployment convention, not enforced by GHCR. Do not republish a release tag.

## Monitoring

```sh
docker compose ps
docker compose logs -f smart-home-state
docker compose logs -f frontend
docker stats
```
