package com.omniguard.feature.guidemehome.router

import com.omniguard.feature.guidemehome.model.FootTrafficLevel
import com.omniguard.feature.guidemehome.model.GeoPoint
import com.omniguard.feature.guidemehome.model.LightingLevel
import com.omniguard.feature.guidemehome.model.SafeRoute
import com.omniguard.feature.guidemehome.model.SafeRouteSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafeRouteResolverTest {

    private lateinit var resolver: SafeRouteResolver

    @BeforeEach
    fun setup() {
        resolver = SafeRouteResolver()
    }

    @Test
    fun `resolveSafestRoute selects well-lit CCTV route over isolated dark shortcut`() {
        val origin = GeoPoint(37.7749, -122.4194)
        val dest = GeoPoint(37.7800, -122.4100)

        // Route A: Main Boulevard - Well lit, high foot traffic, CCTV coverage
        val safeRoute = SafeRoute(
            routeId = "route-safe",
            origin = origin,
            destination = dest,
            segments = listOf(
                SafeRouteSegment(
                    startPoint = origin,
                    endPoint = dest,
                    streetName = "Main Boulevard",
                    distanceMeters = 800.0,
                    lightingLevel = LightingLevel.EXCELLENT_LIT,
                    footTrafficLevel = FootTrafficLevel.HIGH_DENSITY,
                    hasCctvSurveillance = true,
                    isMainThoroughfare = true,
                    safetyScore = 95.0f
                )
            ),
            totalDistanceMeters = 800.0,
            estimatedDurationSeconds = 600,
            compositeSafetyScore = 95.0f,
            maneuvers = emptyList()
        )

        // Route B: Dark alleyway shortcut - Unlit, isolated, no CCTV
        val dangerousShortcut = SafeRoute(
            routeId = "route-shortcut",
            origin = origin,
            destination = dest,
            segments = listOf(
                SafeRouteSegment(
                    startPoint = origin,
                    endPoint = dest,
                    streetName = "Dark Back Alley",
                    distanceMeters = 400.0,
                    lightingLevel = LightingLevel.UNLIT,
                    footTrafficLevel = FootTrafficLevel.ISOLATED,
                    hasCctvSurveillance = false,
                    isMainThoroughfare = false,
                    safetyScore = 15.0f
                )
            ),
            totalDistanceMeters = 400.0,
            estimatedDurationSeconds = 300,
            compositeSafetyScore = 15.0f,
            maneuvers = emptyList()
        )

        val scoreSafe = resolver.calculateSafetyWeight(safeRoute)
        val scoreShortcut = resolver.calculateSafetyWeight(dangerousShortcut)

        assertTrue(scoreSafe > 80.0f)
        assertTrue(scoreShortcut < 25.0f)

        val chosenRoute = resolver.resolveSafestRoute(origin, dest, listOf(dangerousShortcut, safeRoute))
        assertEquals("route-safe", chosenRoute.routeId)
    }
}
