package com.annotatio.maximus.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object GitHubApi {
    private const val GITHUB_API_URL = "https://api.github.com"
    private const val REPO_OWNER = "Misterwanwa"
    private const val REPO_NAME = "AnnotatioMaximus"
    // Personal access token with "issues: write" scope – set via BuildConfig or leave empty for anonymous
    private const val ACCESS_TOKEN = ""

    private val client = OkHttpClient()

    suspend fun submitBugReport(description: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("title", "Bug Report from App")
                    put("body", description)
                    put("labels", listOf("bug"))
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$GITHUB_API_URL/repos/$REPO_OWNER/$REPO_NAME/issues")
                    .addHeader("Authorization", "token $ACCESS_TOKEN")
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val issueJson = JSONObject(responseBody)
                    val issueNumber = issueJson.getInt("number")
                    Result.success("Issue #$issueNumber created successfully")
                } else {
                    Result.failure(Exception("Failed to create issue: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}