# Causa Backend

**AI-Powered Diagnostic Tool for Java/Kubernetes Memory Anomalies**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()
[![Java Version](https://img.shields.io/badge/java-25-orange)]()
[![Quarkus](https://img.shields.io/badge/quarkus-3.x-blue)]()

---

## 📌 Overview

**Causa** is an automated, intelligent diagnostic agent designed to accelerate incident response for Java memory anomalies in Kubernetes environments. By bridging the gap between monitoring systems and advanced language models, Causa acts as an automated first responder. When a memory-related alert is triggered, Causa intercepts the alert, aggregates relevant contextual data (metrics, logs, and K8s events), and performs a dual-layered analysis:

**LLM-Based Diagnostics:** Uses Large Language Models to interpret complex telemetry data, providing human-readable root cause analysis and validating the nature of the anomaly.

**Rule-Based Recommendations:** Employs a deterministic rule engine to generate precise, actionable tuning recommendations for both Kubernetes resource configurations (Requests and Limits) and Java Runtime parameters (Heap sizing, GC policies).

## 🎯 Key Features and Capabilities
- **Automated Alert Ingestion:** Seamlessly listens to webhook payloads from standard monitoring and alerting systems (e.g., Prometheus Alertmanager).
- **Intelligent Root Cause Analysis:** Leverages LLMs to synthesize logs, metrics, and Kubernetes events into a clear, actionable diagnostic summary.
- **K8s Resource Optimization:** Provides calculated recommendations for container memory requests and limits to prevent resource starvation or over-provisioning.
- **JVM Tuning Engine:** Suggests optimal Java Virtual Machine parameters, such as -Xms, -Xmx, and appropriate GC algorithms (e.g., G1GC, ZGC) based on workload profiles.
- **Extensible API Layer:** Exposes a robust set of REST API endpoints designed to serve diagnostic results and recommendations directly to an SRE-facing frontend dashboard.
- **Zero-Friction Deployment:** Packaged for Kubernetes, offering straightforward installation and configuration (via Helm/Kustomize and ConfigMaps) to easily integrate into existing clusters.


## 👨‍👨‍👧‍👧 Target Audience and Value Proposition
The primary users of Causa are Site Reliability Engineers (SREs), Platform Engineers, and DevOps teams.

By automating the initial triage and analysis phases of an incident, Causa drastically reduces MTTR, mitigates the cognitive load on engineering teams, and ensures that Java applications run with optimized resource efficiency and stability in production.


## 🏗️ Architecture

Causa is built as a Java-based microservice, utilizing an event-driven architecture. At its core, it leverages LangChain4J as the central AI orchestration framework to bridge the observability stack (Prometheus), external data tools (MCP Servers), and the Large Language Model.

### High-Level Workflow:

- **Alert Ingestion:** Prometheus triggers a webhook payload to Causa’s Spring Boot / Quarkus REST ingestion endpoint upon detecting a memory anomaly.
- **AI Orchestration (LangChain4J):** The alert payload initializes an orchestration workflow within LangChain4J.
- **Context Routing (MCP via LangChain4J Tools):** LangChain4J handles the retrieval of contextual data. By mapping Model Context Protocol (MCP) servers to LangChain4J @Tool annotations or dynamic tool providers, Causa systematically queries:
- **Kubernetes MCP:** Real-time Pod states, events, and container logs.
- **Cryostat MCP:** JFR (Java Flight Recorder) analysis reports and memory profiles.
- **Kruize MCP:** Deterministic resource and JVM tuning recommendations.
- **Diagnostic Pipeline (LLM):** LangChain4J formats the aggregated data into highly structured prompt templates and manages the communication with the LLM provider (e.g., OpenAI, Vertex AI) to generate the root-cause analysis.
- **Serving:** The LangChain4J output parsers ensure the LLM returns strict, structured JSON, which Causa merges with Kruize's recommendations and serves to the SRE frontend via REST APIs.


## 🤝 Contributing

📖 **[Getting Started Guide](docs/development/getting-started.md)**

---

**Built with ❤️ by the Causa Team**