# backend-service-project

## Backend-service

The backend service receives data related to the CPU usage from the client. The client implementation is ommited and we are using Postman as an API tool to simulate the requests. The client sends information regarding faulty clients, so only clients with 80% or higher CPU usage.

The client sends a Post request to the service containing useful information in the body.
The backend-service receives these data and stores them in a PostgreSQL database and then fetches the data and analyze it accordingly. 
