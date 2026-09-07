# Getting Started - Development Guide

This guide will help you set up your development environment, build, compile, and run the Causa application.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Initial Setup](#initial-setup)
- [Building the Application](#building-the-application)
- [Running the Application](#running-the-application)
- [Development Workflow](#development-workflow)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software

| Software | Minimum Version | Recommended Version | Purpose |
|----------|----------------|---------------------|---------|
| **Java JDK** | 25 | 25 | Runtime and compilation |
| **Maven** | 3.9.x | 3.9.x | Build tool (wrapper included) |
| **Docker** or **Podman** | 20.10 | Latest | Container builds |
| **Git** | 2.30 | Latest | Version control |

### Optional Tools

- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions
- **curl** or **httpie**: For testing API endpoints
- **jq**: For JSON formatting
- **kubectl**: For Kubernetes deployments


## Initial Setup

### 1. Fork the Repository

Fork the repository to your personal GitHub account.

### 2. Clone the Forked Repository

```bash
# Clone the repository
git clone https://github.com/your-org/causa-backend.git
cd causa-backend

# Verify you're in the correct directory
ls -la
# Should see: pom.xml, mvnw, src/, docs/, etc.
```
### 3. Configure Environment Variables (Optional - EASY Development)

Create a `.env` file in the project root for local configuration:

```bash
# .env (example)
QUARKUS_HTTP_PORT=8080
QUARKUS_LOG_LEVEL=DEBUG

# LLM Configuration (if using external providers)
LANGCHAIN4J_OPENAI_API_KEY=your-api-key-here
LANGCHAIN4J_ANTHROPIC_API_KEY=your-api-key-here
```

**Note:** Never commit `.env` files with sensitive data to version control.

## Building the Application

### Quick Build

```bash
# Clean and build (skips tests)
./mvnw clean package -DskipTests

# Build output location:
# target/causa-backend-0.0.1-SNAPSHOT-runner.jar
```

### Full Build with Tests

```bash
# Clean, compile, test, and package
./mvnw clean package

# This will:
# 1. Clean previous builds
# 2. Compile source code
# 3. Run unit tests
# 4. Run integration tests
# 5. Package the application
```

### Build Options

```bash
# Fast build (no clean, skip tests)
./mvnw package -DskipTests

# Build with specific profile
./mvnw clean package -Pnative

# Build with verbose output
./mvnw clean package -X

# Build offline (use cached dependencies)
./mvnw clean package -o

# Update dependencies
./mvnw clean package -U
```

### Build Artifacts

After a successful build, you'll find:

```
target/
├── causa-backend-0.0.1-SNAPSHOT.jar          # Thin JAR (requires dependencies)
├── causa-backend-0.0.1-SNAPSHOT-runner.jar   # Runnable JAR (Quarkus fast-jar)
├── quarkus-app/                               # Quarkus application directory
│   ├── app/                                   # Application classes
│   ├── lib/                                   # Dependencies
│   └── quarkus-run.jar                        # Main JAR
├── classes/                                   # Compiled classes
└── test-classes/                              # Compiled test classes
```

## Running the Application

### Development Mode (Recommended for Development)

Quarkus Dev Mode provides hot reload, continuous testing, and Dev UI.

```bash
# Start in development mode
./mvnw quarkus:dev

# Application will start on http://localhost:8080
# Dev UI available at http://localhost:8080/q/dev
```

**Dev Mode Features:**
- 🔥 **Hot Reload**: Code changes are automatically detected and reloaded
- 🧪 **Continuous Testing**: Tests run automatically on code changes
- 🎨 **Dev UI**: Web interface for configuration, health checks, and more
- 📊 **Live Metrics**: Real-time application metrics

**Dev Mode Shortcuts:**
- Press `r` - Re-run all tests
- Press `f` - Re-run failed tests
- Press `b` - Toggle 'broken only' mode
- Press `v` - Print failures from last test run
- Press `p` - Pause tests
- Press `o` - Toggle test output
- Press `l` - Toggle live reload
- Press `s` - Force restart
- Press `h` - Display help
- Press `q` - Quit

### Running with Docker

```bash
# Build Docker image
./mvnw clean package -Dquarkus.container-image.build=true

# Run container
docker run -i --rm -p 8080:8080 \
  quay.io/rh-ee-shesaxen/causa-backend:0.0.1-SNAPSHOT

# Run with environment variables
docker run -i --rm -p 8080:8080 \
  -e QUARKUS_LOG_LEVEL=DEBUG \
  quay.io/rh-ee-shesaxen/causa-backend:0.0.1-SNAPSHOT
```

For detailed Docker build instructions, see [Build Guide](./build.md).

### Verifying the Application

Once the application is running, verify it's working:

```bash
# Check health endpoint
curl http://localhost:8080/q/health

# Expected output:
# {
#   "status": "UP",
#   "checks": [...]
# }

# Check liveness
curl http://localhost:8080/q/health/live

# Check readiness
curl http://localhost:8080/q/health/ready

# View OpenAPI/Swagger UI
# Open in browser: http://localhost:8080/q/swagger-ui
```

## Development Workflow

### Typical Development Cycle

1. **Start Dev Mode**
   ```bash
   ./mvnw quarkus:dev
   ```

2. **Make Code Changes**
   - Edit Java files in `src/main/java/`
   - Changes are automatically detected and reloaded

3. **Test Changes**
   - Access endpoints via browser or curl
   - Check Dev UI at http://localhost:8080/q/dev
   - Run tests with `./mvnw test`

4. **Commit Changes**
   ```bash
   git add .
   git commit -m "Description of changes"
   git push
   ```

### Project Structure

```
causa-backend/
├── src/
│   ├── main/
│   │   ├── java/com/causa/
│   │   │   ├── api/              # REST API controllers
│   │   │   ├── common/           # Common utilities, constants
│   │   │   ├── config/           # Configuration classes
│   │   │   ├── core/             # Core business logic
│   │   │   ├── infrastructure/   # Infrastructure adapters
│   │   │   ├── llm/              # LLM integration
│   │   │   ├── mcp/              # MCP integration
│   │   │   ├── notification/     # Notification services
│   │   │   ├── rag/              # RAG implementation
│   │   │   ├── security/         # Security components
│   │   │   └── validation/       # Validation logic
│   │   └── resources/
│   │       ├── application.yml   # Application configuration
│   │       └── db/migration/     # Database migrations
│   └── test/
│       └── java/com/causa/       # Test classes
├── docs/                         # Documentation
├── scripts/                      # Build and deployment scripts
├── deployment/                   # Kubernetes manifests
├── pom.xml                       # Maven configuration
└── mvnw                          # Maven wrapper
```

### Adding New Features

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/my-new-feature
   ```

2. **Implement Feature**
   - Add classes in appropriate packages
   - Follow existing code structure and patterns
   - Add tests for new functionality

3. **Test Locally**
   ```bash
   ./mvnw clean test
   ./mvnw quarkus:dev
   ```

4. **Build and Verify**
   ```bash
   ./mvnw clean package
   ```

5. **Build Custom Image with Your Feature**
   - Build image in your quay repository. 
   - Test the changes in kubernetes environments

5. **Submit Pull Request**
   - Push branch to remote
   - Create PR with description against main
   - Add your custom quay image for faster testing and reviews
   - Wait for code review

## Testing

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=HealthControllerIT

# Run tests with coverage
./mvnw clean test jacoco:report

# Run tests in continuous mode (dev mode)
./mvnw quarkus:dev
# Then press 'r' to run tests
```

### Writing Tests

**Example Unit Test:**
```java
package com.causa.core.services;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MyServiceTest {
    
    @Test
    void testMyMethod() {
        // Arrange
        MyService service = new MyService();
        
        // Act
        String result = service.myMethod();
        
        // Assert
        assertEquals("expected", result);
    }
}
```

## Quick Reference

### Essential Commands

```bash
# Development
./mvnw quarkus:dev                    # Start dev mode
./mvnw clean package                  # Build with tests
./mvnw clean package -DskipTests      # Build without tests

# Testing
./mvnw test                           # Run tests
./mvnw verify                         # Run integration tests

# Running
java -jar target/quarkus-app/quarkus-run.jar

# Docker
./mvnw clean package -Dquarkus.container-image.build=true
docker run -i --rm -p 8080:8080 quay.io/rh-ee-shesaxen/causa-backend:latest

# Health Checks
curl http://localhost:8080/q/health
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

### Useful URLs (when running)

- Application: http://localhost:8080
- Dev UI: http://localhost:8080/q/dev
- Health: http://localhost:8080/q/health
- Swagger UI: http://localhost:8080/q/swagger-ui
- OpenAPI: http://localhost:8080/q/openapi

---

**Happy Coding! 🚀**