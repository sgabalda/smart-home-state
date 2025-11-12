# Copilot Instructions for Smart Home State

## Project Overview

- This project manages the state of a smart home system as a single, consistent state.
- It aggregates multiple event sources and updates OpenHAB items via the REST API (OpenHAB is used as a frontend, not as the main state holder).
- The goal is to reduce complexity and inconsistency from managing many OpenHAB items individually.

## Architecture & Key Components

- **src/main/scala/calespiga/**: Main Scala source code.
  - `processor/`: Contains processors for handling and transforming events.
  - `mqtt/`: Handles MQTT communication for event input/output.
  - `model/`: Defines core data models and state representations.
  - `openhab/`: Integrates with the OpenHAB REST API.
  - `persistence/`: State persistence logic.
- **data/**: Contains configuration and state data (e.g., `state.json`).
- **logs/**: Application logs for debugging and monitoring.
- **test-reports/**: XML test reports for CI or local test runs.

## Developer Workflows

- **Build**: Use `sbt compile` to build the project.
- **Test**: Run `sbt test` for all tests. Test reports are output to `test-reports/`.
- **Run**: Use `sbt run` to start the application.
- **Docker**: Use `docker-compose.yml` and `Dockerfile` for containerized deployment.

## Project-Specific Patterns & Conventions

- **Single State Principle**: All device/item states are managed in a single state object, not scattered across multiple OpenHAB items.
- **Event-Driven Processing**: Processors in `processor/` react to events from MQTT, user input, or other sources, and update the state accordingly.
- **Integration Boundaries**: Communication with OpenHAB is isolated in the `openhab/` package. MQTT logic is isolated in `mqtt/`.
- **Testing**: Unit tests are in `src/test/scala/`. Test classes are named `*Suite.scala`.
- **Logging**: Logs are written to `logs/application.log`.

## Style Guidelines

- Follow Scala coding conventions as per the official Scala style guide.
- Use meaningful names for classes, methods, and variables.
- For modifying classes from the model, do not use the copy method, instead use the quicklens dependency for better readability.
- When adding new tests, add them at the end of the test class.

## Openhab items

- The openhab items in scala code can only be defined as annotations of the Event classes in the model package, or in the application.conf file.
- No other definition of openhab items is allowed.
- All openhab items should be in the file `oh/items/smart-home-state.items`, in the section corresponding to their function.
- The items should also be added to the sitemap file `oh/sitemaps/shs.sitemap`, in the section corresponding to their function.

## External Integrations

- **OpenHAB REST API**: Used for reading/writing item states.
- **MQTT**: Used for event input and output.

## Examples

- To add a new event processor, create a class in `processor/` and register it in the main processing pipeline.
- To persist new state fields, update the model in `model/` and the persistence logic in `persistence/`.

## References

- See `README.md` for high-level project motivation and goals.
- See `docker-compose.yml` and `Dockerfile` for deployment details.

---

If you are unsure about a workflow or pattern, check the corresponding package or ask for clarification.
