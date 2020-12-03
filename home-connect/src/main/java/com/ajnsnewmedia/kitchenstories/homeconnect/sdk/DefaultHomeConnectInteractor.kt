package com.ajnsnewmedia.kitchenstories.homeconnect.sdk

import android.util.Log
import com.ajnsnewmedia.kitchenstories.homeconnect.HomeConnectApi
import com.ajnsnewmedia.kitchenstories.homeconnect.model.appliances.HomeAppliance
import com.ajnsnewmedia.kitchenstories.homeconnect.model.appliances.HomeApplianceType
import com.ajnsnewmedia.kitchenstories.homeconnect.model.base.HomeConnectApiError
import com.ajnsnewmedia.kitchenstories.homeconnect.model.base.HomeConnectApiErrorResponse
import com.ajnsnewmedia.kitchenstories.homeconnect.model.base.HomeConnectApiRequest
import com.ajnsnewmedia.kitchenstories.homeconnect.model.jsonadapters.HomeConnectMoshiBuilder
import com.ajnsnewmedia.kitchenstories.homeconnect.model.programs.AvailableProgram
import com.ajnsnewmedia.kitchenstories.homeconnect.model.programs.StartProgramRequest
import com.ajnsnewmedia.kitchenstories.homeconnect.util.ErrorHandler
import com.ajnsnewmedia.kitchenstories.homeconnect.util.HomeConnectApiFactory
import com.ajnsnewmedia.kitchenstories.homeconnect.util.HomeConnectError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.HttpException

internal class DefaultHomeConnectInteractor(
        private val homeConnectApiFactory: HomeConnectApiFactory,
        private val homeConnectSecretsStore: HomeConnectSecretsStore,
        private val errorHandler: ErrorHandler,
        private val ioDispatcher: CoroutineDispatcher,
) : HomeConnectClient {

    private val homeConnectApi: HomeConnectApi by lazy { homeConnectApiFactory.getHomeConnectApi() }

    override val isAuthorized: Boolean
        get() = homeConnectSecretsStore.accessToken != null

    override suspend fun getAllHomeAppliances(ofType: HomeApplianceType?): List<HomeAppliance> {
        try {
            val allAppliances = homeConnectApi.getAllHomeAppliances().data.homeappliances
            return if (ofType != null) {
                allAppliances.filter { it.type == ofType }
            } else {
                allAppliances
            }
        } catch (e: Throwable) {
            Log.e("HomeConnectApi", "getAllHomeAppliances failed", e)
            errorHandler.handle(e)
        }
    }

    override suspend fun getAvailablePrograms(forApplianceId: String): List<AvailableProgram> {
        return try {
            homeConnectApi.getAvailablePrograms(forApplianceId).data.programs
        } catch (e: Throwable) {
            Log.e("HomeConnectApi", "getAllHomeAppliances failed", e)
            errorHandler.handle(e)
        }
    }

    override fun logOutUser() {
        homeConnectSecretsStore.accessToken = null
    }

    override suspend fun startProgram(forApplianceId: String, program: StartProgramRequest) {
        try {
            val response = homeConnectApi.startProgram(forApplianceId, HomeConnectApiRequest(program))
            if (!response.isSuccessful) {
                val errorDescription = response.errorBody()?.tryParsingApiError()?.description
                throw HomeConnectError.Unspecified(message = errorDescription ?: "", cause = HttpException(response))
            }
        } catch (e: Throwable) {
            Log.e("HomeConnectApi", "starting a program failed", e)
            errorHandler.handle(e)
        }
    }

    private suspend fun ResponseBody.tryParsingApiError(): HomeConnectApiError? {
        val error = try {
            val errorJsonAdapter = HomeConnectMoshiBuilder.moshiInstance.adapter(HomeConnectApiErrorResponse::class.java)
            withContext(ioDispatcher) {
                errorJsonAdapter.fromJson(Buffer().readFrom(this@tryParsingApiError.byteStream()))
            }
        } catch (e: Throwable) {
            null
        }
        return error?.error
    }

}

