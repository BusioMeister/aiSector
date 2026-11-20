"""Test the experimental 26.3 proxy on private loopback ports without stopping aiSector."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
import time
import uuid
import zipfile

from layout_smoke import INPUT, SERVER_HASHES, world_files
from official_client import prepare, arguments
from staging_smoke import JAVA, REPO, TEMP, command, listening


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--proxy-plugin", type=Path, help="Override the InstantTransfer proxy plugin for an A/B run")
    parser.add_argument("--sector-platform", action="store_true", help="Walk across the real aiSector border on a 50x50 platform")
    parser.add_argument("--two-clients", action="store_true", help="Exercise nearby player head updates during arrival")
    parser.add_argument("--guild-create", action="store_true", help="Verify creation feedback without WorldEdit")
    parser.add_argument("--map-edge", action="store_true", help="Verify shared and outer bossbars plus map edge repulsion")
    parser.add_argument("--cooldown-push", action="store_true", help="Try to turn back immediately after a physical transfer")
    parser.add_argument("--boat-boundary", action="store_true", help="Verify mounting and destroying boats at the seam")
    parser.add_argument("--network-tab", action="store_true", help="Verify TAB content and private welcome across sectors")
    args = parser.parse_args()
    if (args.two_clients or args.guild_create or args.map_edge or args.cooldown_push or args.boat_boundary or args.network_tab) and not args.sector_platform:
        parser.error("These checks require --sector-platform")
    starts = {"GameWest": 18, "GameEast": 24, "Spawn": 29}
    ports = {"GameWest": 25576, "GameEast": 25577, "Spawn": 25578, "Proxy": 25575}
    if any(listening(port) for port in ports.values()):
        raise RuntimeError("Expected unused lab ports")
    review = TEMP / "instant-transfer-review"
    proxy_jar = TEMP / "velocity-instant-263/proxy/build/libs/velocity-proxy-4.3.1-all.jar"
    paper = INPUT / "paper.verified.jar"
    archive = TEMP / "aisector-map-package-f7vr0y6u/aisector-mapa-1000x1000.zip"
    for path, expected in ((paper, SERVER_HASHES["paper"]),
                           (archive, "3057ee5ad438058e500bf381599372c723c007fabbc537c6c0d783efd2f39cc0"),
                           (review / "packetevents-spigot-2.14.0.jar", "060087c58ec268eae7dd0eb1f17ac3bea1eaadda1693414141ff64e506091bd7"),
                           (review / "packetevents-velocity-2.14.0.jar", "0772f64b51aaf79132c76bb1503707ea0d35433713d5312e67401eaa599d271b")):
        with path.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise RuntimeError("Input hash mismatch: " + path.name)
    run = Path(tempfile.mkdtemp(prefix="instant-263-smoke-", dir=TEMP))
    print("INSTANT_LAB_EVIDENCE", run, flush=True)
    name = "RwSInst" + uuid.uuid4().hex[:6]
    ident = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3)
    peer_name = "RwSPeer" + uuid.uuid4().hex[:6]
    peer_ident = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + peer_name).encode()).digest(), version=3)
    prepared = prepare(TEMP / "aisector-official-client-26.3")
    command([JAVA / "javac.exe", "--add-modules", "jdk.attach", "-cp", prepared[1], "-d", run,
             REPO / "tools/runtime/AttachSmoke.java", REPO / "tools/runtime/ClientControlAgent.java"])
    manifest = run / "client.mf"
    manifest.write_text("Manifest-Version: 1.0\nAgent-Class: ClientControlAgent\n\n")
    agent = run / "client.jar"
    command([JAVA / "jar.exe", "cfm", agent, manifest, "-C", run, "ClientControlAgent.class"])
    processes, logs = {}, {}
    client = None
    peer = None
    mongo = None
    redis_started = False
    env = os.environ.copy()
    wsl = ["wsl", "-d", "Ubuntu", "-u", "root", "--"]

    def lab_redis(*args):
        return subprocess.check_output([*wsl, "redis-cli", "-h", redis_host, "-p", "6380", *args],
                                       env={**env, "WSLENV": "REDISCLI_AUTH", "REDISCLI_AUTH": redis_password},
                                       text=True, timeout=10).strip()

    def log(name):
        return (run / (name + ".log")).read_text(encoding="utf-8", errors="replace")

    def send(name, text):
        processes[name].stdin.write(text + "\n")
        processes[name].stdin.flush()

    def wait(condition, label, seconds=100):
        until = time.monotonic() + seconds
        while not condition():
            if any(p.poll() is not None for p in processes.values()) or time.monotonic() >= until:
                raise RuntimeError("Failed: " + label + " (" + str(run) + ")")
            time.sleep(.3)

    def start(name, directory, jar, options, *args):
        output = (run / (name + ".log")).open("w", encoding="utf-8")
        logs[name] = output
        processes[name] = subprocess.Popen([JAVA / "java.exe", "-Xms256M", "-Xmx1500M",
                "-XX:ActiveProcessorCount=3", *options, "-jar", jar, *args], cwd=directory,
                stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True, env=env)

    def control(action, value="", actor=None):
        output = run / (uuid.uuid4().hex + ".result")
        payload = action + "|" + base64.b64encode(value.encode()).decode() + "|" + str(output)
        command([JAVA / "java.exe", "--add-modules", "jdk.attach", "-cp", run, "AttachSmoke",
                 (actor or client).pid, agent, payload])
        wait(output.exists, "agent result", 15)
        if action in {"trace-command-walk", "trace-border-walk", "trace-edge-walk", "trace-cooldown-push", "trace-boat-walk"}:
            return output.read_text(), output.with_name(output.name + ".walk").read_text()
        return output.read_text(encoding="utf-8")

    def position(role, player_name=name):
        pattern = rf"{player_name} has the following entity data: \[([-.\d]+)d, ([-.\d]+)d, ([-.\d]+)d\]"
        def positions():
            return re.findall(pattern, re.sub(r"\x1b\[[0-9;]*m", "", log(role)))
        seen = len(positions())
        send(role, f"data get entity {player_name} Pos")
        wait(lambda: len(positions()) > seen, "read authoritative position", 10)
        return tuple(float(n) for n in positions()[-1])

    try:
        if args.sector_platform:
            import ipaddress
            if listening(27027):
                raise RuntimeError("Expected an unused isolated Mongo port")
            redis_host = subprocess.check_output([*wsl, "hostname", "-I"], text=True).split()[0]
            if not ipaddress.IPv4Address(redis_host).is_private or redis_host.startswith("127."):
                raise RuntimeError("Could not reserve a distinct private WSL bind for Redis")
            redis_password = secrets.token_hex(24)
            command([*wsl, "redis-server", "--port", "6380", "--bind", redis_host, "--protected-mode", "yes",
                     "--requirepass", redis_password, "--save", "", "--appendonly", "no", "--daemonize", "yes",
                     "--pidfile", f"/var/run/aisector-platform-{name}.pid"])
            assert lab_redis("PING") == "PONG"
            redis_started = True
            mongo_bin = Path(os.environ["ProgramFiles"]) / "MongoDB/Server/8.2/bin/mongod.exe"
            (run / "mongo").mkdir()
            logs["mongo"] = (run / "mongo.log").open("w", encoding="utf-8")
            mongo = subprocess.Popen([mongo_bin, "--bind_ip", "127.0.0.1", "--port", "27027",
                                      "--dbpath", run / "mongo", "--wiredTigerCacheSizeGB", "0.3"],
                                     stdout=logs["mongo"], stderr=subprocess.STDOUT)
            wait(lambda: listening(27027) and mongo.poll() is None, "isolated Mongo", 25)
            env.update(AISECTOR_REDIS_HOST=redis_host, AISECTOR_REDIS_PORT="6380", AISECTOR_REDIS_PASSWORD=redis_password,
                       AISECTOR_MONGODB_URI="mongodb://127.0.0.1:27027", AISECTOR_MONGODB_DATABASE="aisector_staging",
                       AISECTOR_LOCKED_SPAWN="Spawn")
        with zipfile.ZipFile(archive) as data:
            for sector in starts:
                base = run / sector
                world_files(base, ports[sector], 2718281828)
                data.extractall(run, [path for path in data.namelist() if path.startswith(sector + "/world/")])
                plugins = base / "plugins"
                plugins.mkdir()
                players = [{"uuid": str(ident), "name": name}]
                if args.two_clients:
                    players.append({"uuid": str(peer_ident), "name": peer_name})
                (base / "whitelist.json").write_text(json.dumps(players))
                shutil.copy2(review / "packetevents-spigot-2.14.0.jar", plugins)
                shutil.copy2(review / "backend-agent/spigot/target/original-instanttransfer-agent-0.1.0.jar",
                             plugins / "InstantTransferAgent-0.1.0.jar")
                if args.sector_platform:
                    config = plugins / "aiSECTOR"
                    config.mkdir()
                    (config / "config.yml").write_text(
                        f'this-sector-name: "{sector}"\nredis:\n  host: "{redis_host}"\n  port: 6380\n'
                        f'  password: "{redis_password}"\nmongodb:\n  uri: "mongodb://127.0.0.1:27027"\n'
                        '  database: "aisector_staging"\n')
                    (config / "sectors.yml").write_bytes(data.read("sectors.yml"))
                    shutil.copy2(REPO / "aiSector/target/aiSector-2.0.0-SNAPSHOT.jar", plugins / "aiSector.jar")
                start(sector, base, paper, (), "--nogui")
        base = run / "proxy"
        (base / "plugins").mkdir(parents=True)
        shutil.copy2(review / "packetevents-velocity-2.14.0.jar", base / "plugins")
        shutil.copy2(args.proxy_plugin or review / "proxy-plugin/target/InstantTransfer-0.1.0.jar",
                     base / "plugins/InstantTransfer-0.1.0.jar")
        if args.sector_platform:
            shutil.copy2(REPO / "VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar", base / "plugins/VelocityPlugin.jar")
        first = "Spawn" if args.sector_platform else "GameWest"
        (base / "velocity.toml").write_text('config-version = "2.9"\nbind = "127.0.0.1:25575"\nonline-mode = false\n'
            'force-key-authentication = false\nplayer-info-forwarding-mode = "none"\n[servers]\n'
            f'GameWest = "127.0.0.1:25576"\nGameEast = "127.0.0.1:25577"\ntry = ["{first}"]\n'
            'Spawn = "127.0.0.1:25578"\n'
            f'[forced-hosts]\n"127.0.0.1" = ["{first}"]\n')
        start("Proxy", base, proxy_jar, ("-Dvelocity.seamlessSwitch=true", "-Dvelocity.seamlessSpawn=true"))
        wait(lambda: all("Done (" in log(sector) and listening(port) for sector, port in ports.items()), "startup")
        if any("Error occurred while enabling" in log(sector) for sector in starts):
            raise RuntimeError("Backend agent failed to enable")
        for sector, x in starts.items():
            if args.sector_platform:
                if sector == "Spawn":
                    send(sector, "gamerule minecraft:respawn_radius 0")
                    send(sector, "setworldspawn 2050 126 2050 180 0")
                    wait(lambda: "spawn point" in log(sector).lower(), "real Spawn point", 20)
                    continue
                send(sector, "forceload add -25 -25 24 24")
                wait(lambda: "force loaded" in log(sector).lower(), f"platform tickets on {sector}", 20)
                time.sleep(2)
                send(sector, "fill -25 140 -25 24 140 24 minecraft:stone")
                wait(lambda: "successfully filled" in log(sector).lower(), f"50x50 platform on {sector}", 20)
                send(sector, "fill -25 141 -25 24 143 24 minecraft:air")
                wait(lambda: log(sector).lower().count("successfully filled") >= 2
                     or "no blocks were filled" in log(sector).lower(), f"platform headroom on {sector}", 20)
                continue
            send(sector, "forceload add 16 0 31 115")
            wait(lambda: "force loaded" in log(sector).lower() or "marked" in log(sector).lower(), f"chunk tickets on {sector}", 20)
            time.sleep(1)
            send(sector, "fill 16 139 0 31 139 115 stone")
            wait(lambda: "successfully filled" in log(sector).lower(), f"walkway on {sector}", 20)
            send(sector, "fill 16 140 0 31 144 115 air")
            wait(lambda: log(sector).lower().count("successfully filled") >= 2
                 or "no blocks were filled" in log(sector).lower(), f"clear walkway on {sector}", 20)
            send(sector, "gamerule minecraft:respawn_radius 0")
            send(sector, f"setworldspawn {x} 140 30 0 0")
            wait(lambda: "spawn point" in log(sector).lower(), f"spawn point on {sector}", 20)
        opts = arguments(prepared, run / name, name)
        assert opts[-1] == "127.0.0.1:25565"
        opts[-1] = "127.0.0.1:25575"
        logs["client"] = (run / "client.log").open("w", encoding="utf-8")
        client = subprocess.Popen(opts, cwd=run, stdout=logs["client"], stderr=subprocess.STDOUT)
        wait(lambda: name + " -> " + first + " has connected" in log("Proxy"), "first join")
        wait(lambda: control("client-state") == "PLAY", "client ready after initial loading", 30)
        if args.sector_platform:
            lab_redis("SET", "aisector:global_spawn", json.dumps(
                dict(sector="Spawn", x=2050.5, y=126, z=2050.5, yaw=180, pitch=0)))
            send("Spawn", f"op {name}")
            wait(lambda: f"Made {name} a server operator" in log("Spawn"), "grant test-only OP", 10)
            if args.network_tab:
                def tab_ready():
                    text = control("tab-content")
                    return all(word in text for word in (name, "Ranga:", "Zabójstwa:", "Śmierci:",
                                                          "Ranking gildii:", "TOP 20 GRACZY", "TOP 20 GILDII"))
                wait(tab_ready, "TAB on Spawn", 15)
                assert log("client").count(f"Witaj, {name}!") == 1
            control("command", "god")
            send("Spawn", f"minecraft:tp {name} 2050.5 126 2048.5 180 0")
            time.sleep(1)
            trace = control("trace-block", "2050,127,2046")
            assert len(trace.splitlines()) == 1 and trace.endswith(" PLAY"), trace
            role = next((sector for sector in ("GameEast", "GameWest")
                         if f"{name} -> {sector} has connected" in log("Proxy")), None)
            assert role, "Sponge button did not move the test player"
            results = {"sponge": {"target": role, "screens": trace}}
            for index in range(0 if args.cooldown_push or args.boat_boundary else 4):
                west = role == "GameWest"
                dest, yaw, side = ("GameEast", -90, 1) if west else ("GameWest", 90, -1)
                send(role, f"minecraft:tp {name} {-.85 if west else .85} 141 0.5 {yaw} 0")
                pos = position(role)
                assert abs(pos[0] - (-.85 if west else .85)) < .10 and abs(pos[1] - 141) < .05, (role, pos)
                time.sleep(.5)
                if index < 2:
                    control("screenshot")
                if args.map_edge:
                    assert dest in control("boundary-title"), "Missing destination sector in border bossbar"
                before = log("Proxy").count(f"{name} -> {dest} has connected")
                print("PLATFORM_WALK", role, dest, flush=True)
                trace, walk = control("trace-border-walk", str(yaw))
                (run / f"platform-{index + 1}.txt").write_text(walk)
                assert len(trace.splitlines()) == 1 and trace.endswith(" PLAY"), trace
                assert log("Proxy").count(f"{name} -> {dest} has connected") == before + 1
                samples = [list(map(float, row.split())) for row in walk.splitlines()]
                x, y, z = position(dest)
                assert 3 <= x * side <= 24 and abs(y - 141) < .05 and abs(z - .5) < 1, (dest, x, y, z)
                first_step = next(i for i, row in enumerate(samples) if row[1] * side >= .2)
                assert all(row[1] * side >= -.2 for row in samples[first_step:]), (dest, walk)
                assert any(r[5] == 4096 and abs((r[4] - side * 2048) - (-side * 2)) < .01
                           for r in samples[first_step:]), (dest, walk)
                results[str(index + 1)] = {"target": dest, "screens": trace, "server_x": x,
                                           "server_y": y, "server_z": z, "border_confirmed": True}
                role = dest
            if args.network_tab:
                wait(tab_ready, "TAB after transfer", 15)
                assert log("client").count(f"Witaj, {name}!") == 1
                assert not any(phrase in log("client") for phrase in (" joined the game", " left the game"))
                (run / "network-tab.txt").write_text(control("tab-content"), encoding="utf-8")
                results["network_tab"] = {"private_welcome": True, "top_20": True}
                (run / "results.json").write_text(json.dumps(results, indent=2))
                print("NETWORK_TAB_OK", run, flush=True)
                return
            if args.boat_boundary:
                time.sleep(1)
                side = -1 if role == "GameWest" else 1
                yaw = -90 if side < 0 else 90
                other = "GameEast" if side < 0 else "GameWest"
                send(role, "fill -12 140 -2 12 140 2 minecraft:blue_ice")
                send(role, f"minecraft:tp {name} {2.0 * side} 141 0.5 {yaw} 0")
                wait(lambda: abs(position(role)[0] - 2 * side) < .1, "boat test position at seam", 10)
                send(role, f'summon minecraft:oak_chest_boat {2.0 * side} 141 0.5 {{Tags:["boundary_lab"]}}')
                send(role, f'ride {name} mount @e[tag=boundary_lab,limit=1]')
                wait(lambda: "couldn't start riding" in log(role), "mounting blocked at seam", 10)
                send(role, 'kill @e[tag=boundary_lab]')
                send(role, f"minecraft:tp {name} {6.0 * side} 141 0.5 {yaw} 0")
                wait(lambda: abs(position(role)[0] - 6 * side) < .1, "boat test position outside restricted band", 10)
                send(role, f'summon minecraft:oak_chest_boat {6.0 * side} 141 0.5 {{Tags:["boundary_lab"],Rotation:[{yaw}f,0f]}}')
                send(role, 'item replace entity @e[tag=boundary_lab,limit=1] container.0 with minecraft:diamond')
                send(role, f'ride {name} mount @e[tag=boundary_lab,limit=1]')
                wait(lambda: "started riding" in log(role), "mounting allowed away from seam", 10)
                before = log("Proxy").count(f"{name} -> {other} has connected")
                send(role, f'data merge entity @e[tag=boundary_lab,limit=1] {{Motion:[{-2.0 * side}d,0d,0d]}}')
                trace, walk = control("trace-boat-walk", str(yaw))
                (run / "boat-boundary.txt").write_text(walk)
                assert trace == "0 PLAY" and log("Proxy").count(f"{name} -> {other} has connected") == before
                samples = [list(map(float, row.split())) for row in walk.splitlines()]
                approach = next(i for i, row in enumerate(samples) if abs(row[1]) < 1)
                assert any(row[1] * side >= 3 for row in samples[approach:]), walk
                send(role, 'execute unless entity @e[tag=boundary_lab] run say BOAT_BOUNDARY_DESTROYED')
                wait(lambda: "BOAT_BOUNDARY_DESTROYED" in log(role), "verify boat removed", 10)
                send(role, 'execute if entity @e[type=minecraft:item,nbt={Item:{id:"minecraft:diamond"}}] run say BOAT_BOUNDARY_CARGO')
                wait(lambda: "BOAT_BOUNDARY_CARGO" in log(role), "verify dropped cargo", 10)
                x, _, _ = position(role)
                assert x * side > .5, x
                results["boat_boundary"] = {"screens": trace, "sector": role, "server_x": x}
                (run / "results.json").write_text(json.dumps(results, indent=2))
                print("BOAT_BOUNDARY_OK", run, flush=True)
                return
            if args.cooldown_push:
                west = role == "GameWest"
                dest, yaw, side = ("GameEast", -90, 1) if west else ("GameWest", 90, -1)
                send(role, f"minecraft:tp {name} {-.85 if west else .85} 141 0.5 {yaw} 0")
                assert abs(position(role)[0] - (-.85 if west else .85)) < .05
                back = log("Proxy").count(f"{name} -> {role} has connected")
                trace, walk = control("trace-cooldown-push", str(yaw))
                (run / "cooldown-push.txt").write_text(walk)
                assert trace == "0 PLAY" and log("Proxy").count(f"{name} -> {role} has connected") == back
                x, y, z = position(dest)
                assert 4.75 <= x * side <= 6 and abs(y - 141) < .05 and abs(z - .5) < .1, (dest, x, y, z)
                samples = [list(map(float, row.split())) for row in walk.splitlines()]
                closest = next(i for i, row in enumerate(samples) if row[1] * side >= .25)
                landing = samples[closest:]
                increments = [abs(b[1] - a[1]) for a, b in zip(landing, landing[1:])]
                assert max(increments) < 1.5 and sum(step > .1 for step in increments) >= 5, landing
                results["cooldown_push"] = {"screens": trace, "target": dest, "server_x": x, "smooth": True}
                (run / "results.json").write_text(json.dumps(results, indent=2))
                print("COOLDOWN_PUSH_OK", run, flush=True)
                return
            if args.map_edge:
                edge_x = -8 if role == "GameWest" else 8
                send(role, f"forceload add {edge_x} -502 {edge_x + 2} -480")
                time.sleep(2)
                send(role, f"fill {edge_x} 140 -500 {edge_x + 2} 140 -480 minecraft:stone")
                send(role, f"fill {edge_x} 141 -500 {edge_x + 2} 143 -480 minecraft:air")
                time.sleep(1)
                send(role, f"minecraft:tp {name} {edge_x + .5} 141 -497.5 180 0")
                assert abs(position(role)[2] - (-497.5)) < .05
                assert "Granica mapy" in control("boundary-title")
                trace, walk = control("trace-edge-walk", "180")
                (run / "map-edge-walk.txt").write_text(walk)
                samples = [row.split() for row in walk.splitlines()]
                z = [float(row[3]) for row in samples]
                assert trace == "0 PLAY" and any(row[6] == "true" for row in samples)
                assert all(value >= -500.1 for value in z) and any(b - a > .12 for a, b in zip(z, z[1:])), z
                assert position(role)[2] >= -500
                results["map_edge"] = {"bossbar": True, "actionbar": True, "repelled": True}
                (run / "results.json").write_text(json.dumps(results, indent=2))
                print("MAP_EDGE_REPULSION_OK", run, flush=True)
                return
            if args.guild_create:
                send(role, f"op {name}")
                wait(lambda: f"Made {name} a server operator" in log(role), "guild test-only OP", 10)
                assert control("command", "g zaloz LAB Platform Guild") == "OK"
                wait(lambda: "Stworzy" in log("client") and "[LAB] Platform Guild" in log("client"),
                     "local guild creation confirmation", 12)
                wait(lambda: "[GILDIA]" in log("client") and "wklejono" in log("client"),
                     "global guild announcement and optional schematic warning", 12)
                assert "NoClassDefFoundError" not in log(role)
                results["guild_creation"] = {"confirmation": True, "without_worldedit": True}
                (run / "results.json").write_text(json.dumps(results, indent=2))
                print("GUILD_CREATION_WITHOUT_WORLDEDIT_OK", run, flush=True)
                return
            if args.two_clients:
                peer_opts = arguments(prepared, run / peer_name, peer_name)
                peer_opts[-1] = "127.0.0.1:25575"
                logs["peer"] = (run / "peer.log").open("w", encoding="utf-8")
                peer = subprocess.Popen(peer_opts, cwd=run, stdout=logs["peer"], stderr=subprocess.STDOUT)
                wait(lambda: f"{peer_name} -> Spawn has connected" in log("Proxy"), "second player joins")
                time.sleep(3)
                send("Spawn", f"minecraft:tp {peer_name} 2052.5 126 2052.5 0 0")
                time.sleep(.5)
                assert control("client-state", actor=peer) == "PLAY"
                control("rotate-head", actor=peer)
            trace, walk = control("trace-command-walk", "spawn|2050.5")
            (run / "spawn-walk.txt").write_text(walk)
            assert len(trace.splitlines()) == 1 and trace.endswith(" PLAY"), trace
            x, y, z = position("Spawn")
            assert 2049 <= x <= 2052 and abs(y - 126) < .05 and 2055 <= z <= 2062, (x, y, z)
            samples = [list(map(float, row.split())) for row in walk.splitlines()]
            assert samples and samples[0][0] < 2500
            assert all(new[1] >= old[1] - .5 for old, new in zip(samples, samples[1:])), walk
            if args.two_clients:
                assert control("client-state", actor=peer) == control("client-state") == "PLAY"
            results["spawn"] = {"screens": trace, "server_x": x, "server_y": y, "server_z": z}
            if args.two_clients:
                results["spawn"]["two_clients"] = True
                counts = {sector: log("Proxy").count(f"{name} -> {sector} has connected")
                          for sector in ("GameWest", "GameEast")}
                send("Spawn", f"minecraft:tp {name} 2050.5 126 2048.5 180 0")
                time.sleep(1)
                assert control("trace-block", "2050,127,2046") == "0 PLAY"
                game = next((sector for sector, count in counts.items()
                             if log("Proxy").count(f"{name} -> {sector} has connected") > count), None)
                assert game, "Operator did not leave Spawn using the button"
                send(game, f"op {name}")
                wait(lambda: f"Made {name} a server operator" in log(game), "game-sector OP", 10)
                assert control("command", f"s {peer_name}") == "OK"
                wait(lambda: f"{peer_name} -> {game} has connected" in log("Proxy"), "admin summon from Spawn", 12)
                time.sleep(2)
                assert control("client-state", actor=peer) == "PLAY"
                operator = position(game)
                summoned = position(game, peer_name)
                assert sum((a - b) ** 2 for a, b in zip(operator, summoned)) < 9, (operator, summoned)
                results["summon"] = {"target": game, "non_operator_summoned": True}
                send(game, f"op {peer_name}")
                wait(lambda: f"Made {peer_name} a server operator" in log(game), "test-only TPA permission", 10)
                assert control("trace-command", "spawn", actor=peer) == "0 PLAY"
                send("Spawn", f"op {peer_name}")
                wait(lambda: f"Made {peer_name} a server operator" in log("Spawn"), "test-only Spawn TPA permission", 10)
                count = log("Proxy").count(f"{peer_name} -> {game} has connected")
                assert control("command", f"tpa {name}", actor=peer) == "OK"
                time.sleep(1)
                assert log("Proxy").count(f"{peer_name} -> {game} has connected") == count
                assert control("command", "tpaccept") == "OK"
                wait(lambda: log("Proxy").count(f"{peer_name} -> {game} has connected") == count + 1,
                     "accepted TPA exits Spawn", 20)
                assert control("client-state", actor=peer) == "PLAY"
                results["tpa"] = {"target": game, "accepted_before_transfer": True}
            (run / "results.json").write_text(json.dumps(results, indent=2))
            print("REAL_SECTOR_PLATFORM_OK", run, flush=True)
            return
        results = {}
        for index, target in enumerate(("GameEast", "Spawn", "GameWest", "GameEast", "GameWest")):
            connection = name + " -> " + target + " has connected"
            before = log("Proxy").count(connection)
            trace, walk = control("trace-command-walk", f"server {target}|{starts[target] + .5}")
            print(target, trace, sep="\n", flush=True)
            assert len(trace.splitlines()) == 1 and trace.endswith(" PLAY"), trace
            assert log("Proxy").count(connection) == before + 1
            trajectory = [row.split() for row in walk.splitlines()]
            _, _, z1 = position(target)
            assert trajectory and float(trajectory[0][0]) < 2500, (target, trajectory[:2])
            zs = [float(row[1]) for row in trajectory]
            assert z1 - zs[0] >= 5, (target, zs[0], z1, trajectory[-5:])
            first_two_seconds = [float(row[1]) for row in trajectory if float(row[0]) - float(trajectory[0][0]) <= 1800]
            assert first_two_seconds[-1] - zs[0] >= 1, (target, first_two_seconds)
            assert all(new >= old - 1 for old, new in zip(zs, zs[1:])), (target, zs)
            results[str(index + 1)] = {"target": target, "screens": trace, "confirmed_walk_blocks": z1 - zs[0]}
        assert log("Proxy").count("sent PlayerLoaded for " + name) == len(results)
        (run / "results.json").write_text(json.dumps(results, indent=2))
        print("NO_LOADING_SCREENS_OBSERVED", run)
    finally:
        if peer is not None and peer.poll() is None:
            try:
                control("exit", actor=peer)
                peer.wait(timeout=10)
            except Exception:
                if peer.poll() is None: peer.terminate(); peer.wait(timeout=10)
        if client is not None and client.poll() is None:
            try:
                control("exit")
                client.wait(timeout=10)
            except Exception:
                if client.poll() is None: client.terminate(); client.wait(timeout=10)
        for role, process in reversed(processes.items()):
            if process.poll() is None:
                try:
                    send(role, "shutdown" if role == "Proxy" else "stop")
                    process.wait(timeout=65)
                except (OSError, subprocess.TimeoutExpired):
                    print("Lab process did not stop:", role, process.pid, flush=True)
        for stream in logs.values():
            stream.close()
        if redis_started:
            try:
                lab_redis("SHUTDOWN", "NOSAVE")
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                print("Could not stop isolated Redis", flush=True)
        if mongo is not None and mongo.poll() is None:
            mongo.terminate()
            mongo.wait(timeout=15)


if __name__ == "__main__":
    main()
