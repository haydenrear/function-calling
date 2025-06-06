#!/bin/bash

# Failing build simulation script
echo "Starting build process..."
echo "Build ID: $(date +%s)"

# Simulate different build stages
echo "Stage 1: Compiling source code..."
sleep 1
echo "Compiling main.java..."
echo "Compiling utils.java..."
echo "ERROR: Compilation failed in utils.java:45"
echo "ERROR: Cannot find symbol 'undefinedMethod'"
echo "Compilation failed!"

echo "Stage 2: Build failed, skipping tests..."

echo "Build failed with errors!"
echo "Build duration: 1 seconds"

exit 1
