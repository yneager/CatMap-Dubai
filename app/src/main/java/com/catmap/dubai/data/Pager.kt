package com.catmap.dubai.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What a paged list has so far. [loaded] is false until the first page arrives. */
data class Page<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val end: Boolean = false,
    val loaded: Boolean = false,
    val error: Throwable? = null,
)

/**
 * A list read one page at a time (Phase 5 of docs/done/MAJOR_UPDATE_PLAN.md): each page continues after the last document
 * of the one before (a cursor, never an offset, so nothing is read twice), and nothing listens for changes. Pull to
 * refresh calls [refresh]. A null [query] means "nothing to show" (for example an empty search).
 *
 * [fallback], when given, is used instead if [query] needs an index that isn't built yet (the app was installed
 * before the new indexes were deployed): usually the plain newest-first list, with the screen filtering the rest.
 */
class Pager<T : Any>(
    private val scope: CoroutineScope,
    private val pageSize: Long,
    private val query: () -> Query?,
    private val parse: (QuerySnapshot) -> List<T>,
    private val key: (T) -> String,
    private val fallback: (() -> Query?)? = null,
) {
    private var useFallback = false
    private val _state = MutableStateFlow(Page<T>())
    val state: StateFlow<Page<T>> = _state.asStateFlow()
    private var cursor: DocumentSnapshot? = null
    private var job: Job? = null

    fun loadMore() {
        val now = _state.value
        if (now.loading || now.end || job?.isActive == true) return
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                val base = if (useFallback) fallback?.invoke() else query()
                if (base == null) {
                    _state.value = Page(loaded = true, end = true)
                    return@launch
                }
                val snap = (cursor?.let { base.startAfter(it) } ?: base).limit(pageSize).get().await()
                cursor = snap.documents.lastOrNull() ?: cursor
                val fresh = parse(snap)
                _state.update { s ->
                    val seen = s.items.mapTo(HashSet(), key)
                    s.copy(items = s.items + fresh.filter { key(it) !in seen }, loading = false, loaded = true, end = snap.size() < pageSize)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!useFallback && cursor == null && fallback?.invoke() != null &&
                    (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                ) {
                    useFallback = true
                    _state.update { it.copy(loading = false) }
                    job = null // this attempt is over; let the retry start
                    loadMore()
                    return@launch
                }
                AppErrors.report(e)
                _state.update { it.copy(loading = false, loaded = true, error = e) }
            }
        }
    }

    /** Starts again from the first page. */
    fun refresh() {
        job?.cancel()
        cursor = null
        _state.value = Page()
        loadMore()
    }

    /** Removes an item that was deleted or hidden while the list was open. */
    fun remove(id: String) = _state.update { s -> s.copy(items = s.items.filterNot { key(it) == id }) }

    companion object {
        inline fun <reified T : Any> of(
            scope: CoroutineScope,
            pageSize: Long,
            noinline key: (T) -> String,
            noinline fallback: (() -> Query?)? = null,
            noinline query: () -> Query?,
        ): Pager<T> = Pager(scope, pageSize, query, { it.safeObjects<T>() }, key, fallback)
    }
}
