package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.runway.RunwayItem
import io.github.mejdi14.oddscroll.runway.RunwayScroll
import io.github.mejdi14.oddscroll.runway.RunwaySpan
import io.github.mejdi14.oddscroll.runway.rememberRunwayScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.wide_basalt
import kmpoddscroll.shared.generated.resources.wide_falls
import kmpoddscroll.shared.generated.resources.wide_forest
import kmpoddscroll.shared.generated.resources.wide_peaks
import kmpoddscroll.shared.generated.resources.wide_sounds
import kmpoddscroll.shared.generated.resources.wide_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The colour-banded blueprint runway, populated across all eleven sections with
 * raised 3D card plinths, side callout leaders, the 500-px coordinate ruler,
 * and the left-margin index tethered by live Bezier splines.
 */
@Composable
fun RunwayDemoScreen() {
    val sounds = imageResource(Res.drawable.wide_sounds)
    val falls = imageResource(Res.drawable.wide_falls)
    val peaks = imageResource(Res.drawable.wide_peaks)
    val forest = imageResource(Res.drawable.wide_forest)
    val basalt = imageResource(Res.drawable.wide_basalt)
    val towers = imageResource(Res.drawable.wide_towers)

    val items = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(
            RunwayItem(
                image = sounds,
                title = "Shop skill for any agent",
                subtitle = "Give agents discovery, personalized recommendations, and checkout.",
                callout = "Shop skill for any agent",
                sectionIndex = 0,
                span = RunwaySpan.Full,
            ),
            RunwayItem(
                image = peaks,
                title = "Digital products in agents",
                subtitle = "Drop a digital product into any agent with instant fulfillment.",
                callout = "Digital products in agents",
                sectionIndex = 0,
                span = RunwaySpan.Left,
            ),
            RunwayItem(
                image = falls,
                title = "Product sourcing in agents",
                subtitle = "Import products from collective catalogs directly inside chat.",
                callout = "Product sourcing in agents",
                sectionIndex = 0,
                span = RunwaySpan.Right,
            ),
            RunwayItem(
                image = towers,
                title = "More technical tasks with Sidekick",
                subtitle = "Stages multi-step changes, computes, codes, and searches.",
                callout = "More technical tasks with Sidekick",
                sectionIndex = 1,
                span = RunwaySpan.Full,
            ),
            RunwayItem(
                image = forest,
                title = "Your whole store edited in one view",
                subtitle = "Design your entire storefront on a unified canvas.",
                callout = "Your whole store edited in one view",
                sectionIndex = 2,
                span = RunwaySpan.Full,
            ),
            RunwayItem(
                image = basalt,
                title = "Clearer rollouts setup",
                subtitle = "Schedule launches, themes, and checkout rules as one rollout.",
                callout = "Clearer rollouts setup",
                sectionIndex = 2,
                span = RunwaySpan.Full,
            ),
            RunwayItem(
                image = peaks,
                title = "Multi-source product data mapping",
                subtitle = "Map and sync catalog attributes across channels.",
                callout = "Multi-source product mapping",
                sectionIndex = 3,
                span = RunwaySpan.Left,
            ),
            RunwayItem(
                image = sounds,
                title = "Variants managed from the product page",
                subtitle = "High-cardinality variant sets edited inline.",
                callout = "Variants managed inline",
                sectionIndex = 3,
                span = RunwaySpan.Right,
            ),
            RunwayItem(
                image = towers,
                title = "Expanded in-store customer recognition",
                subtitle = "POS identifies returning shoppers across every location.",
                callout = "Expanded in-store recognition",
                sectionIndex = 4,
                span = RunwaySpan.Full,
            ),
            RunwayItem(
                image = falls,
                title = "More local payment methods",
                subtitle = "Regional wallets and instant bank transfers built in.",
                callout = "More local payment methods",
                sectionIndex = 5,
                span = RunwaySpan.Left,
            ),
            RunwayItem(
                image = forest,
                title = "Payouts made more scannable",
                subtitle = "Date-based payout breakdown with expandable settlement rows.",
                callout = "Payouts made more scannable",
                sectionIndex = 5,
                span = RunwaySpan.Right,
            ),
            RunwayItem(
                image = basalt,
                title = "Geospatial clustering and interactive maps",
                subtitle = "Real-time regional demand and fulfillment heatmaps.",
                callout = "Geospatial clustering & maps",
                sectionIndex = 6,
                span = RunwaySpan.Full,
            ),
        )
    }

    RunwayScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        state = rememberRunwayScrollState(autoplay = false),
        paperBuildings = true,
    )
}
