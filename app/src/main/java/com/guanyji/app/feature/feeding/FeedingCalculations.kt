package com.guanyji.app.feature.feeding

import com.guanyji.app.data.local.FeedingEventWithTargets
import com.guanyji.app.data.local.FeedingTargetType

fun tankFeedingEventCount(events: List<FeedingEventWithTargets>): Int = events.map { it.event.id }.distinct().size

fun isFeedingAssociatedWithFish(event: FeedingEventWithTargets, fishId: String, tankId: String): Boolean =
    event.targets.any { it.fishId == fishId || (it.targetType == FeedingTargetType.tank && it.tankId == tankId) }
