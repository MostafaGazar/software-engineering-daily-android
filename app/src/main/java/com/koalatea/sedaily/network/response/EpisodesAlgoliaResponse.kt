package com.koalatea.sedaily.network.response

import com.koalatea.sedaily.database.model.Episode

data class EpisodesAlgoliaResponse(
        val hits: List<Episode>,
        val page: Int,
        val nbHits: Int,
        val nbPages: Int
)