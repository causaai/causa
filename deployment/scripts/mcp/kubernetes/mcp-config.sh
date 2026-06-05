#!/bin/bash

##############################################################################
# MCP Server Configuration File
# 
# IMPORTANT: Update these values with your OpenShift cluster information
# before running the deployment or test scripts.
#
# This file should NOT be committed with real credentials.
# Add to .gitignore if it contains sensitive information.
##############################################################################

# OpenShift Cluster Configuration
# Replace with your actual cluster API URL
OPENSHIFT_API="https://api.<your-cluster-domain>:6443"

# OpenShift Authentication Token
# SECURITY: Never commit real tokens to git!
# Generate a token from your OpenShift console or use: oc create token
OPENSHIFT_TOKEN="${OPENSHIFT_TOKEN:-}"

# OpenShift Console URL (for reference/documentation)
OPENSHIFT_CONSOLE="https://console-openshift-console.apps.<your-cluster-domain>"

# Target Namespace
# The namespace where MCP server will be deployed
NAMESPACE="diagnostics-tool"

# TLS Verification
# Set to "true" to skip TLS certificate verification (NOT recommended for production)
# Only use this for local/development clusters without proper CA certificates
# Default: false (TLS verification enabled)
SKIP_TLS_VERIFY="${SKIP_TLS_VERIFY:-false}"

##############################################################################
# Usage Instructions:
#
# 1. Copy this file to create your local configuration:
#    cp mcp-config.sh mcp-config.local.sh
#
# 2. Update mcp-config.local.sh with your cluster values
#
# 3. Source the config before running scripts:
#    source mcp-config.local.sh
#    ./deploy-mcp-server.sh
#
# OR set environment variable:
#    export OPENSHIFT_TOKEN="your-token-here"
#    ./deploy-mcp-server.sh
##############################################################################
