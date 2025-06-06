#!/bin/bash

# Stop deployment simulation script
echo "Starting deployment stop process..."
echo "Stop ID: $(date +%s)"

# Simulate different stop stages
echo "Stage 1: Gracefully shutting down application..."
sleep 1
echo "Sending SIGTERM to application processes..."
echo "Waiting for graceful shutdown..."
echo "Application stopped gracefully!"

echo "Stage 2: Updating load balancer..."
sleep 1
echo "Removing server from load balancer pool..."
echo "Draining existing connections..."
echo "Load balancer updated!"

echo "Stage 3: Cleaning up resources..."
echo "Stopping background services..."
echo "Releasing file locks..."
echo "Cleaning temporary files..."
echo "Resources cleaned up!"

echo "Stage 4: Updating deployment registry..."
echo "Marking deployment as stopped..."
echo "Notifying monitoring systems..."
echo "Registry updated!"

echo "Deployment stopped successfully!"
echo "All services have been shut down"
echo "Stop duration: 3 seconds"
echo "Application is now offline"

exit 0
