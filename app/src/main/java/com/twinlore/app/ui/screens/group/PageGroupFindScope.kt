package com.twinlore.app.ui.screens.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.twinlore.app.ui.screens.FindChip

/**
 * What the page group's find searches. [ALL_PAGES] is the cross-page
 * result list ([PageGroupFindScreen]); [THIS_PAGE] is the single guide's
 * find bar on the page that is open: WebView matches highlighted in place,
 * a match counter, Previous / Next.
 */
enum class PageGroupFindScope { ALL_PAGES, THIS_PAGE }

/**
 * What to search once a new page finishes loading under an open This-page
 * find bar: the bar's [query] as typed, or null for a blank one
 * (the fresh page has no matches to clear). A query already pending from a
 * cross-page pick ([pending]) is kept as it is.
 */
internal fun pageFindRequeryOnLoad(query: String, pending: String?): String? = when {
    pending != null -> pending
    query.isBlank() -> null
    else -> query
}

/**
 * The All pages / This page switch. Shown in both find views, the result
 * list's header and the find bar, so either one can flip to the other with
 * the query carried over.
 */
@Composable
internal fun PageGroupFindScopeSwitch(
    scope: PageGroupFindScope,
    focusColor: Color,
    onScopeChange: (PageGroupFindScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FindChip(
            label = "All pages",
            focusColor = focusColor,
            selected = scope == PageGroupFindScope.ALL_PAGES,
            leadingIcon = Icons.Filled.LibraryBooks,
            onClick = { onScopeChange(PageGroupFindScope.ALL_PAGES) },
        )
        FindChip(
            label = "This page",
            focusColor = focusColor,
            selected = scope == PageGroupFindScope.THIS_PAGE,
            leadingIcon = Icons.Filled.Article,
            onClick = { onScopeChange(PageGroupFindScope.THIS_PAGE) },
        )
    }
}
