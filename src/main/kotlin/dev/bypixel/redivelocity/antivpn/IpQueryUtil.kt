package dev.bypixel.redivelocity.antivpn

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object IpQueryUtil {
    // Example response from https://api.ipquery.io/1.1.1.1
    /**
     * {
     *   "ip": "1.1.1.1",
     *   "isp": {
     *     "asn": "AS13335",
     *     "org": "Cloudflare, Inc.",
     *     "isp": "Cloudflare, Inc."
     *   },
     *   "location": {
     *     "country": "Australia",
     *     "country_code": "AU",
     *     "city": "Sydney",
     *     "state": "New South Wales",
     *     "zipcode": "1001",
     *     "latitude": -33.854548400186665,
     *     "longitude": 151.20016200912815,
     *     "timezone": "Australia/Sydney",
     *     "localtime": "2025-10-13T19:37:08" // Note: localtime field may always be obsolete, because it is not updated in real-time
     *   },
     *   "risk": {
     *     "is_mobile": false,
     *     "is_vpn": false,
     *     "is_tor": false,
     *     "is_proxy": false,
     *     "is_datacenter": true,
     *     "risk_score": 0
     *   }
     * }
     */

    private val dispatcher = Dispatcher().apply {
        maxRequests = 128
        maxRequestsPerHost = 32
    }

    private val client = OkHttpClient().newBuilder()
        .dispatcher(dispatcher)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun getIpData(ip: String): JSONObject =
        suspendCancellableCoroutine { cont ->

            val request = Request.Builder()
                .url("https://api.ipquery.io/$ip")
                .get()
                .build()

            val call = client.newCall(request)

            cont.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IOException("HTTP ${response.code}")
                                )
                            }
                            return
                        }

                        val body = response.body.string()

                        if (cont.isActive) {
                            cont.resume(JSONObject(body))
                        }
                    }
                }
            })
        }

    fun getIp(json: JSONObject) = json.optString("ip", "null")
    fun getIpAsn(json: JSONObject) = json.optJSONObject("isp")?.optString("asn", "null") ?: "null"
    fun getIpOrg(json: JSONObject) = json.optJSONObject("isp")?.optString("org", "null") ?: "null"
    fun getIpIsp(json: JSONObject) = json.optJSONObject("isp")?.optString("isp", "null") ?: "null"
    fun getIpCountry(json: JSONObject) = json.optJSONObject("location")?.optString("country", "null") ?: "null"
    fun getIpCountryCode(json: JSONObject) = json.optJSONObject("location")?.optString("country_code", "null") ?: "null"
    fun getIpCity(json: JSONObject) = json.optJSONObject("location")?.optString("city", "null") ?: "null"
    fun getIpState(json: JSONObject) = json.optJSONObject("location")?.optString("state", "null") ?: "null"
    fun getIpZipcode(json: JSONObject) = json.optJSONObject("location")?.optString("zipcode", "null") ?: "null"
    fun getIpLatitude(json: JSONObject) = json.optJSONObject("location")?.optDouble("latitude", 0.0) ?: 0.0
    fun getIpLongitude(json: JSONObject) = json.optJSONObject("location")?.optDouble("longitude", 0.0) ?: 0.0
    fun getIpTimezone(json: JSONObject) = json.optJSONObject("location")?.optString("timezone", "null") ?: "null"
    fun getIpLocaltime(json: JSONObject) = json.optJSONObject("location")?.optString("localtime", "null") ?: "null"

    fun getIpIsMobile(json: JSONObject) = json.optJSONObject("risk")?.optBoolean("is_mobile", false) ?: false
    fun getIpIsVpn(json: JSONObject) = json.optJSONObject("risk")?.optBoolean("is_vpn", false) ?: false
    fun getIpIsTor(json: JSONObject) = json.optJSONObject("risk")?.optBoolean("is_tor", false) ?: false
    fun getIpIsProxy(json: JSONObject) = json.optJSONObject("risk")?.optBoolean("is_proxy", false) ?: false
    fun getIpIsDatacenter(json: JSONObject) = json.optJSONObject("risk")?.optBoolean("is_datacenter", false) ?: false
    fun getIpRiskScore(json: JSONObject) = json.optJSONObject("risk")?.optInt("risk_score", 0) ?: 0

    fun isIpRisky(json: JSONObject): Boolean {
        val risk = json.optJSONObject("risk") ?: return false
        return risk.optBoolean("is_proxy", false) ||
                risk.optBoolean("is_tor", false) ||
                risk.optBoolean("is_vpn", false) ||
                risk.optBoolean("is_datacenter", false)
    }

    fun getFlaggedRisks(json: JSONObject): List<String> {
        val risks = json.optJSONObject("risk") ?: return emptyList()
        return listOfNotNull(
            "proxy".takeIf { risks.optBoolean("is_proxy", false) },
            "tor".takeIf { risks.optBoolean("is_tor", false) },
            "vpn".takeIf { risks.optBoolean("is_vpn", false) },
            "datacenter".takeIf { risks.optBoolean("is_datacenter", false) }
        )
    }
}