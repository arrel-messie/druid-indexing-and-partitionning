#!/bin/bash

# Script to build and run the Kafka Transaction Producer

echo "Building Kafka Transaction Producer..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful! Starting producer..."
    echo ""
    java -jar target/kafka-producer-1.0.0.jar
else
    echo "Build failed. Please check the errors above."
    exit 1
fi



