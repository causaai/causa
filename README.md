<!-- Back to top anchor -->
<a id="readme-top"></a>
<div align="center">
  <h2 align="center">Causa AI Agent</h2>
  <p align="center">
    Automated, intelligent root-cause analysis for Java Memory Anomalies in Kubernetes.
    <br /><br />
    <a href="https://github.com/causaai/causa-backend/tree/main/docs">Explore The Docs</a>
    &middot;
    <a href="https://github.com/causaai/causa-backend/tree/main/docs/api">API Specifications</a>
    &middot;
    <a href="https://github.com/causaai/causa-backend/tree/main/docs/tunables">Tunables</a>
    &middot;
    <a href="https://github.com/causaai/causa-backend/issues/new?labels=bug">Report Bug</a>
    &middot;
    <a href="https://github.com/causaai/causa-backend/issues/new?labels=enhancement">Request Feature</a>
  </p>
  <br />

[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![License][license-shield]][license-url]

</div>

---

<!-- TABLE OF CONTENTS -->
**Table of Contents**

1. [About The Project](#about-the-project)
   - [Built With](#built-with)
   - [Key Features](#key-features)
2. [Why Causa?](#why-causa)
3. [Getting Started](#getting-started)
   - [Prerequisites](#prerequisites)
   - [Running in Dev Mode](#running-in-dev-mode)
   - [Packaging](#packaging)
4. [Contributing](#contributing)
5. [License](#license)
6. [Contact](#contact)

---

## About The Project
[Causa](https://github.com/causaai/causa-backend) is an AI-powered root cause analysis agent that helps engineering teams reduce Mean Time to Resolution (MTTR) for Java memory issues on Kubernetes and virtual machines.

When a Prometheus [Alertmanager](https://prometheus.io/docs/alerting/latest/alertmanager/) alert is triggered, Causa automatically collects production context—including application logs, pod health, Kubernetes events, and infrastructure insights via MCP servers—and uses AI to identify the root cause. Within minutes, it delivers prioritized remediation steps, covering both immediate mitigation and long-term fixes, so engineers can resolve incidents faster with less manual investigation.

Instead of engineers manually collecting data from multiple tools, Causa delivers a complete diagnosis and recommended next actions, enabling teams to resolve production incidents faster, minimize downtime, and improve service reliability.

### Built With

[![Quarkus][quarkus-shield]][quarkus-url] [![Java][java-shield]][java-url] [![PostgreSQL][postgres-shield]][postgres-url] [![LangChain4J][lc4j-shield]][lc4j-url]

## Key Features

- 🚨 **Automated Incident Response** – Responds instantly to Prometheus Alertmanager memory alerts with no manual intervention.

- 🔍 **AI-Powered Root Cause Analysis** – Correlates logs, metrics, Kubernetes events, and runtime information to identify the root cause.

- ⚡ **Reduce MTTR** – Delivers prioritized remediation steps, including immediate mitigation and long-term fixes, within minutes.

- 🧠 **Context-Aware Diagnostics** – Automatically gathers production context from Kubernetes, JVM, [Kruize Recommendations](https://github.com/kruize/autotune/) and infrastructure through MCP servers.

- ☸️ **Kubernetes & VM Support (Planned)** – Diagnoses Java memory issues across both Kubernetes clusters and traditional virtual machine deployments (VM Support is planned for next release).

- 🤖 **Flexible LLM Providers** – Supports multiple AI backends including Anthropic Claude, Vertex AI Claude, with extensible support for IBM Bob (Planned) and Ollama (Planned).

- ⚙️ **Dynamic Configuration** – Update operational settings securely without restarting the application.


<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## Why Causa?

| Manual Incident Response | With Causa 🚀 |
|---------------------------|---------------|
| Engineer waits for an alert and starts investigating manually. | Automatically responds to Prometheus Alertmanager alerts. |
| Switches between Grafana, kubectl, logs, JFR, and monitoring dashboards. | Collects logs, Kubernetes events, JFR (Planned), JVM metrics (Planned), and infrastructure context automatically. |
| Correlates data across multiple tools manually. | AI correlates all production context into a single diagnosis. |
| Requires JVM and Kubernetes expertise to identify the root cause. | AI identifies the most likely root cause with supporting evidence. |
| Engineers spend hours debugging memory leaks, OOMKills, and GC issues. | Root cause analysis is completed in minutes, reducing MTTR. |
| Team decides what to do next under pressure. | Provides prioritized remediation steps with immediate mitigation and permanent fixes. |
| Knowledge stays with individual engineers. | Every incident follows a consistent, repeatable investigation workflow. |
| High operational overhead during production incidents. | Faster incident resolution, less downtime, and improved reliability. |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## Getting Started

### Prerequisites

| Requirement | Minimum Version | Notes |
|---|---|---|
| Java (JDK) | 21 | OpenJDK or compatible |
| Apache Maven | 3.9+ | or use `./mvnw` |
| Docker / Podman | any recent | for container image builds |
| PostgreSQL | 14+ | must have **[pgvector](https://github.com/pgvector/pgvector)** installed |
| [Kubernetes MCP Server](https://github.com/containers/kubernetes-mcp-server) | latest | pod status, logs, events via JSON-RPC 2.0 |
| [Kruize MCP Server](https://github.com/kruize/kruize-mcp-server) | latest | resource cost & performance recommendations |
| `kubectl` + `kustomize` | 1.27+ / 5+ | for Kubernetes deployment |

### Running in Dev Mode

Dev mode enables live-code reload and autowires **Quarkus Dev Services** — Postgres instance must be deployed to run application in dev mode. 

```bash
./mvnw compile quarkus:dev
# or
quarkus dev
```

Dev UI is available at [`http://localhost:8080/q/dev/`](http://localhost:8080/q/dev/).

Health Endpoint is available at [`http://localhost:8080/api/v1/healthz`](http://localhost:8080/api/v1/healthz).

Alert Webhook Endpoint is available at [`http://localhost:8080/api/v1/healthz`](http://localhost:8080/api/v1/webhooks/alerts).

> The dev profile defaults to `dev_password` for the DB password and `vertex-ai-anthropic` for the LLM provider. Override via environment variables before starting in application.yaml

### Packaging

```bash
# Fast-JAR (default)
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar

# Über-JAR
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar

```

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

---

## Contact

Project Link: [https://github.com/causaai/causa-backend](https://github.com/causaai/causa-backend)

<p align="right">(<a href="#readme-top">back to top</a>)</p>


<p align="center">
  Built with ❤️ by the <strong>Causa Team</strong>
</p>

<!-- MARKDOWN LINKS & IMAGES -->
[contributors-shield]: https://img.shields.io/github/contributors/causaai/causa-backend.svg?style=for-the-badge
[contributors-url]: https://github.com/causaai/causa-backend/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/causaai/causa-backend.svg?style=for-the-badge
[forks-url]: https://github.com/causaai/causa-backend/network/members
[stars-shield]: https://img.shields.io/github/stars/causaai/causa-backend.svg?style=for-the-badge
[stars-url]: https://github.com/causaai/causa-backend/stargazers
[issues-shield]: https://img.shields.io/github/issues/causaai/causa-backend.svg?style=for-the-badge
[issues-url]: https://github.com/causaai/causa-backend/issues
[license-shield]: https://img.shields.io/github/license/causaai/causa-backend.svg?style=for-the-badge
[license-url]: https://github.com/causaai/causa-backend/blob/main/LICENSE

[quarkus-shield]: https://img.shields.io/badge/Quarkus-3.36.1-4695EB?style=for-the-badge&logo=quarkus&logoColor=white
[quarkus-url]: https://quarkus.io/
[java-shield]: https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white
[java-url]: https://adoptium.net/
[postgres-shield]: https://img.shields.io/badge/PostgreSQL-pgvector-336791?style=for-the-badge&logo=postgresql&logoColor=white
[postgres-url]: https://github.com/pgvector/pgvector
[lc4j-shield]: https://img.shields.io/badge/LangChain4J-1.15.1-00897B?style=for-the-badge
[lc4j-url]: https://docs.langchain4j.dev/