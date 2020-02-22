package com.koalatea.sedaily.feature.episodes.paging

import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagedList
import com.algolia.search.saas.AlgoliaException
import com.algolia.search.saas.Client
import com.algolia.search.saas.Query
import com.google.gson.Gson
import com.koalatea.sedaily.database.model.Episode
import com.koalatea.sedaily.database.model.EpisodeDetails
import com.koalatea.sedaily.model.SearchQuery
import com.koalatea.sedaily.network.NetworkManager
import com.koalatea.sedaily.network.NetworkState
import com.koalatea.sedaily.network.SEDailyApi
import com.koalatea.sedaily.network.response.EpisodesAlgoliaResponse
import com.koalatea.sedaily.util.Event
import com.koalatea.sedaily.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class EpisodesBoundaryCallback(
        private val searchQuery: SearchQuery,
        private val api: SEDailyApi,
        private val algoliaClient: Client,
        private val gson: Gson,
        private val networkManager: NetworkManager,
        private val insertResultIntoDb: (SearchQuery, List<Episode>?) -> Unit,
        private val handleSuccessfulRefresh: (SearchQuery, List<Episode>?) -> Unit,
        private val networkPageSize: Int)
    : PagedList.BoundaryCallback<EpisodeDetails>() {

    val networkState = MutableLiveData<Event<NetworkState>>()

    private var isRequestInProgress = false

    private var algoliaPage = 0

    @MainThread
    override fun onZeroItemsLoaded() {
        load(callback = insertResultIntoDb)
    }

    @MainThread
    override fun onItemAtFrontLoaded(itemAtFront: EpisodeDetails) {
        // ignored, since we only ever append to what's in the DB
    }

    @MainThread
    override fun onItemAtEndLoaded(itemAtEnd: EpisodeDetails) {
        load(itemAtEnd.episode.date, insertResultIntoDb)
    }

    @MainThread
    fun refresh() {
        algoliaPage = 0

        load(callback = handleSuccessfulRefresh)
    }

    @MainThread
    private fun load(createdAtBefore: String? = null, callback: (SearchQuery, List<Episode>?) -> Unit) {
        if (isRequestInProgress) return

        if (searchQuery.searchTerm.isNullOrBlank()) {
            searchSed(createdAtBefore, callback)
        } else {
            searchAlgolia(callback)
        }
    }

    @MainThread
    private fun searchSed(createdAtBefore: String?, callback: (SearchQuery, List<Episode>?) -> Unit) {
        GlobalScope.launch(Dispatchers.Main) {
            isRequestInProgress = true
            algoliaPage = 0
            networkState.value = Event(NetworkState.Loading)

            val response = safeApiCall {
                api.getEpisodesAsync(
                        searchQuery.searchTerm,
                        searchQuery.categoryId,
                        searchQuery.tagId,
                        createdAtBefore,
                        networkPageSize).await()
            }

            if (response?.isSuccessful == true) {
                callback(searchQuery, response.body())

                networkState.value = Event(NetworkState.Loaded(response.body()?.size ?: 0))
            } else {
                val error = NetworkState.Error(response?.errorBody()?.string(), networkManager.isConnected)

                networkState.value = Event(error)
            }

            isRequestInProgress = false
        }
    }

    @MainThread
    private fun searchAlgolia(callback: (SearchQuery, List<Episode>?) -> Unit) {
        isRequestInProgress = true
        networkState.value = Event(NetworkState.Loading)

        val index = algoliaClient.getIndex("prod_POSTS")

        val query = Query(searchQuery.searchTerm)
                .setPage(algoliaPage)
                .setHitsPerPage(networkPageSize)

        index.searchAsync(query) { content: JSONObject?, error: AlgoliaException? ->
            content?.let {
                val algoliaResponse = gson.fromJson(it.toString(), EpisodesAlgoliaResponse::class.java)
                callback(searchQuery, algoliaResponse.hits)

                networkState.value = Event(NetworkState.Loaded(algoliaResponse.nbHits))

                algoliaPage += 1
            } ?: run {
                networkState.value = Event(NetworkState.Error(error?.message, networkManager.isConnected))
            }

            isRequestInProgress = false
        }
    }

}