#!/bin/bash

# ---------------------------------------------------------------------------
# GWFramework - Build and Run Script
# ---------------------------------------------------------------------------
# This script will:
#   1. Build the whole project
#   2. Generate config.json
#   3. Package the distribution (zip, tar)
#   4. Run the built game jar
# ---------------------------------------------------------------------------

# Fail fast on errors
set -e

DEFAULT_CONFIG="example" # <-- TODO: Change this to default configSet

echo "🔵 Building and Running GWFramework..."
echo

# Allow passing configSet as first parameter, otherwise use default
CONFIG_SET="${1:-$DEFAULT_CONFIG}"

# Step 1: Build everything
echo "🛠️ Running Gradle build for configSet=${CONFIG_SET}..."
./gradlew clean build -PconfigSet="${CONFIG_SET}"

# Step 2: Run the built game
echo
echo "🚀 Running the built game..."
./gradlew run -PconfigSet="${CONFIG_SET}"

echo
echo "✅ Build and Run complete!"
