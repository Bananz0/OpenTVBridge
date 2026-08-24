package dev.bananz0.opentvbridge.core

data class NodeSnapshot(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val children: List<NodeSnapshot> = emptyList(),
)

object AccessibilityTreeTitleFinder {
    private val googleTitleIds = listOf(
        "entity_details_title_row",
        "entity_details_title",
        "movie_title",
        "detail_title",
        "title",
    )

    fun findGoogleTitle(root: NodeSnapshot?): ParsedTitle? {
        if (root == null) return null
        return flatten(root)
            .mapNotNull { node ->
                val suffix = node.viewId?.substringAfterLast('/') ?: return@mapNotNull null
                val priority = googleTitleIds.indexOf(suffix).takeIf { it >= 0 } ?: return@mapNotNull null
                val parsed = LauncherTextParser.parseTitle(node.text ?: node.contentDescription)
                    ?: return@mapNotNull null
                priority to parsed
            }
            .minByOrNull { it.first }
            ?.second
    }

    fun findFireTvTitle(root: NodeSnapshot?): ParsedTitle? {
        if (root == null) return null
        return flatten(root)
            .firstOrNull { it.viewId?.endsWith(":id/main_image") == true }
            ?.contentDescription
            ?.let(LauncherTextParser::parseTitle)
    }

    private fun flatten(root: NodeSnapshot): Sequence<NodeSnapshot> = sequence {
        yield(root)
        root.children.forEach { yieldAll(flatten(it)) }
    }
}
