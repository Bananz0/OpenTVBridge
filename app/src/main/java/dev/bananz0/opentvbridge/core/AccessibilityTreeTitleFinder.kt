// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

data class NodeSnapshot(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val children: List<NodeSnapshot> = emptyList(),
)

object AccessibilityTreeTitleFinder {
    /**
     * Finds a detail-page title using [profile]'s id priority, so a specific
     * id such as `entity_details_title_row` beats a generic `title`.
     */
    fun findDetailTitle(root: NodeSnapshot?, profile: LauncherProfile): ParsedTitle? {
        if (root == null || profile.detailTitleViewIds.isEmpty()) return null
        return flatten(root)
            .mapNotNull { node ->
                val suffix = node.viewId?.substringAfterLast('/') ?: return@mapNotNull null
                val priority = profile.detailTitleViewIds.indexOf(suffix)
                    .takeIf { it >= 0 } ?: return@mapNotNull null
                val parsed = LauncherTextParser.parseTitle(node.text ?: node.contentDescription)
                    ?: return@mapNotNull null
                priority to parsed
            }
            .minByOrNull { it.first }
            ?.second
    }

    /** Finds a card title from the content description of a profile card id. */
    fun findCardTitle(root: NodeSnapshot?, profile: LauncherProfile): ParsedTitle? {
        if (root == null || profile.cardDescriptionViewIds.isEmpty()) return null
        return flatten(root)
            .firstOrNull { node ->
                val suffix = node.viewId?.substringAfterLast('/') ?: return@firstOrNull false
                suffix in profile.cardDescriptionViewIds && !node.contentDescription.isNullOrBlank()
            }
            ?.contentDescription
            ?.let(LauncherTextParser::parseTitle)
    }

    fun findGoogleTitle(root: NodeSnapshot?): ParsedTitle? =
        findDetailTitle(root, LauncherProfiles.GOOGLE_TV)

    fun findFireTvTitle(root: NodeSnapshot?): ParsedTitle? =
        findCardTitle(root, LauncherProfiles.FIRE_TV)

    private fun flatten(root: NodeSnapshot): Sequence<NodeSnapshot> = sequence {
        yield(root)
        root.children.forEach { yieldAll(flatten(it)) }
    }
}
