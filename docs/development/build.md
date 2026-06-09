# Building and Pushing Docker Images

This guide explains how to build and push multi-architecture Docker images for causa-backend.

## Overview

The `scripts/development/build_and_push.sh` script provides a comprehensive solution for building and pushing Docker images with support for:

- ✅ Custom image names and tags
- ✅ Multi-architecture builds (AMD64 and ARM64)
- ✅ Environment variable configuration
- ✅ Flexible build options
- ✅ Registry authentication support

## Prerequisites

1. **Docker** or **Podman** installed and running
2. **Maven** (uses Maven wrapper included in project)
3. **Authentication** to your container registry (if pushing images)
4. **Quarkus Container Image Jib Extension** (already configured in `pom.xml`)

## Quick Start

```bash
# Build image locally (no push)
./scripts/dev/build_and_push.sh

# Build and push with custom tag
./scripts/dev/build_and_push.sh -t v1.0.0 -p true

# Build with custom full image name
./scripts/dev/build_and_push.sh -i quay.io/myorg/causa-backend:latest -b true -p true
```

## Usage

```bash
./scripts/dev/build_and_push.sh [OPTIONS]
```

### Command-Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `-i IMAGE_NAME` | Full image name (registry/repository:tag) | - |
| `-r REGISTRY` | Container registry | `quay.io` |
| `-n REPO_NAME` | Repository name | `causa/causa-backend` |
| `-t TAG` | Image tag (used if -i not provided) | `latest` |
| `-b BUILD` | Build image (true/false) | `true` |
| `-p PUSH` | Push image (true/false) | `false` |
| `-l PLATFORMS` | Target platforms | `linux/amd64,linux/arm64` |
| `-c CLEAN` | Run clean build (true/false) | `true` |
| `-s SKIP_TESTS` | Skip tests during build (true/false) | `false` |
| `-h` | Show help message | - |

### Environment Variables

Alternative to command-line flags (flags take precedence):

| Variable | Description |
|----------|-------------|
| `IMAGE_NAME` | Full image name |
| `REGISTRY` | Container registry |
| `REPO_NAME` | Repository name |
| `IMAGE_TAG` | Image tag |
| `BUILD_IMAGE` | Build image (true/false) |
| `PUSH_IMAGE` | Push image (true/false) |
| `PLATFORMS` | Target platforms |
| `CLEAN_BUILD` | Clean build (true/false) |
| `SKIP_TESTS` | Skip tests (true/false) |

## Examples


### Build and Push to Registry

```bash
# Build with default settings
./scripts/dev/build_and_push.sh

# Build with custom tag for testing
./scripts/dev/build_and_push.sh -t dev-$(date +%Y%m%d)

# Fast build (skip tests, no clean)
./scripts/dev/build_and_push.sh -c false -s true -t dev

# Build and push with version tag
./scripts/dev/build_and_push.sh -t v1.0.0 -p true

# Build and push with custom full image name
./scripts/dev/build_and_push.sh -i quay.io/myorg/causa-backend:1.0.0 -p true

# Build and push to Docker Hub
./scripts/dev/build_and_push.sh -r docker.io -n myusername/causa -t latest -p true
```

### Architecture-Specific Builds

```bash
# Build for AMD64 only
./scripts/dev/build_and_push.sh -t amd64-only -l linux/amd64

# Build for ARM64 only (e.g., Apple Silicon)
./scripts/dev/build_and_push.sh -t arm64-only -l linux/arm64

# Build for both (default)
./scripts/dev/build_and_push.sh -t multi-arch -l linux/amd64,linux/arm64
```

### Using Environment Variables

```bash
# Set environment variables
export IMAGE_TAG=v2.0.0
export PUSH_IMAGE=true
export PLATFORMS=linux/amd64

# Run script
./scripts/dev/build_and_push.sh

# Or inline
IMAGE_TAG=v2.0.0 PUSH_IMAGE=true ./scripts/dev/build_and_push.sh
```

### CI/CD Pipeline

```bash
# GitHub Actions / GitLab CI example
./scripts/dev/build_and_push.sh \
  -i quay.io/rh-ee-shesaxen/causa-backend:${CI_COMMIT_TAG} \
  -b true \
  -p true \
  -l linux/amd64,linux/arm64 \
  -s false
```

## Registry Authentication

Before pushing images, authenticate with your container registry.

### Quay.io

```bash
docker login quay.io
# Enter username and password when prompted

# Or with token
echo $QUAY_TOKEN | docker login quay.io -u $QUAY_USER --password-stdin
```

### Docker Hub

```bash
docker login docker.io
# Or
docker login
```

### GitHub Container Registry

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin
```

### Using Podman

```bash
podman login quay.io
podman login docker.io
podman login ghcr.io
```

## Build Process Details

The script performs the following steps:

1. **Validation**: Checks for required files (`pom.xml`, `mvnw`)
2. **Configuration**: Processes command-line flags and environment variables
3. **Maven Build**: Executes Maven with Quarkus container image plugin
4. **Image Build**: Uses Jib to build multi-architecture images
5. **Push** (optional): Pushes images to the specified registry

### Under the Hood

The script uses:
- **Quarkus Container Image Jib Extension** for building images
- **Maven Wrapper** (`./mvnw`) for consistent builds
- **Jib** for multi-architecture support without Docker daemon
- **Quarkus properties** for configuration

Example Maven command generated:
```bash
./mvnw clean package \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.image=quay.io/rh-ee-shesaxen/causa-backend:v1.0.0 \
  -Dquarkus.container-image.push=true \
  -Dquarkus.jib.platforms=linux/amd64,linux/arm64
```



## Best Practices

### Development

- Use descriptive tags: `dev-YYYYMMDD`, `feature-name`, etc.
- Don't push development images to production registries
- Use `-s true` to skip tests for faster iteration

### Staging/Production

- Use semantic versioning: `v1.0.0`, `v1.0.1`, etc.
- Always run tests before pushing (`-s false`)
- Build for both architectures (`-l linux/amd64,linux/arm64`)

### CI/CD

- Use commit SHA or tag as image tag
- Always authenticate before pushing
- Store registry credentials securely
- Build and push only on main/release branches

## Configuration Files

### pom.xml

The `pom.xml` includes the Quarkus Container Image Jib extension:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-container-image-jib</artifactId>
</dependency>
```

### application.yml

You can also configure container image settings in `src/main/resources/application.yml`:

```yaml
quarkus:
  container-image:
    registry: quay.io
    group: causa
    name: causa-backend
    tag: latest
```

## Related Documentation

- [Quarkus Container Images Guide](https://quarkus.io/guides/container-image)
- [Jib Documentation](https://github.com/GoogleContainerTools/jib)

## Support

For issues or questions: Open an issue in the project repository