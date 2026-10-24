local operation, proxy, owner, uuid, session = ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5]
if redis.call('HGET', 'redivelocity:proxy:instances', proxy) ~= owner then return 0 end
if operation == 'login' then
    local time = redis.call('TIME')
    redis.call('ZADD', 'redivelocity:player:joined-at', tonumber(time[1]) * 1000000 + tonumber(time[2]), uuid)
    redis.call('HSET', 'redivelocity:player:max-players-bypass', uuid, ARGV[8] or 'false')
    redis.call('HSET', 'redivelocity:player:proxies', uuid, proxy)
    redis.call('HSET', 'redivelocity:player:sessions', uuid, session)
    redis.call('HSET', 'redivelocity:player:names', uuid, ARGV[6])
    redis.call('HSET', 'redivelocity:player:ips', uuid, ARGV[7])
    redis.call('HDEL', 'redivelocity:player:servers', uuid)
    return 1
end
if redis.call('HGET', 'redivelocity:player:proxies', uuid) ~= proxy or
   redis.call('HGET', 'redivelocity:player:sessions', uuid) ~= session then return 0 end
if operation == 'disconnect' then
    redis.call('ZREM', 'redivelocity:player:joined-at', uuid)
    for _, field in ipairs({'servers', 'names', 'proxies', 'sessions', 'ips', 'max-players-bypass'}) do
        redis.call('HDEL', 'redivelocity:player:' .. field, uuid)
    end
    return 1
elseif operation == 'bypass' then
    redis.call('HSET', 'redivelocity:player:max-players-bypass', uuid, ARGV[6])
    return 1
elseif operation == 'switch' then
    redis.call('HSET', 'redivelocity:player:servers', uuid, ARGV[6])
    return 1
end
return redis.error_reply('Unknown player operation')
