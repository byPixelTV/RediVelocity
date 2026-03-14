package dev.bypixel.redivelocity.antivpn

import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.flow.toList

object AntiVPNManager {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun addIpToBlacklist(ip: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hset("redivelocity:antivpn-blacklist-ip", ip, ip)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun removeIpFromBlacklist(ip: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hdel("redivelocity:antivpn-blacklist-ip", ip)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun addAsnToBlacklist(asn: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hset("redivelocity:antivpn-blacklist-asn", asn, asn)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun removeAsnFromBlacklist(asn: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hdel("redivelocity:antivpn-blacklist-asn", asn)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun addIpToWhitelist(ip: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hset("redivelocity:antivpn-whitelist-ip", ip, ip)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun removeIpFromWhitelist(ip: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hdel("redivelocity:antivpn-whitelist-ip", ip)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun addAsnToWhitelist(asn: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hset("redivelocity:antivpn-whitelist-asn", asn, asn)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun removeAsnFromWhitelist(asn: String) {
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hdel("redivelocity:antivpn-whitelist-asn", asn)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun isIpBlacklisted(ip: String): Boolean {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hget("redivelocity:antivpn-blacklist-ip", ip) != null
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun isAsnBlacklisted(asn: String): Boolean {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hget("redivelocity:antivpn-blacklist-asn", asn) != null
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getAllBlacklistedIps(): List<String> {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hkeys("redivelocity:antivpn-blacklist-ip").toList()
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getAllBlacklistedAsns(): List<String> {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hkeys("redivelocity:antivpn-blacklist-asn").toList()
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun isIpWhitelisted(ip: String): Boolean {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hget("redivelocity:antivpn-whitelist-ip", ip) != null
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun isAsnWhitelisted(asn: String): Boolean {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hget("redivelocity:antivpn-whitelist-asn", asn) != null
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getAllWhitelistedIps(): List<String> {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hkeys("redivelocity:antivpn-whitelist-ip").toList()
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getAllWhitelistedAsns(): List<String> {
        return RediVelocity.instance.lettuceClient.withCoroutines {
            it.hkeys("redivelocity:antivpn-whitelist-asn").toList()
        }
    }
}