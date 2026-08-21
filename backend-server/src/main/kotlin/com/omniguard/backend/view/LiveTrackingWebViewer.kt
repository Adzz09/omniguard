package com.omniguard.backend.view

import com.omniguard.backend.model.TrackingSessionState

/**
 * Embedded HTML5/JS Web Viewer template utilizing Leaflet and OpenStreetMap.
 * Allows trusted contacts to view live location, speed, breadcrumb trail, and emergency state without installing an app (FR-03).
 */
object LiveTrackingWebViewer {

    fun renderHtml(session: TrackingSessionState, host: String = "localhost:8080"): String {
        val initialLat = session.currentLatitude
        val initialLng = session.currentLongitude
        val initialBreadcrumbsJson = session.breadcrumbs.joinToString(",", "[", "]") {
            "[${it.latitude}, ${it.longitude}]"
        }

        val roleIcon = when (session.userRole.name) {
            "BIKER" -> "🏍️"
            "STUDENT" -> "🎓"
            "ELDERLY" -> "👵"
            else -> "🛡️"
        }

        val statusColor = if (session.isCancelled) "#10B981" else "#EF4444"
        val statusText = if (session.isCancelled) "RESOLVED / CANCELLED" else session.status.name.replace("_", " ")

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>OmniGuard Live Escort - Session ${session.sessionId}</title>
    <!-- Leaflet CSS -->
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
    
    <style>
        :root {
            --bg-dark: #0B0F19;
            --surface-dark: #151C2C;
            --surface-border: #232E47;
            --accent-red: #EF4444;
            --accent-green: #10B981;
            --accent-blue: #3B82F6;
            --text-primary: #F3F4F6;
            --text-secondary: #9CA3AF;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: var(--bg-dark);
            color: var(--text-primary);
            display: flex;
            flex-direction: column;
            height: 100vh;
            overflow: hidden;
        }

        header {
            background-color: var(--surface-dark);
            border-bottom: 1px solid var(--surface-border);
            padding: 14px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            z-index: 1000;
        }

        .brand-container {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .brand-logo {
            background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);
            width: 38px;
            height: 38px;
            border-radius: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 20px;
            box-shadow: 0 0 15px rgba(239, 68, 68, 0.4);
        }

        .brand-title {
            font-size: 1.15rem;
            font-weight: 800;
            letter-spacing: -0.5px;
            color: #FFFFFF;
        }

        .brand-subtitle {
            font-size: 0.75rem;
            color: var(--text-secondary);
            font-family: 'JetBrains Mono', monospace;
        }

        .status-badge {
            display: flex;
            align-items: center;
            gap: 8px;
            background-color: rgba(239, 68, 68, 0.15);
            border: 1px solid var(--accent-red);
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 0.82rem;
            font-weight: 700;
            color: var(--accent-red);
            transition: all 0.3s ease;
        }

        .pulse-dot {
            width: 8px;
            height: 8px;
            background-color: var(--accent-red);
            border-radius: 50%;
            animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
        }

        #main-container {
            display: flex;
            flex: 1;
            position: relative;
        }

        #map {
            flex: 1;
            height: 100%;
            background: #111827;
        }

        #telemetry-panel {
            position: absolute;
            bottom: 24px;
            left: 24px;
            right: 24px;
            max-width: 540px;
            background: rgba(21, 28, 44, 0.92);
            backdrop-filter: blur(12px);
            border: 1px solid var(--surface-border);
            border-radius: 16px;
            padding: 18px 20px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
            z-index: 1000;
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 12px;
        }

        .telemetry-item {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .telemetry-label {
            font-size: 0.7rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--text-secondary);
            font-weight: 600;
        }

        .telemetry-value {
            font-size: 1.05rem;
            font-weight: 700;
            color: #FFFFFF;
            font-family: 'JetBrains Mono', monospace;
        }

        .alert-banner {
            position: absolute;
            top: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(239, 68, 68, 0.95);
            color: white;
            padding: 10px 24px;
            border-radius: 30px;
            font-weight: 700;
            font-size: 0.9rem;
            z-index: 1000;
            box-shadow: 0 8px 24px rgba(239, 68, 68, 0.4);
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .user-chip {
            display: flex;
            align-items: center;
            gap: 8px;
            background: rgba(255, 255, 255, 0.06);
            padding: 4px 10px;
            border-radius: 8px;
            font-size: 0.8rem;
        }

        /* Custom pulsing marker */
        .pulsing-marker {
            width: 24px;
            height: 24px;
            background: #EF4444;
            border: 3px solid #FFFFFF;
            border-radius: 50%;
            box-shadow: 0 0 12px rgba(239, 68, 68, 0.8);
        }

        @media (max-width: 640px) {
            #telemetry-panel {
                grid-template-columns: repeat(2, 1fr);
                left: 12px;
                right: 12px;
                bottom: 12px;
            }
        }
    </style>
