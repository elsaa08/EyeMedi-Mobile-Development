package com.capstone.mybottomnav.db

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.capstone.mybottomnav.Api.ApiService
import com.capstone.mybottomnav.MainActivity
import com.capstone.mybottomnav.data.Card
import com.capstone.mybottomnav.response.ListFeedbackItem

@OptIn(ExperimentalPagingApi::class)
class RemoteMediator(private val database: RoomDb,
                     private val apiService: ApiService
) : RemoteMediator<Int, Card>() {

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }
    private fun getPageStory(stories: List<ListFeedbackItem>): List<Card> {
        val list = ArrayList<Card>()
        for (data in stories) {
            val item = data.id?.let {
                Card(

                    data.nama.toString(),
                    data.testimoni.toString(),
                    data.createdAt.toString(),
                    it,
                )
            }
            if (item != null) {
                list.add(item)
            }
        }
        return list
    }
    override suspend fun load(
        loadType: LoadType, state: PagingState<Int, Card>
    )
            : MediatorResult {
        val page = when (loadType) {

            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: INITIAL_PAGE_INDEX
            }

            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKey = remoteKeys?.prevKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                prevKey
            }

            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey
                    ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                nextKey
            }

        }

        try {
            val responseData = apiService.getPaging(
                MainActivity.TOKEN.toString(),
                page,
                state.config.pageSize
            )
            val listpageStory = getPageStory(responseData.listFeedback )
            val endOfPaginationReached = listpageStory.isEmpty()

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    database.remoteKeysDao().deleteRemoteKeys()
                    database.storyDao().deleteAll()
                }

                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = listpageStory.map {
                    RemoteKeys(id = it.id, prevKey = prevKey, nextKey = nextKey)
                }
                database.remoteKeysDao().insertAll(keys)
                database.storyDao().insertQuote(listpageStory)
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)

        } catch (exception: Exception) {
            return MediatorResult.Error(exception)
        }
    }
    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, Card>): RemoteKeys? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()?.let { data ->
            database.remoteKeysDao().getRemoteKeysId(data.id)
        }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, Card>): RemoteKeys? {
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()?.let { data ->
            database.remoteKeysDao().getRemoteKeysId(data.id)
        }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, Card>): RemoteKeys? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { id ->
                database.remoteKeysDao().getRemoteKeysId(id)
            }
        }
    }



    private companion object {
        const val INITIAL_PAGE_INDEX = 1
    }
}