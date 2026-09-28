-- All liveness decisions use Redis time, not the individual proxy clocks.
local operation, id, token = ARGV[1], ARGV[2], ARGV[3]
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local function live(proxy, timeout)
    local heartbeat = tonumber(redis.call('HGET', 'redivelocity:heartbeats', proxy))
    return heartbeat and now - heartbeat <= timeout
end
local function remove(proxy)
    -- Keep ownership checks, deletion, and notifications in the same transaction.
    local players = redis.call('HGETALL', 'redivelocity:player:proxies')
    for i = 1, #players, 2 do
        if players[i + 1] == proxy then
            local uuid = players[i]
            local message = {action='DISCONNECT', uuid=uuid, proxyId=proxy, timestamp=now}
            message.username = redis.call('HGET', 'redivelocity:player:names', uuid) or nil
            message.sessionId = redis.call('HGET', 'redivelocity:player:sessions', uuid) or nil
            message.ip = redis.call('HGET', 'redivelocity:player:ips', uuid) or nil
            for _, field in ipairs({'servers', 'names', 'proxies', 'sessions', 'ips'}) do
                redis.call('HDEL', 'redivelocity:player:' .. field, uuid)
            end
            redis.call('PUBLISH', 'redivelocity:players', cjson.encode(message))
        end
    end
    for _, key in ipairs({'proxies', 'heartbeats', 'proxy:instances', 'votes', 'proxy:player-counts'}) do
        redis.call('HDEL', 'redivelocity:' .. key, proxy)
    end
    redis.call('DEL', 'redivelocity:registered-servers:' .. proxy)
    redis.call('SREM', 'redivelocity:existing-proxy-ids', proxy)
    if redis.call('HGET', 'redivelocity:leader', 'leader-id') == proxy then
        redis.call('HDEL', 'redivelocity:leader', 'leader-id')
    end
    redis.call('DEL', 'redivelocity:global:playercount')
    redis.call('PUBLISH', 'redivelocity:proxy-events', cjson.encode({action='REMOVE', id=proxy}))
    redis.call('PUBLISH', 'redivelocity:global-player-updates', '{"action":"UPDATE"}')
end
if operation == 'register' then
    local owner = redis.call('HGET', 'redivelocity:proxy:instances', id)
    if live(id, 90000) and owner ~= token then return 0 end
    if owner ~= token then remove(id) end
    -- An empty registry is insufficient: a starting/legacy proxy can already have a heartbeat.
    local anyLive = false
    for _, proxy in ipairs(redis.call('HKEYS', 'redivelocity:heartbeats')) do
        if live(proxy, 90000) then anyLive = true; break end
    end
    if not anyLive then
        for _, field in ipairs({'servers', 'names', 'proxies', 'sessions', 'ips'}) do
            redis.call('DEL', 'redivelocity:player:' .. field)
        end
        for _, key in ipairs({'proxy:player-counts', 'global:playercount', 'votes', 'leader'}) do
            redis.call('DEL', 'redivelocity:' .. key)
        end
    end
    redis.call('HSET', 'redivelocity:proxy:instances', id, token)
    redis.call('HSET', 'redivelocity:heartbeats', id, now)
    redis.call('HSET', 'redivelocity:proxies', id, id)
    redis.call('PUBLISH', 'redivelocity:proxy-events', cjson.encode({action='ADD', id=id}))
    return 1
elseif operation == 'heartbeat' then
    if redis.call('HGET', 'redivelocity:proxy:instances', id) ~= token then return 0 end
    redis.call('HSET', 'redivelocity:heartbeats', id, now)
    redis.call('HSET', 'redivelocity:proxies', id, id)
    return 1
elseif operation == 'count' or operation == 'server-add' or operation == 'server-remove' or operation == 'servers' then
    if redis.call('HGET', 'redivelocity:proxy:instances', id) ~= token then return 0 end
    if operation == 'count' then
        redis.call('HSET', 'redivelocity:proxy:player-counts', id, ARGV[4])
    elseif operation == 'server-add' then
        redis.call('HSET', 'redivelocity:registered-servers:' .. id, ARGV[4], ARGV[5])
    elseif operation == 'server-remove' then
        redis.call('HDEL', 'redivelocity:registered-servers:' .. id, ARGV[4])
    else
        redis.call('DEL', 'redivelocity:registered-servers:' .. id)
        for i = 4, #ARGV, 2 do
            redis.call('HSET', 'redivelocity:registered-servers:' .. id, ARGV[i], ARGV[i + 1])
        end
    end
    return 1
elseif operation == 'orphan-player' then
    if redis.call('HEXISTS', 'redivelocity:player:proxies', id) == 1 then return 0 end
    for _, field in ipairs({'servers', 'names', 'sessions', 'ips'}) do
        redis.call('HDEL', 'redivelocity:player:' .. field, id)
    end
    return 1
elseif operation == 'cleanup' then
    -- Revalidate even when the candidate was discovered from an old SCAN/snapshot.
    if live(id, 90000) then return 0 end
    remove(id)
    return 1
elseif operation == 'shutdown' then
    if redis.call('HGET', 'redivelocity:proxy:instances', id) ~= token then return 0 end
    remove(id)
    return 1
elseif operation == 'elect' then
    local leader = redis.call('HGET', 'redivelocity:leader', 'leader-id')
    if leader and redis.call('HEXISTS', 'redivelocity:proxies', leader) == 1 and live(leader, 30000) then
        return leader
    end
    local candidates = redis.call('HKEYS', 'redivelocity:proxies')
    table.sort(candidates)
    local winner = ''
    for _, candidate in ipairs(candidates) do
        if live(candidate, 30000) then winner = candidate; break end
    end
    redis.call('HDEL', 'redivelocity:leader', 'leader-id')
    redis.call('DEL', 'redivelocity:votes')
    if winner ~= '' then redis.call('HSET', 'redivelocity:leader', 'leader-id', winner) end
    if leader and leader ~= winner then
        redis.call('PUBLISH', 'redivelocity:leader-election', cjson.encode({action='REMOVED', recipient=leader}))
    end
    if winner ~= '' then
        redis.call('PUBLISH', 'redivelocity:leader-election', cjson.encode({action='SET', recipient=winner, reason='Live proxy election', votes=0}))
    end
    return winner
end
return redis.error_reply('Unknown lifecycle operation')
