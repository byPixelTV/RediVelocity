-- Freeze one network-wide selection per command so different proxies cannot over-kick.
local limit, fallback, enabled = tonumber(ARGV[1]), tonumber(ARGV[2]), ARGV[3] == 'true'
local planKey = 'redivelocity:max-player-plan:' .. ARGV[4]
local cached = redis.call('GET', planKey)
if cached then return cached end
local previous = tonumber(redis.call('GET', 'redivelocity:login-config:max-players')) or fallback
redis.call('SET', 'redivelocity:login-config:max-players', limit)
local selected = {}
if enabled and limit >= 0 and (previous < 0 or limit < previous) then
    local remaining = redis.call('HLEN', 'redivelocity:player:proxies') - limit
    for _, uuid in ipairs(redis.call('ZREVRANGE', 'redivelocity:player:joined-at', 0, -1)) do
        if remaining <= 0 then break end
        local session = redis.call('HGET', 'redivelocity:player:sessions', uuid)
        if session and redis.call('HEXISTS', 'redivelocity:player:proxies', uuid) == 1 and
           redis.call('HGET', 'redivelocity:player:max-players-bypass', uuid) ~= 'true' then
            selected[#selected + 1] = {uuid, session}
            remaining = remaining - 1
        end
    end
end
local result = #selected == 0 and '[]' or cjson.encode(selected)
redis.call('SET', planKey, result, 'EX', 300)
return result
