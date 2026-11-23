# Smart Home State

Application to manage the state of an OpenHAB smart home system as a single state.

## Why?

I have been developing my home automation system for a while now and I have a lot of different components that I want to manage in a single state.
This application is designed to do just that.

Having multiple Items in an OpenHab system is error-prone and challenging to manage. As the number of items grows, and you have to have many
of them to manage single components, the odds of having inconsistencies or making mistakes increases.

This application has multiple inputs as events and as an output updates the items using the OpenHab REST API.

## Modules and API types

- `shared/` contains the Scala API models and Tapir endpoint contracts.
- `backend/` contains the existing Scala application and its HTTP4s server.
- `frontend/` contains the Vite/React application. Its API definitions are generated from the shared Tapir endpoint's OpenAPI schema.

To install frontend dependencies, generate the TypeScript API types, and check the frontend:

```sh
npm --prefix frontend install
npm --prefix frontend run sync-types
npm --prefix frontend run typecheck
```

`sync-types` invokes `sbt generateTsTypes`, which compiles `shared`, writes the endpoint's OpenAPI schema, then runs `openapi-typescript` to create `frontend/src/generated/api-types.ts`. The frontend dev server proxies `/api` to the backend's default port, 8081.

GitHub release builds publish matching multi-platform backend and frontend images to GHCR with the same release tag. Only after both versioned images are published does the workflow promote their `stable` tags; Watchtower monitors both services and updates them on the Raspberry Pi. The server only needs Docker Compose and the deployment configuration; it does not need the repository source code. Compose uses one `SHS_VERSION` value for both images, defaulting to `stable`, so they cannot be configured to track different channels.

Copy `.env.template` to `.env` and configure the backend values:

```sh
cp .env.template .env
# edit .env and set SHS_VERSION and backend configuration
docker compose pull
docker compose up -d --remove-orphans
```

Release deployment is automatic: Watchtower checks for updated image tags every five minutes. To pin or roll back both services, set `SHS_VERSION` to the same published release tag, then run `docker compose pull && docker compose up -d`. Set it back to `stable` to resume automatic deployments.

The frontend is served on port 8888; Nginx proxies `/api` to the backend on port 8081. The backend and frontend use host networking to preserve the backend's existing access to MQTT/OpenHAB and allow that localhost proxy. Forward router port 80 to the Raspberry Pi's port 8888 to access the UI over HTTP. Configure HTTPS and Let's Encrypt before exposing it publicly; HTTPS termination is not included yet.

The release tags are immutable by convention; the mutable `stable` tags are promoted only after both release images are available. Watchtower updates the services sequentially, so there may be a short interval where one container is on the new release and the other is still on the previous release. Keep API changes backward-compatible across that interval.

For frontend development outside Docker, run `sbt "backend/run"` and `npm --prefix frontend run dev` in separate terminals. The Vite dev server proxies `/api` to backend port 8081.

The generated interface is derived from `shared/src/main/scala/calespiga/shared/SystemStatus.scala`; changing its fields and regenerating types causes incompatible frontend property accesses to fail `typecheck`.