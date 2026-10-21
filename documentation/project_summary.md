# Solution Overview & Final Architecture                                [ hubs-global.csv ]
│
▼
┌───────────────────┐
│ Ingestion Service │ (:7050)
└─────────┬─────────┘
│ GET /hubs
▼
┌───────────────────┐
│    Hub Service    │ (:7051)
└─────────┬─────────┘
│ GET /hubs/{hubId}
▼
┌─────────────────────┐        ┌───────────────────┐
│ Delay Stage Service │        │  Transit Service  │ (:7053)
└──────────┬──────────┘        └─────────▲─────────┘
│                             │
│ POST /delay-stage/{hubId}   │ Subscribes to updates
│                   │
▼                             │
┌────────────────────────────────────────┴─────────┐
│        ActiveMQ (package-status-topic)          │[cite: 1]
└────────────────────────┬─────────────────────────┘
│
│ Subscribes (Stage >= 4)[cite: 1]
▼
┌───────────────────┐
│     AlertBot      │ (:7054)[cite: 1]
└───────────────────┘
1. Stage 1 (Ingestion Service - :7050): Sanitizes and cleans hubs-global.csv (whitespace trimming, casing normalization, boolean standardizing, and deduplication) and exposes the clean JSON via GET /hubs[cite: 1].

2. Stage 2 (Synchronous REST Services):
   hub-service (:7051) acts as the place-name source of truth by loading cleaned hub data from ingestion-service[cite: 1].
   delay-stage-service (:7052) tracks in-memory delay stages ($0$–$8$)[cite: 1].

3. Stage 3 (Asynchronous Decoupling via ActiveMQ): delay-stage-service publishes delay updates to package-status-topic[cite: 1]. transit-service (:7053) subscribes to the topic to maintain a local delay cache and calculates total ETA windows without synchronous REST coupling to delay-stage-service[cite: 1].

4. Stage 4 (AlertBot Notification Service - :7054): alertbot subscribes to package-status-topic and triggers simulated public transit notifications whenever a hub's delay stage hits or exceeds the severe disruption threshold (Stage $\ge 4$)[cite: 1]. 

## Verification Summary
You can verify the entire decoupled system end-to-end with these quick steps:

Verify Health Endpoints:

    Bash
    curl http://localhost:7050/health   # Ingestion Service[cite: 1]
    curl http://localhost:7051/health   # Hub Service[cite: 1]
    curl http://localhost:7052/health   # Delay Stage Service[cite: 1]
    curl http://localhost:7053/health   # Transit Service[cite: 1]
    curl http://localhost:7054/health   # AlertBot Service[cite: 1]

Trigger Disruption Update:
    
    Bash
    curl -X POST http://localhost:7052/delay-stage/H-501 \
    -H "Content-Type: application/json" \
    -d '{"stage": 6}'

Check ETA Result (transit-service):
    
    Bash
    curl http://localhost:7053/eta/H-501
