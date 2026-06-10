# PostgreSQL 17 with pgvector extension
# Base image: CloudNativePG official PostgreSQL 17 image
FROM ghcr.io/cloudnative-pg/postgresql:17

# Metadata
LABEL maintainer="causa-backend"
LABEL description="CloudNativePG PostgreSQL 17 with pgvector extension for vector similarity search"
LABEL version="17-pgvector"

# Switch to root to install packages
USER root

# Install pgvector extension from package
RUN set -xe && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        postgresql-17-pgvector && \
    # Cleanup
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Verify pgvector installation (Debian paths)
RUN echo "Verifying pgvector installation..." && \
    ls -la /usr/share/postgresql/17/extension/vector* && \
    ls -la /usr/lib/postgresql/17/lib/vector.so

# Fix permissions for OpenShift compatibility (random UID support)
RUN chgrp -R 0 /var/lib/postgresql /var/run/postgresql && \
    chmod -R g=u /var/lib/postgresql /var/run/postgresql

# Switch back to non-root user for security
USER postgres

# Expose PostgreSQL port
EXPOSE 5432


