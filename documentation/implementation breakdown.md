📋 Quick Setup & Environment Check
Before writing business logic, verify your local development environment:

Verify Prerequisites:

Java 17+ (java -version)

Maven 3.8+ (mvn -version)

Docker running (for ActiveMQ in Stage 3)

Package Structure Rule:

Every class in this project lives in a single flat package: co.wethinkcode.logisticsconnect.




🎯 Implementation Roadmap1.Stage 1: CSV Data Cleaning & Ingestion Service:Port 7050 • ~1 to 1.5 hours.ObjectiveClean dirty legacy CSV records in ingestion-service/src/main/resources/hubs-global.csv and serve them over REST.Action StepsInspect Cleaning Requirements:Open ingestion-service/README.md to review the specific issue categories (e.g., header variations, missing fields, trailing spaces, corrupted IDs, improper date formats).Build Data Cleaning Logic:Create a parser to ingest hubs-global.csv.Strip unwanted whitespace, sanitize null/empty values, and correct record formatting.Expose REST Endpoint:Implement Javalin route in IngestionServiceApp.java:GET http://localhost:7050/hubs $\rightarrow$ Returns a JSON array of cleaned hub records.Sanity Check:Run curl http://localhost:7050/hubs and confirm valid, cleaned JSON output.