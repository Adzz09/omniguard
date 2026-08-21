package com.omniguard.android.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object GuideMeHome : Screen("guide_me_home")
    object SafeZones : Screen("safe_zones")
    object DuressPin : Screen("duress_pin")
    object TransitLogs : Screen("transit_logs")
}
