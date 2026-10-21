# Project Codebase Export

## File: `./.gitignore`

```text
target/
*.class
dependency-reduced-pom.xml
.idea/
*.iml
.vscode/
.DS_Store

```

## File: `./README.md`

```text
# LogisticsConnect

## Overview

Supply chain parcel delivery hub and transit delay tracking.

Domain entities: hubs, sorting centers, regional districts.

Every class in this repo lives in a single flat package, `co.wethinkcode.logisticsconnect`. LogisticsConnect is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`hubs-global.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`package-status-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **AlertBotApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7050 | Parses and cleans `hubs-global.csv` |
| HubServiceApp | [`hub-service/`](hub-service) | 7051 | Serves provinces and sorting centers (place-name source of truth). |
| DelayStageServiceApp | [`delay-stage-service/`](delay-stage-service) | 7052 | Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns). |
| TransitServiceApp | [`transit-service/`](transit-service) | 7053 | Calculates estimated arrival windows based on hub and delay stage. |
| AlertBotApp | [`alertbot/`](alertbot) | 7054 | posts proactive delay notifications to public transit social media pages (simulated). |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `package-status-topic`: Package status updates move from latency-driven RPC to bandwidth-driven messaging.

**Status:** scaffold only — build files, Javalin bootstrap, and TODOs are in place; no
business logic has been implemented yet.

## Your task

Implement the four stages below, in order — each one builds on the last, and the
later stages assume the earlier ones work. Stage 1-3 are required; stage 4 is a
stretch goal if you have time left.

| Stage | Required? | What "done" looks like | Rough effort |
|---|---|---|---|
| 1. Clean `hubs-global.csv` | Required | IngestionServiceApp exposes the cleaned records via REST (see [Integration contracts](#integration-contracts)); every issue category in [ingestion-service/README.md](ingestion-service/README.md#known-data-issues) is handled | ~1-1.5h |
| 2. Wire up the REST services | Required | hub-service, delay-stage-service, and transit-service each expose real domain endpoints (not just `/health`) and call each other synchronously per the contracts below; `transit-service` can return an ETA for a hub | ~1.5-2h |
| 3. Decouple with the MQ topic | Required | `delay-stage-service` publishes to `package-status-topic` on stage change; `transit-service` subscribes instead of calling `delay-stage-service` directly; broker runs via `common/docker-compose.yml` | ~1h |
| 4. AlertBot | Stretch | `alertbot` subscribes to `package-status-topic` and simulates posting an alert when a hub's delay stage crosses a threshold you choose | ~30-45m |

You don't need to match any exact field names, endpoint paths, or message shapes —
the ones below are illustrative. Favor a working, readable implementation over a
gold-plated one; partial completion of stage 3 or 4 is fine if 1-2 are solid.

## Integration contracts

Two kinds of integration point exist in this repo: synchronous REST calls (stage 2)
and the asynchronous MQ topic (stage 3). Field/endpoint names below are illustrative
— reasonable variations are fine as long as the shape (who calls whom, with what
kind of payload) is preserved.

### REST (stage 2)

| Caller | Callee | Example | Purpose |
|---|---|---|---|
| hub-service | ingestion-service | `GET :7050/hubs` → JSON array of cleaned hub records | hub-service loads its place-name data from the cleaned CSV output instead of re-parsing it itself |
| transit-service | hub-service | `GET :7051/hubs/{hubId}` → hub/sorting-center details | transit-service needs hub location data to calculate an ETA |
| transit-service | delay-stage-service | `GET :7052/delay-stage/{hubId}` → `{ "hubId": "H-501", "stage": 3 }` | transit-service needs the current delay stage to calculate an ETA — **this call is replaced by the MQ subscription in stage 3** |
| (client) | delay-stage-service | `POST :7052/delay-stage/{hubId}` with a body like `{ "stage": 3 }` | the stage/state-change endpoint referenced in [common/README.md](common/README.md) — this is also where the stage-3 MQ publish happens |

### MQ (stage 3) — topic `package-status-topic`

Already documented in detail in [common/README.md](common/README.md): broker URL and
topic name come from the shared `co.wethinkcode.logisticsconnect.mq.MqConfig` class,
duplicated into each participating service.

- **Producer:** `delay-stage-service`, on its stage/state-change endpoint above.
- **Consumers:** `transit-service` (replacing its direct REST call to
  delay-stage-service) and, for the stretch goal, `alertbot`.
- **Example message shape:** `{ "hubId": "H-501", "stage": 5, "timestamp": "2026-07-18T10:15:00Z" }`

## Project structure

```
logisticsconnect/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7050)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/logisticsconnect/IngestionServiceApp.java
│       └── resources/hubs-global.csv
├── hub-service/          (port 7051)
├── delay-stage-service/          (port 7052)
├── transit-service/          (port 7053)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── alertbot/          (port 7054)
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every folder here (`ingestion-service/`, each domain service, and `alertbot/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```
cd hub-service
mvn package
```

...or build every module in the repo in one pass from the project root:

```
find . -name pom.xml -execdir mvn -q package \;
```

## Run

```
# ingestion
cd ingestion-service && mvn package && java -jar target/ingestion-service.jar

# domain services, each in its own terminal
# terminal 1
cd hub-service && mvn package && java -jar target/hub-service.jar
# terminal 2
cd delay-stage-service && mvn package && java -jar target/delay-stage-service.jar
# terminal 3
cd transit-service && mvn package && java -jar target/transit-service.jar

# MQ broker (needed once the MQ-aware services above are wired up)
cd common && docker compose up -d

# alerting
cd alertbot && mvn package && java -jar target/alertbot.jar
```

| Service | Port |
|---|---|
| IngestionServiceApp (`ingestion-service`) | 7050 |
| HubServiceApp (`hub-service`) | 7051 |
| DelayStageServiceApp (`delay-stage-service`) | 7052 |
| TransitServiceApp (`transit-service`) | 7053 |
| AlertBotApp (`alertbot`) | 7054 |

## Test

No automated tests exist yet (this is a scaffold). Each running service exposes
`/health`, so sanity-check manually:

```
curl http://localhost:7050/health   # -> OK
```

To add real tests to a module, add JUnit 5 and Surefire to its `pom.xml`:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>
```

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
</plugin>
```

then add tests under that module's `src/test/java/...` and run:

```
mvn test
```

```

## File: `./RUBRIC.md`

```text
# RUBRIC — for reviewers only, do not share with candidates

Scores the four stages from the root [README.md](README.md#your-task) "Your task"
table, plus cross-cutting concerns. Stages 1-3 are required; stage 4 is a stretch
goal — weight it as a bonus, not a requirement for a passing score.

## Stage 1 — Clean `hubs-global.csv`

- [ ] Handles casing inconsistencies (IDs, province names, status/category values)
- [ ] Trims padding / collapses internal double spaces
- [ ] Normalizes date formats (if candidate touches date-like fields beyond this CSV's columns)
- [ ] Normalizes boolean/flag representations (`Y`/`yes`/`1`/`true`/`YES` → one form)
- [ ] Handles missing/placeholder values (`N/A`, `unknown`, blank, etc.) sensibly — not just left as-is
- [ ] Some form of duplicate detection/resolution attempted, with reasoning the
      candidate can articulate (exact strategy is open — judge the reasoning, not
      a specific answer)
- [ ] Cleaned records are exposed via REST for other services to consume (not just
      cleaned and logged/discarded)

## Stage 2 — REST services

- [ ] hub-service, delay-stage-service, and transit-service each expose real
      domain endpoints beyond `/health`
- [ ] hub-service serves place-name data sourced from ingestion-service's cleaned
      output (directly or cached)
- [ ] transit-service calls hub-service and delay-stage-service and successfully
      returns an ETA-shaped response for at least one hub
- [ ] Reasonable HTTP semantics (status codes, JSON bodies) — doesn't need to be
      polished, just functional and legible

## Stage 3 — MQ decoupling

- [ ] delay-stage-service publishes to `package-status-topic` on a stage change
- [ ] transit-service subscribes to the topic instead of calling
      delay-stage-service synchronously
- [ ] Broker comes up via `common/docker-compose.yml` and a message can be
      observed flowing end-to-end (logs or the ActiveMQ web console are fine
      evidence)

## Stage 4 — AlertBot (stretch)

- [ ] alertbot subscribes to `package-status-topic`
- [ ] Some threshold/condition triggers a simulated alert (webhook call, log
      line, etc. — "simulated" is explicit in the brief, no real social API needed)

## Cross-cutting

- [ ] **Readability** — code a teammate could pick up without a walkthrough
- [ ] **Incremental commits** — history shows the stages being built up, not one
      giant commit
- [ ] **Tests** — any automated tests beyond the provided `/health` smoke checks
      (not required per the scaffold, but a strong positive signal)
- [ ] **Articulating tradeoffs** — in a debrief/PR description, can the candidate
      explain the dedup strategy they picked, why REST vs. MQ makes sense at each
      stage, and what they'd do differently with more time

```

## File: `./common/docker-compose.yml`

```text
version: "3.8"
services:
  activemq:
    image: apache/activemq-classic:5.18.3
    ports:
      - "61616:61616"   # OpenWire (client connections)
      - "8161:8161"     # Web console

```

## File: `./common/README.md`

```text
# common — Asynchronous Decoupling (MQ)

## Overview

Topic: `package-status-topic`

Package status updates move from latency-driven RPC to bandwidth-driven messaging.

Part of the [LogisticsConnect](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own.

- Producer: `delay-stage-service` (`../delay-stage-service`)
- Consumer(s): `transit-service` (required, stage 3); `alertbot` (stretch, stage 4 —
  reacts to stage changes to decide when to raise an alert)

Broker URL and topic name are shared via a common `co.wethinkcode.logisticsconnect.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has
`src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Once the TODOs below are implemented, verify end-to-end by publishing a message from
`delay-stage-service` and confirming the consumer(s) receive it — e.g. via logs, or by
watching the topic in the web console.

## TODO

- Add `activemq-client` publish logic to `delay-stage-service` on its stage/state-change endpoint.
- Add `activemq-client` subscriber logic to consumer service(s) above, replacing any
  direct synchronous calls to `delay-stage-service`.
- `alertbot` (stretch goal) needs its own subscriber logic too — its `pom.xml`
  already has the `activemq-client` dependency alongside the other participating
  services.

```

## File: `./ingestion-service/README.md`

```text
# IngestionServiceApp

## Overview

Parses and cleans `hubs-global.csv`, a messy legacy export of hubs, sorting centers, and regional districts data, and is the
first stop in the LogisticsConnect pipeline. Independent Maven module, no parent pom.

Part of the [LogisticsConnect](../README.md) project.

## Known data issues

`hubs-global.csv` is deliberately messy — cleaning it is the point of this service. Look
out for (and handle) at least:

- **Inconsistent casing** in IDs, names, and status/category values (`Active` /
  `active` / `ACTIVE`)
- **Padding** — leading/trailing spaces, and the occasional double space, inside
  fields
- **Duplicate records** for the same real-world entity, written with a different ID
  casing/format and/or slightly different field values
- **Inconsistent date formats** (`YYYY-MM-DD`, `MM/DD/YYYY`, `DD-MM-YYYY`, one- and
  two-digit months/days) and outright invalid dates
- **Missing / placeholder values** — blank fields, `N/A`, `n/a`, `TBD`, `unknown`,
  `-`, `NaN`
- **Invalid or non-numeric values** in numeric columns (negative counts, spelled-out
  numbers, unrealistic values)
- **Inconsistent boolean/flag representations** (`Y`/`N`, `yes`/`no`, `1`/`0`,
  `true`/`FALSE`)
- **Naming/spelling variants** for the same thing (e.g. regional spelling
  differences, synonyms)

## Worked example

Two raw rows from `hubs-global.csv`:

```
hub_id, Province ,sorting_center,active
H-502 ,gauteng,Pretoria North,0
H-505,Western Cape ,Cape Town  Port,TRUE
```

A reasonable cleaned shape for those same two rows:

| hub_id | province | sorting_center | active |
|---|---|---|---|
| H-502 | Gauteng | Pretoria North | false |
| H-505 | Western Cape | Cape Town Port | true |

That covers padding (`H-502 ` → `H-502`), casing (`gauteng` → `Gauteng`), a
collapsed double space (`Cape Town  Port` → `Cape Town Port`), and boolean
normalization (`0`/`TRUE` → `false`/`true`). Column names, exact casing
convention, and boolean representation are up to you — just be consistent.

This doesn't cover deduplication: rows like `H-500`, `H-504`, `H-510`, and `H-515`
all describe "Johannesburg Central" in "Gauteng" under different hub IDs, with
conflicting `active` values between them. How you resolve that (which one wins,
how you detect they're duplicates in the first place) is part of the exercise —
there's no single correct answer, but be ready to explain your reasoning.

## Project structure

```
ingestion-service/
├── pom.xml
└── src/main/
    ├── java/co/wethinkcode/logisticsconnect/IngestionServiceApp.java
    └── resources/hubs-global.csv
```

## Build

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port `7050`. Currently just exposes `/health` — the actual CSV
parsing/cleaning logic is a TODO.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7050/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/logisticsconnect/`, and run `mvn test`.

```

## File: `./ingestion-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.logisticsconnect</groupId>
  <artifactId>ingestion-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>com.opencsv</groupId>
      <artifactId>opencsv</artifactId>
      <version>5.9</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>ingestion-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.logisticsconnect.IngestionServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./ingestion-service/src/main/java/co/wethinkcode/logisticsconnect/IngestionServiceApp.java`

```text
package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

public class IngestionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/hubs-global.csv (hubs, sorting centers, regional districts data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
    }
}

