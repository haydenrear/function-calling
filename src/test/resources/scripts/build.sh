#!/bin/bash

# Simple build simulation script
echo "Starting build process..."
echo "Build ID: $(date +%s)"

# Simulate different build stages
echo "Stage 1: Compiling source code..."
sleep 1
echo "Compiling main.java..."
echo "Compiling utils.java..."
echo "Compilation successful!"

echo "Stage 2: Running tests..."
sleep 1
echo "Running unit tests..."
echo "✓ TestClass1: 5 tests passed"
echo "✓ TestClass2: 3 tests passed"
echo "All tests passed!"

echo "Stage 3: Packaging artifacts..."
sleep 1
echo "Creating JAR file..."
echo "Creating artifact: target/myapp-1.0.0.jar"

# Create a fake artifact file if target directory exists
if [ -d "target" ]; then
    echo "Build artifact created at $(date)" > target/myapp-1.0.0.jar
    echo "Artifact saved to target/myapp-1.0.0.jar"
fi

echo "Build completed successfully!"
echo "Build duration: 3 seconds"
echo "Artifacts ready for deployment"

exit 0
