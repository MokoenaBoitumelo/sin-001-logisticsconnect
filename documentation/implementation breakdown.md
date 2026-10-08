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




🔗 Architecture & Integration MapServicePortPrimary ResponsibilityREST Endpoint ExposedCall Dependenciesingestion-service7050Cleans CSV exportGET /hubsNonehub-service7051Source of truth for hubsGET /hubs/{hubId}GET http://localhost:7050/hubsdelay-stage-service7052Tracks delay stages ($0$–$8$)POST /delay-stage/{hubId}GET /delay-stage/{hubId}Publishes to package-status-topictransit-service7053Calculates arrival ETAGET /eta/{hubId}Calls :7051/hubs/{hubId}Subscribes to package-status-topicalertbot7054Social alert simulationNoneSubscribes to package-status-topic