```

## File: `./ingestion-service/src/main/resources/hubs-global.csv`

```text
hub_id, Province ,sorting_center,active
H-500, Gauteng ,Johannesburg Central,Y
h-501,Western Cape,Cape Town Port,yes
H-502 ,gauteng,Pretoria North,0
H-503,KwaZulu-Natal,Durban Harbour,Y
H-504,Gauteng,Johannesburg Central,true
H-505,Western Cape ,Cape Town  Port,TRUE
H-506,Kwa-Zulu Natal,Durban Harbour,1
H-507,Free State,Bloemfontein Hub,N
H-508,,Pretoria North,yes
H-509,eastern cape,Port Elizabeth Hub,0
H-510,Gauteng,johannesburg central,FALSE
H-511,Limpopo,Polokwane Hub,unknown
h-512,western cape,Cape Town Port,Y
H-513,North West,Rustenburg Hub,no
H-514,Mpumalanga ,Nelspruit Hub,1
H-515,Gauteng,Johannesburg Central,YES
H-516,KwaZulu Natal,Durban Harbour,true
H-517,Northern Cape,Kimberley Hub,N/A

```

## File: `./alertbot/README.md`

```text
# AlertBotApp

## Overview

Posts proactive delay notifications to public transit social media pages (simulated).

Part of the [LogisticsConnect](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

Mechanism: Outbound webhook, simulated social post

MQ (stretch goal): this service subscribes to the ActiveMQ topic
`package-status-topic` — see [`../common/`](../common). Broker URL and topic name
come from the common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside
it in this module. Use the stage in each message to decide when to raise an alert
(e.g. above a threshold you choose).

## Project structure

```
alertbot/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── AlertBotApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/alertbot.jar
```

Listens on port `7054`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7054/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/logisticsconnect/`, and run `mvn test`.

```

## File: `./alertbot/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.logisticsconnect</groupId>
  <artifactId>alertbot</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>alertbot</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.logisticsconnect.AlertBotApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./alertbot/src/main/java/co/wethinkcode/logisticsconnect/AlertBotApp.java`

```text
package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

public class AlertBotApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7054);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Posts proactive delay notifications to public transit social media pages (simulated).)
        // Mechanism: Outbound webhook, simulated social post
    }
}

