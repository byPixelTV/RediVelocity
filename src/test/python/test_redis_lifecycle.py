"""Integration tests against a DISPOSABLE Redis. Uses and flushes database 15.

Run: REDIVELOCITY_TEST_REDIS_PORT=16389 python -m unittest discover -s src/test/python -v
No Python packages required. See src/test/README.md for Docker setup.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import socket
import unittest

PORT = os.environ.get('REDIVELOCITY_TEST_REDIS_PORT')
RESOURCES = Path(__file__).resolve().parents[2] / 'main' / 'resources' / 'redis'
LIFECYCLE = (RESOURCES / 'lifecycle.lua').read_text()
PLAYER = (RESOURCES / 'player.lua').read_text()
MAX_PLAYERS = (RESOURCES / 'max-players.lua').read_text()


class Redis:
    def __init__(self):
        self.socket = socket.create_connection(('127.0.0.1', int(PORT)), timeout=10)
        self.stream = self.socket.makefile('rb')
        self.call('SELECT', 15)

    def close(self):
        self.stream.close()
        self.socket.close()

    def read(self):
        kind = self.stream.read(1)
        line = self.stream.readline().removesuffix(b'\r\n')
        if kind == b'-':
            raise RuntimeError(line.decode())
        if kind == b':':
            return int(line)
        if kind == b'$':
            n = int(line)
            if n < 0:
                return None
            value = self.stream.read(n).decode()
            self.stream.read(2)
            return value
        if kind == b'*':
            return [self.read() for _ in range(int(line))]
        if kind == b'+':
            return line.decode()
        raise RuntimeError(f'Unexpected Redis response: {kind!r} {line!r}')

    def call(self, *args):
        args = [str(arg).encode() for arg in args]
        self.socket.sendall(b'*%d\r\n' % len(args) + b''.join(
            b'$%d\r\n' % len(arg) + arg + b'\r\n' for arg in args))
        return self.read()

    def lifecycle(self, op, proxy='a', token='owner-a', *args):
        return self.call('EVAL', LIFECYCLE, 0, op, proxy, token, *args)

    def player(self, op, proxy='a', token='owner-a', session='session-a', *args):
        return self.call('EVAL', PLAYER, 0, op, proxy, token, 'uuid', session, *args)


@unittest.skipUnless(PORT, 'Set REDIVELOCITY_TEST_REDIS_PORT to a disposable Redis instance')
class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.r = Redis()
        self.r.call('FLUSHDB')

    def tearDown(self):
        self.r.close()

    def login(self, proxy='a', token='owner-a', session='session-a'):
        self.assertEqual(1, self.r.player('login', proxy, token, session, 'Player', '127.0.0.2'))
        self.r.player('switch', proxy, token, session, 'lobby')

    def parallel(self, actions):
        def run(action):
            client = Redis()
            try:
                return action(client)
            finally:
                client.close()
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            return list(pool.map(run, actions))

    def test_simultaneous_startup_preserves_all_registrations(self):
        def start(i):
            def action(r):
                self.assertEqual(1, r.lifecycle('register', f'p{i}', f't{i}'))
                return r.call('EVAL', PLAYER, 0, 'login', f'p{i}', f't{i}', f'u{i}', f's{i}', 'Name', 'ip')
            return action
        self.assertEqual([1] * 32, self.parallel([start(i) for i in range(32)]))
        self.assertEqual(32, self.r.call('HLEN', 'redivelocity:proxies'))
        self.assertEqual(32, self.r.call('HLEN', 'redivelocity:player:proxies'))

    def test_duplicate_id_has_one_owner(self):
        results = self.parallel([lambda r, i=i: r.lifecycle('register', 'same', str(i)) for i in range(32)])
        self.assertEqual(1, sum(results))

    def test_simultaneous_elections_agree_and_ignore_dead_candidates(self):
        self.r.lifecycle('register', 'z')
        self.r.call('HSET', 'redivelocity:proxies', 'dead', 'dead')
        results = self.parallel([lambda r: r.lifecycle('elect') for _ in range(32)])
        self.assertEqual({'z'}, set(results))
        self.assertEqual('z', self.r.call('HGET', 'redivelocity:leader', 'leader-id'))

    def test_healthy_leader_is_retained_and_stale_leader_replaced(self):
        self.r.lifecycle('register', 'z')
        self.assertEqual('z', self.r.lifecycle('elect'))
        self.r.lifecycle('register', 'a')
        self.assertEqual('z', self.r.lifecycle('elect'))
        self.r.call('HSET', 'redivelocity:heartbeats', 'z', 1)
        self.assertEqual('a', self.r.lifecycle('elect'))

    def test_empty_registry_removes_stale_leader(self):
        self.r.call('HSET', 'redivelocity:leader', 'leader-id', 'missing')
        self.assertEqual('', self.r.lifecycle('elect'))
        self.assertIsNone(self.r.call('HGET', 'redivelocity:leader', 'leader-id'))

    def test_restart_after_crash_clears_orphan_player_fields(self):
        self.r.call('HSET', 'redivelocity:proxies', 'dead', 'dead')
        self.r.call('HSET', 'redivelocity:heartbeats', 'dead', 1)
        self.r.call('HSET', 'redivelocity:player:ips', 'orphan', 'ip')
        self.r.lifecycle('register')
        self.assertEqual(0, self.r.call('EXISTS', 'redivelocity:player:ips'))
        self.r.lifecycle('cleanup', 'dead')
        self.assertEqual(['a'], self.r.call('HKEYS', 'redivelocity:proxies'))

    def test_live_heartbeat_without_registry_is_protected(self):
        self.r.lifecycle('register')
        self.login()
        self.r.call('HDEL', 'redivelocity:proxies', 'a')
        self.assertEqual(0, self.r.lifecycle('cleanup'))
        self.r.lifecycle('register', 'b', 'owner-b')
        self.assertEqual('session-a', self.r.call('HGET', 'redivelocity:player:sessions', 'uuid'))

    def test_recovered_heartbeat_invalidates_cleanup_snapshot(self):
        self.r.lifecycle('register')
        self.login()
        self.r.call('HDEL', 'redivelocity:heartbeats', 'a')
        self.r.lifecycle('heartbeat')
        self.assertEqual(0, self.r.lifecycle('cleanup'))
        self.assertEqual('a', self.r.call('HGET', 'redivelocity:player:proxies', 'uuid'))

    def test_dead_proxy_cleanup_removes_every_player_and_proxy_field(self):
        self.r.lifecycle('register')
        self.login()
        self.r.lifecycle('count', 'a', 'owner-a', '12')
        self.r.lifecycle('server-add', 'a', 'owner-a', 'lobby', 'localhost')
        self.r.call('SADD', 'redivelocity:existing-proxy-ids', 'a')
        self.r.lifecycle('elect')
        self.r.call('HSET', 'redivelocity:heartbeats', 'a', 'invalid')
        self.assertEqual(1, self.r.lifecycle('cleanup'))
        self.assertEqual([], self.r.call('KEYS', 'redivelocity:*'))

    def test_stale_shutdown_and_writes_cannot_touch_replacement(self):
        self.r.lifecycle('register')
        self.r.call('HSET', 'redivelocity:heartbeats', 'a', 1)
        self.assertEqual(1, self.r.lifecycle('register', 'a', 'replacement'))
        self.login('a', 'replacement', 'new-session')
        for op, args in [('shutdown', ()), ('heartbeat', ()), ('count', ('99',)), ('server-add', ('old', 'address'))]:
            self.assertEqual(0, self.r.lifecycle(op, 'a', 'owner-a', *args))
        self.assertEqual('new-session', self.r.call('HGET', 'redivelocity:player:sessions', 'uuid'))

    def test_old_disconnect_does_not_delete_new_session_on_same_proxy(self):
        self.r.lifecycle('register')
        self.login()
        self.login(session='new-session')
        self.assertEqual(0, self.r.player('disconnect'))
        self.assertEqual('new-session', self.r.call('HGET', 'redivelocity:player:sessions', 'uuid'))

    def test_reconcile_removes_stale_session_but_not_replacement(self):
        self.r.lifecycle('register')
        self.login()
        self.login(session='new-session')
        self.assertEqual(0, self.r.player('reconcile'))
        self.assertEqual('new-session', self.r.call('HGET', 'redivelocity:player:sessions', 'uuid'))
        self.assertEqual(0, self.r.player('reconcile', token='old-owner', session='new-session'))
        self.assertEqual(1, self.r.player('reconcile', session='new-session'))
        self.assertEqual([], self.r.call('KEYS', 'redivelocity:player:*'))

    def test_cleanup_and_reconnect_preserve_new_proxy_session(self):
        self.r.lifecycle('register')
        self.r.lifecycle('register', 'b', 'owner-b')
        for _ in range(30):
            self.r.lifecycle('register')
            self.login()
            self.r.call('HSET', 'redivelocity:heartbeats', 'a', 1)
            self.parallel([
                lambda r: r.lifecycle('cleanup'),
                lambda r: r.player('login', 'b', 'owner-b', 'session-b', 'NewName', 'new-ip'),
            ])
            self.assertEqual('b', self.r.call('HGET', 'redivelocity:player:proxies', 'uuid'))
            self.assertEqual('new-ip', self.r.call('HGET', 'redivelocity:player:ips', 'uuid'))

    def test_disconnect_deletes_ip_and_late_switch_cannot_resurrect_data(self):
        self.r.lifecycle('register')
        self.login()
        self.assertEqual(1, self.r.player('disconnect'))
        self.assertEqual(0, self.r.player('switch', 'a', 'owner-a', 'session-a', 'late-server'))
        self.assertEqual([], self.r.call('KEYS', 'redivelocity:player:*'))

    def test_shutdown_racing_startup_preserves_new_proxy(self):
        self.r.lifecycle('register')
        self.parallel([lambda r: r.lifecycle('shutdown'), lambda r: r.lifecycle('register', 'b', 'owner-b')])
        self.assertEqual(['b'], self.r.call('HKEYS', 'redivelocity:proxies'))
        self.assertEqual('owner-b', self.r.call('HGET', 'redivelocity:proxy:instances', 'b'))

    def test_orphan_server_hash_can_be_cleaned_without_registry(self):
        self.r.call('HSET', 'redivelocity:registered-servers:orphan', 'lobby', 'address')
        self.r.lifecycle('cleanup', 'orphan')
        self.assertEqual(0, self.r.call('EXISTS', 'redivelocity:registered-servers:orphan'))

    def test_configuration_survives_startup_and_shutdown(self):
        self.r.call('SET', 'redivelocity:login-config:maintenance-enabled', 'true')
        self.r.lifecycle('register')
        self.r.lifecycle('shutdown')
        self.assertEqual('true', self.r.call('GET', 'redivelocity:login-config:maintenance-enabled'))

    def test_partial_player_data_is_removed_while_other_proxies_are_live(self):
        self.r.lifecycle('register')
        for field in ('servers', 'names', 'sessions', 'ips'):
            self.r.call('HSET', 'redivelocity:player:' + field, 'orphan', 'value')
        self.assertEqual(1, self.r.lifecycle('orphan-player', 'orphan'))
        self.assertEqual([], self.r.call('KEYS', 'redivelocity:player:*'))

    def test_orphan_sweep_cannot_delete_concurrent_login(self):
        self.r.lifecycle('register')
        for _ in range(30):
            self.r.player('disconnect')
            self.parallel([
                lambda r: r.lifecycle('orphan-player', 'uuid'),
                lambda r: r.player('login', 'a', 'owner-a', 'session-a', 'Name', 'ip'),
            ])
            self.assertEqual('session-a', self.r.call('HGET', 'redivelocity:player:sessions', 'uuid'))
            self.assertEqual('ip', self.r.call('HGET', 'redivelocity:player:ips', 'uuid'))

    def limit(self, value, request='command', enabled=True):
        return json.loads(self.r.call('EVAL', MAX_PLAYERS, 0, value, 10,
                                      str(enabled).lower(), request))

    def populate_limit_players(self):
        self.r.lifecycle('register')
        self.r.lifecycle('register', 'b', 'owner-b')
        self.r.call('SET', 'redivelocity:login-config:max-players', 10)
        for index in range(5):
            proxy = 'a' if index % 2 == 0 else 'b'
            self.r.call('EVAL', PLAYER, 0, 'login', proxy, 'owner-' + proxy,
                        f'u{index}', f's{index}', 'Name', 'ip', str(index == 4).lower())
            # Deterministic times for selection assertions, independent of execution speed.
            self.r.call('ZADD', 'redivelocity:player:joined-at', index, f'u{index}')

    def test_lower_limit_selects_newest_across_proxies_and_skips_bypass(self):
        self.populate_limit_players()
        self.assertEqual([['u3', 's3'], ['u2', 's2']], self.limit(3))

    def test_same_command_keeps_selection_after_disconnect_and_reconnect(self):
        self.populate_limit_players()
        selected = self.limit(3)
        self.r.call('EVAL', PLAYER, 0, 'disconnect', 'b', 'owner-b', 'u3', 's3')
        self.r.call('EVAL', PLAYER, 0, 'login', 'b', 'owner-b', 'u3', 'replacement', 'Name', 'ip')
        self.assertEqual(selected, self.limit(3))
        self.assertEqual('replacement', self.r.call('HGET', 'redivelocity:player:sessions', 'u3'))
        self.assertGreater(self.r.call('TTL', 'redivelocity:max-player-plan:command'), 0)

    def test_concurrent_proxies_share_one_selection(self):
        self.populate_limit_players()
        results = self.parallel([
            lambda r: r.call('EVAL', MAX_PLAYERS, 0, 3, 10, 'true', 'shared')
            for _ in range(16)
        ])
        self.assertEqual(1, len(set(results)))
        self.assertEqual([['u3', 's3'], ['u2', 's2']], json.loads(results[0]))

    def test_equal_higher_unlimited_and_disabled_limits_do_not_kick(self):
        for value, enabled in [(10, True), (20, True), (-1, True), (0, False)]:
            with self.subTest(value=value, enabled=enabled):
                self.populate_limit_players()
                self.assertEqual([], self.limit(value, f'{value}-{enabled}', enabled))

    def test_zero_limit_keeps_bypass_players_even_if_still_over_capacity(self):
        self.populate_limit_players()
        self.assertEqual([['u3', 's3'], ['u2', 's2'], ['u1', 's1'], ['u0', 's0']], self.limit(0))

    def test_bypass_refresh_is_session_guarded_and_does_not_change_join_time(self):
        self.r.lifecycle('register')
        self.login()
        joined = self.r.call('ZSCORE', 'redivelocity:player:joined-at', 'uuid')
        self.assertGreater(float(joined), 0)
        self.assertEqual(1, self.r.player('bypass', 'a', 'owner-a', 'session-a', 'true'))
        self.assertEqual(0, self.r.player('bypass', 'a', 'owner-a', 'old-session', 'false'))
        self.assertEqual('true', self.r.call('HGET', 'redivelocity:player:max-players-bypass', 'uuid'))
        self.r.player('switch', 'a', 'owner-a', 'session-a', 'other-server')
        self.assertEqual(joined, self.r.call('ZSCORE', 'redivelocity:player:joined-at', 'uuid'))

    def test_cleanup_removes_orphan_join_time_and_bypass(self):
        self.r.lifecycle('register')
        self.r.call('ZADD', 'redivelocity:player:joined-at', 1, 'orphan')
        self.r.call('HSET', 'redivelocity:player:max-players-bypass', 'orphan', 'true')
        self.assertEqual(1, self.r.lifecycle('orphan-player', 'orphan'))
        self.assertEqual([], self.r.call('KEYS', 'redivelocity:player:*'))
