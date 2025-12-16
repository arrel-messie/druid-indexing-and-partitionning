#!/bin/bash

# Script to run the JSON Transaction Producer
# This producer sends JSON formatted transactions to Kafka

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Transaction JSON Producer${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed or not in PATH${NC}"
    exit 1
fi

# Check if the JAR exists, if not build it
JAR_FILE="target/kafka-producer-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}JAR file not found. Building project...${NC}"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed!${NC}"
        exit 1
    fi
    echo -e "${GREEN}Build successful!${NC}"
fi

# Check if application-json.properties exists
if [ ! -f "src/main/resources/application-json.properties" ]; then
    echo -e "${YELLOW}Warning: application-json.properties not found. Using default configuration.${NC}"
fi

# Run the JSON producer
echo -e "${GREEN}Starting Transaction JSON Producer...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""

# Use the shaded JAR which includes all dependencies
java -cp "$JAR_FILE" \
    com.kafka.injector.producer.TransactionJsonProducer