// MQ TODO (stretch goal): subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)

```

## File: `./alertbot/src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`

```text
package co.wethinkcode.logisticsconnect.mq;

/**
 * Shared by every producer/consumer service that talks to the "package-status-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "package-status-topic";

    private MqConfig() {
    }
}

```

## File: `./hub-service/README.md`

```text
# HubServiceApp

## Overview

Serves provinces and sorting centers (place-name source of truth).

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

## Project structure

```
hub-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/HubServiceApp.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/hub-service.jar
```

Listens on port `7051`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7051/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/logisticsconnect/`, and run `mvn test`.

```

## File: `./hub-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.logisticsconnect</groupId>
  <artifactId>hub-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>hub-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.logisticsconnect.HubServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./hub-service/src/main/java/co/wethinkcode/logisticsconnect/HubServiceApp.java`

```text
package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

public class HubServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7051);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Serves provinces and sorting centers (place-name source of truth).)
        // Add domain endpoints for hub-service here.
    }
}

```

## File: `./delay-stage-service/README.md`

```text
# DelayStageServiceApp

## Overview

Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `package-status-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside it in this module.

## Project structure

```
delay-stage-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── DelayStageServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/delay-stage-service.jar
```

Listens on port `7052`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7052/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/logisticsconnect/`, and run `mvn test`.

```

## File: `./delay-stage-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.logisticsconnect</groupId>
  <artifactId>delay-stage-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>delay-stage-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.logisticsconnect.DelayStageServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./delay-stage-service/src/main/java/co/wethinkcode/logisticsconnect/DelayStageServiceApp.java`

```text
package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

public class DelayStageServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7052);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Tracks the Transit Delay Stage (0-8, e.g. weather shutdowns).)
        // Add domain endpoints for delay-stage-service here.
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)

```

## File: `./delay-stage-service/src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`

```text
package co.wethinkcode.logisticsconnect.mq;

/**
 * Shared by every producer/consumer service that talks to the "package-status-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "package-status-topic";

    private MqConfig() {
    }
}

```

## File: `./transit-service/README.md`

```text
# TransitServiceApp

## Overview

Calculates estimated arrival windows based on hub and delay stage.

Part of the [LogisticsConnect](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service subscribes to the ActiveMQ topic `package-status-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.logisticsconnect.mq.MqConfig` class alongside it in this module.

## Project structure

```
transit-service/
├── pom.xml
└── src/main/java/co/wethinkcode/logisticsconnect/
    ├── TransitServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/transit-service.jar
```

Listens on port `7053`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7053/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/logisticsconnect/`, and run `mvn test`.

```

## File: `./transit-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.logisticsconnect</groupId>
  <artifactId>transit-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.15.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>transit-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.logisticsconnect.TransitServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./transit-service/src/main/java/co/wethinkcode/logisticsconnect/TransitServiceApp.java`

```text
package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

public class TransitServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7053);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Calculates estimated arrival windows based on hub and delay stage.)
        // Add domain endpoints for transit-service here.
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.logisticsconnect.mq.MqConfig)

```

## File: `./transit-service/src/main/java/co/wethinkcode/logisticsconnect/mq/MqConfig.java`

```text
package co.wethinkcode.logisticsconnect.mq;

/**
 * Shared by every producer/consumer service that talks to the "package-status-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "package-status-topic";

    private MqConfig() {
    }
}

```

## File: `./documentation/wiki.md`

```text
🚀 Project OverviewProject Name: [Insert Project Name]Target Completion Date: Day 14Core Objective: Build a functional Minimum Viable Product (MVP) focusing on core features before adding nice-to-haves.Tech Stack & ToolsLanguage/Framework: [e.g., Python, Java, Node.js]Version Control: Git & GitHub (Feature branch workflow)Project Management: GitHub Projects / Trello📅 2-Week Timeline & MilestonesWeekPhaseKey DeliverablesWeek 1Foundation & MVP• Project setup & repo initialization• Core data structures and architecture• Basic working prototype (happy path)Week 2Refinement & Polish• Edge case handling & error management• Testing (Unit/Integration tests)• Documentation & final cleanupDetailed Daily BreakdownWeek 1: Setup and Core LogicDay 1: Scaffolding & ArchitectureInitialize repository, configure build tools/dependencies, and set up the folder structure.Define data models, database schema (if applicable), and API contracts.Day 2–3: Core Features (The Engine)Implement the primary business logic.Get the core feature working end-to-end in a basic state (MVP).Day 4–5: Integration & Initial TestingConnect components together and write initial unit tests for critical logic.Fix blocking bugs and refine data flow.Week 2: Enhancement, Testing, and DeliveryDay 8–9: Secondary Features & UI/InterfaceAdd secondary features, user input handling, or CLI/GUI polish.Day 10–11: Testing & Bug SmashingPerform integration testing, handle edge cases, and fix remaining bugs.Day 12–13: Documentation & Code ReviewWrite a clear README.md (setup instructions, usage guide, tech stack).Clean up comments, format code, and ensure clean Git history.Day 14: Final Submission / DemoFinal review against project requirements and tag release version.📋 Task Backlog & Tracking[ ] Phase 1: Setup[ ] Initialize repo and .gitignore[ ] Define project structure and dependencies[ ] Phase 2: Core Development[ ] Implement core logic / algorithms[ ] Build input/output interface (CLI, API, or UI)[ ] Phase 3: Testing & Polish[ ] Write unit tests for core modules[ ] Handle errors and edge cases[ ] Phase 4: Documentation[ ] Complete README.md setup guide[ ] Add code comments and usage examples🛠️ Definition of Done (DoD)A task or feature is only considered "Done" when:Code compiles/runs without errors or unhandled warnings.Tested locally against expected inputs and basic edge cases.Committed cleanly with descriptive commit messages on a feature branch.Merged into the main branch via a reviewed Pull Request (if working in a team).
```

## File: `./documentation/implementation breakdown.md`

```text
📋 Quick Setup & Environment Check
Before writing business logic, verify your local development environment:

Verify Prerequisites:

Java 17+ (java -version)

Maven 3.8+ (mvn -version)

Docker running (for ActiveMQ in Stage 3)

Package Structure Rule:

Every class in this project lives in a single flat package: co.wethinkcode.logisticsconnect.




🎯 Implementation Roadmap1.Stage 1: CSV Data Cleaning & Ingestion Service:Port 7050 • ~1 to 1.5 hours.ObjectiveClean dirty legacy CSV records in ingestion-service/src/main/resources/hubs-global.csv and serve them over REST.Action StepsInspect Cleaning Requirements:Open ingestion-service/README.md to review the specific issue categories (e.g., header variations, missing fields, trailing spaces, corrupted IDs, improper date formats).Build Data Cleaning Logic:Create a parser to ingest hubs-global.csv.Strip unwanted whitespace, sanitize null/empty values, and correct record formatting.Expose REST Endpoint:Implement Javalin route in IngestionServiceApp.java:GET http://localhost:7050/hubs $\rightarrow$ Returns a JSON array of cleaned hub records.Sanity Check:Run curl http://localhost:7050/hubs and confirm valid, cleaned JSON output.




2.Stage 2: Wire Up Synchronous REST Services:Ports 7051, 7052, 7053 • ~1.5 to 2 hours.ObjectiveExpose domain endpoints for hub-service, delay-stage-service, and transit-service, then connect them via direct HTTP calls.Action StepsHub Service (hub-service :7051):Call IngestionServiceApp at startup/request (GET :7050/hubs) to load cleaned place-name data.Expose route: GET /hubs/{hubId} $\rightarrow$ Returns hub/sorting-center details.Delay Stage Service (delay-stage-service :7052):Maintain in-memory transit delay stages ($0$–$8$).Expose route: POST /delay-stage/{hubId} with body {"stage": X} to update a hub's stage.Expose route: GET /delay-stage/{hubId} $\rightarrow$ Returns {"hubId": "...", "stage": X}.Transit Service (transit-service :7053):Expose route: GET /eta/{hubId}.Under the hood, make synchronous HTTP GET requests to:hub-service (:7051/hubs/{hubId}) for location metadata.delay-stage-service (:7052/delay-stage/{hubId}) for current delay stage.Calculate and return the estimated arrival window (ETA).





3.Stage 3: Asynchronous Decoupling via ActiveMQ:Shared Broker & Topic package-status-topic • ~1 hour.ObjectiveReplace the synchronous REST call between transit-service and delay-stage-service with event-driven messaging.Action StepsSpin up Broker:Run cd common && docker compose up -d to launch the ActiveMQ broker.Producer (delay-stage-service):On POST /delay-stage/{hubId}, publish a JSON message to package-status-topic using configuration in co.wethinkcode.logisticsconnect.mq.MqConfig.Payload: {"hubId": "H-501", "stage": 5, "timestamp": "2026-07-18T10:15:00Z"}.Consumer (transit-service):Subscribe to package-status-topic.Remove the direct HTTP call to :7052/delay-stage/{hubId}.Maintain local cache/state of hub delay stages updated directly from incoming ActiveMQ messages.





4.Stage 4: AlertBot Notification Service (Stretch Goal):Port 7054 • ~30 to 45 mins.ObjectiveSimulate external delay alerting when severe disruption occurs.Action StepsConsumer Setup (alertbot):Subscribe AlertBotApp to package-status-topic.Threshold Alert Logic:Set a delay stage threshold (e.g., stage $\ge 5$).When a message exceeds this threshold, print/log a simulated social media broadcast (e.g., "ALERT: Hub H-501 experiencing severe delay (Stage 5)").




🔗 Architecture & Integration Map


Service              Port      Primary Responsibility           REST Endpoint Exposed        Call Dependencies
ingestion-service    7050      Cleans CSV export                GET /hubs                    None
hub-service          7051      Source of truth for hubs         GET /hubs/{hubId}            GET http://localhost:7050/hubsdelay-stage-service7052Tracks
delay-stage-service  7052      Tracks delay stages ($0$–$8$)    POST /delay-stage/{hubId}    Publishes to package-status-topic
                                                                GET /delay-stage/{hubId}
transit-service      7053      Calculates arrival ETA           GET /eta/{hubId}             Calls :7051/hubs/{hubId}
                                                                                             Subscribes to package-status-topic
alertbot             7054      Social alert simulation          None                         Subscribes to package-status-topic



🧪 Testing & Execution Cheat Sheet


1. Build All Modules
   Bash
   find . -name pom.xml -execdir mvn -q package \;



2. Start Services (In separate terminal tabs)
   Bash
# Terminal 1 - Broker
cd common && docker compose up -d

# Terminal 2 - Ingestion
cd ingestion-service && java -jar target/ingestion-service.jar

# Terminal 3 - Hub
cd hub-service && java -jar target/hub-service.jar

# Terminal 4 - Delay Stage
cd delay-stage-service && java -jar target/delay-stage-service.jar

# Terminal 5 - Transit
cd transit-service && java -jar target/transit-service.jar

# Terminal 6 - AlertBot (Optional)
cd alertbot && java -jar target/alertbot.jar



3. Verify System Flow
   Bash
# Check service health
curl http://localhost:7050/health

# Trigger stage change (Delay Stage Service publishes to MQ)
curl -X POST http://localhost:7052/delay-stage/H-501 \
-H "Content-Type: application/json" \
-d '{"stage": 5}'

# Fetch calculated ETA from Transit Service
curl http://localhost:7053/eta/H-501
```

## File: `./documentation/knowledge_base.txt`

```text

```