</head>
<body>
    <header>
        <div class="brand-container">
            <div class="brand-logo">🛡️</div>
            <div>
                <div class="brand-title">OmniGuard Live Escort</div>
                <div class="brand-subtitle">Session: <span id="session-id-display">${session.sessionId}</span></div>
            </div>
        </div>

        <div style="display: flex; align-items: center; gap: 12px;">
            <div class="user-chip">
                <span>$roleIcon</span>
                <span>${session.userRole.title} (${session.userId})</span>
            </div>
            <div id="status-badge" class="status-badge" style="border-color: $statusColor; color: $statusColor; background-color: ${if (session.isCancelled) "rgba(16, 185, 129, 0.15)" else "rgba(239, 68, 68, 0.15)"}">
                <div class="pulse-dot" style="background-color: $statusColor;"></div>
                <span id="status-text">$statusText</span>
            </div>
        </div>
    </header>

    <div id="main-container">
        <div id="alert-banner" class="alert-banner" style="display: ${if (session.isCancelled) "none" else "flex"};">
            <span>🚨</span>
            <span id="alert-message">Active Emergency Broadcast in Progress</span>
        </div>

        <div id="map"></div>

        <div id="telemetry-panel">
            <div class="telemetry-item">
                <span class="telemetry-label">Latitude</span>
                <span class="telemetry-value" id="val-lat">${"%.5f".format(initialLat)}</span>
            </div>
            <div class="telemetry-item">
                <span class="telemetry-label">Longitude</span>
                <span class="telemetry-value" id="val-lng">${"%.5f".format(initialLng)}</span>
            </div>
            <div class="telemetry-item">
                <span class="telemetry-label">Speed</span>
                <span class="telemetry-value" id="val-speed">${session.speedKmh} km/h</span>
            </div>
            <div class="telemetry-item">
                <span class="telemetry-label">Battery</span>
                <span class="telemetry-value" id="val-battery">${session.batteryPercent}%</span>
            </div>
        </div>
    </div>

    <!-- Leaflet JS -->
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <script>
        const sessionId = "${session.sessionId}";
        let currentLat = $initialLat;
        let currentLng = $initialLng;
        let breadcrumbs = $initialBreadcrumbsJson;

        // Initialize Map
        const map = L.map('map', {
            zoomControl: true,
            attributionControl: false
        }).setView([currentLat, currentLng], 16);

        // OpenStreetMap Dark Theme Tiles (CartoDB Dark Matter)
        L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
            maxZoom: 19,
            subdomains: 'abcd'
        }).addTo(map);

        // Custom pulsing marker icon
        const pulsingIcon = L.divIcon({
            className: 'pulsing-marker',
            iconSize: [20, 20],
            iconAnchor: [10, 10]
        });

        const userMarker = L.marker([currentLat, currentLng], { icon: pulsingIcon }).addTo(map);
        userMarker.bindPopup("<b>${session.userId}</b><br>${session.userRole.title}").openPopup();

        // Accuracy Circle
        const accuracyCircle = L.circle([currentLat, currentLng], {
            radius: ${session.currentAccuracyMeters},
            color: '#EF4444',
            fillColor: '#EF4444',
            fillOpacity: 0.15,
            weight: 1
        }).addTo(map);

        // Polyline Breadcrumb Trail
        const polyline = L.polyline(breadcrumbs, {
            color: '#3B82F6',
            weight: 4,
            opacity: 0.8,
            dashArray: '6, 8'
        }).addTo(map);

        // Real-Time WebSocket Connection
        function connectWebSocket() {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = protocol + '//' + window.location.host + '/api/v1/tracking/' + sessionId;
            console.log('Connecting to WebSocket:', wsUrl);

            const ws = new WebSocket(wsUrl);

            ws.onmessage = function(event) {
                try {
                    const data = JSON.parse(event.data);
                    console.log('Received WebSocket frame:', data);

                    if (data.session) {
                        const s = data.session;
                        currentLat = s.currentLatitude;
                        currentLng = s.currentLongitude;

                        // Update Marker & Map View
                        userMarker.setLatLng([currentLat, currentLng]);
                        accuracyCircle.setLatLng([currentLat, currentLng]);
                        accuracyCircle.setRadius(s.currentAccuracyMeters || 5);
                        map.panTo([currentLat, currentLng]);

                        // Update Polyline
                        if (data.latestPing) {
                            polyline.addLatLng([data.latestPing.latitude, data.latestPing.longitude]);
                        }

                        // Update Telemetry Panel
                        document.getElementById('val-lat').textContent = currentLat.toFixed(5);
                        document.getElementById('val-lng').textContent = currentLng.toFixed(5);
                        document.getElementById('val-speed').textContent = (s.speedKmh || 0).toFixed(1) + ' km/h';
                        document.getElementById('val-battery').textContent = s.batteryPercent + '%';

                        // Update Status
                        if (s.isCancelled) {
                            const badge = document.getElementById('status-badge');
                            badge.style.borderColor = '#10B981';
                            badge.style.color = '#10B981';
                            badge.style.backgroundColor = 'rgba(16, 185, 129, 0.15)';
                            document.querySelector('.pulse-dot').style.backgroundColor = '#10B981';
                            document.getElementById('status-text').textContent = 'RESOLVED / CANCELLED';
                            document.getElementById('alert-banner').style.display = 'none';
                        }
                    }
                } catch (e) {
                    console.error('Error parsing WS frame:', e);
                }
            };

            ws.onclose = function() {
                console.log('WebSocket closed. Reconnecting in 3 seconds...');
                setTimeout(connectWebSocket, 3000);
            };

            ws.onerror = function(err) {
                console.error('WebSocket encountered error:', err);
                ws.close();
            };
        }

        connectWebSocket();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
