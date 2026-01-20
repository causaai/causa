# RCA Agent Analysis Tool (Quarkus)

Causa RCA agent is a Quarkus-based Root Cause Analysis (RCA) agent for Kubernetes. It uses LangChain4j to orchestrate multiple AI agents for anomaly detection, RCA, and report validation.

## Architecture

The application follows a pipeline-based approach:
1. **Data Collection**: Fetches metrics (Prometheus), logs, and JFR analysis.
2. **Anomaly Detection (Model A)**: Classifies the incident.
3. **Root Cause Analysis (Model B)**: Performs deep reasoning with RAG (Retrieval Augmented Generation) using knowledge base runbooks.
4. **Validation (Model C)**: Critiques the findings and formats the output into a structured JSON report.

