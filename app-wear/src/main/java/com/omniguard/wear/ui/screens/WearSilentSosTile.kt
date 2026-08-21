package com.omniguard.wear.ui.screens

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ColorBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Wear OS Tile for instant one-touch / quick-access Silent SOS dispatch.
 */
class WearSilentSosTileService : TileService() {

    override fun onTileRequest(requestParams: TileBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val layout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "OmniGuard SOS")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(ColorBuilders.argb(0xFFFF3B30.toInt()))
                    .build()
            )
            .setContent(
                Button.Builder(this, {})
                    .setTextContent("HOLD 3X\nPANIC")
                    .setButtonColors(ButtonColors.primaryButtonColors(ColorBuilders.argb(0xFFFF3B30.toInt()), ColorBuilders.argb(0xFFFFFFFF.toInt())))
                    .build()
            )
            .setSecondaryLabelTextContent(
                Text.Builder(this, "Press Crown 3x for Silent Panic")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(ColorBuilders.argb(0xFFB0B0B6.toInt()))
                    .build()
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build())
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(timeline)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: TileBuilders.TileResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return Futures.immediateFuture(resources)
    }
}
