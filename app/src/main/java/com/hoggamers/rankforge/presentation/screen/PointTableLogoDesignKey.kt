package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.export.FreeDesignTemplateRegistry

internal fun pointTableLogoDesignKey(
    design: DownloadResultDesignType,
    freeDesignTemplateId: String,
): String? = when (design) {
    DownloadResultDesignType.IMAGE -> "IMAGE"
    DownloadResultDesignType.FREE_DESIGN ->
        FreeDesignTemplateRegistry.findById(freeDesignTemplateId)?.let { "FREE_DESIGN:${it.id}" }
    DownloadResultDesignType.MY_DESIGN -> null
}
