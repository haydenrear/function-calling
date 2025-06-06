#!/bin/bash

# Simple HTTP health check server simulation
PORT=${1:-8080}
ENDPOINT=${2:-/health}

echo "Starting health check server on port $PORT..."
echo "Health endpoint: http://localhost:$PORT$ENDPOINT"

# Create a simple HTTP server using netcat if available, otherwise use Python
if command -v nc >/dev/null 2>&1; then
    echo "Using netcat for HTTP server"
    while true; do
        echo "HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 27
Connection: close

{\"status\":\"UP\",\"healthy\":true}" | nc -l -p $PORT
        echo "Health check request served at $(date)"
        sleep 0.1
    done
elif command -v python3 >/dev/null 2>&1; then
    echo "Using Python HTTP server"
    python3 -c "
import http.server
import socketserver
import json
from urllib.parse import urlparse

class HealthHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '$ENDPOINT':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            response = json.dumps({'status': 'UP', 'healthy': True})
            self.wfile.write(response.encode())
            print(f'Health check request served at {self.date_time_string()}')
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass  # Suppress default logging

with socketserver.TCPServer(('', $PORT), HealthHandler) as httpd:
    print(f'Health server running on port $PORT')
    httpd.serve_forever()
"
else
    echo "ERROR: Neither netcat nor python3 available for HTTP server"
    exit 1
fi
