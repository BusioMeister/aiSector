"""Guarded, local staging smoke with verified fresh backups and console-owned shutdown."""
import hashlib
import json
import os
import shutil
import socket
import subprocess
import time
import sys
import uuid
import base64
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEMP = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
ROOT = TEMP / "aisector-staging"
JAVA = Path(r"C:\Program Files\Java\jdk-25.0.2\bin")
RUN = TEMP / ("aisector-final-smoke-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
SERVERS = [("Sector1", ROOT, 25566), ("Sector2", ROOT / "paper-sector2", 25567),
           ("Sector3", ROOT / "paper-sector3", 25568), ("Velocity", ROOT / "proxy", 25565)]


def listening(port):
    with socket.socket() as connection:
        connection.settimeout(1)
        return connection.connect_ex(("127.0.0.1", port)) == 0


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def command(args):
    return subprocess.run([str(x) for x in args], check=True, capture_output=True, text=True)


def redis(*args):
    return command(["wsl", "-d", "Ubuntu", "-u", "root", "--", "redis-cli", "-p", "6380", *args]).stdout.strip()


def main():
    player_smoke = "--players" in sys.argv
    prepared_client = None
    names = ["RwS" + os.urandom(3).hex() + suffix for suffix in ("A", "B")]
    if player_smoke:
        import official_client
        prepared_client = official_client.prepare(TEMP / "aisector-official-client-26.3")
    if any(listening(port) for _, _, port in SERVERS) or listening(6380):
        raise RuntimeError("Expected stopped staging; refusing to reuse another process")
    if not listening(27017):
        raise RuntimeError("MongoDB unavailable; do not start staging")
    proxy_config = (ROOT / "proxy/velocity.toml").read_text(encoding="utf-8")
    if 'bind = "127.0.0.1:25565"' not in proxy_config or 'online-mode = false' not in proxy_config:
        raise RuntimeError("Expected loopback-only offline staging proxy; refusing to alter its access policy")
    RUN.mkdir()
    hashes = {}
    for name, directory, _ in SERVERS[:-1]:
        properties = dict(line.split("=", 1) for line in (directory / "server.properties").read_text(encoding="utf-8").splitlines()
                          if "=" in line and not line.lstrip().startswith("#"))
        if properties.get("server-ip") != "127.0.0.1" or properties.get("online-mode") != "false":
            raise RuntimeError("Expected loopback-only offline staging backend: " + name)
        config = (directory / "plugins" / "aiSECTOR" / "config.yml").read_text(encoding="utf-8")
        if 'database: "aisector_staging"' not in config or 'port: 6380' not in config:
            raise RuntimeError("Unsafe endpoint config: " + name)
        if 'host: "127.0.0.1"' not in config or 'uri: "mongodb://127.0.0.1:27017"' not in config:
            raise RuntimeError("Unexpected staging host: " + name)
        world = directory / "world"
        backup = RUN / "backup" / name / "world"
        if any(path.is_symlink() for path in world.rglob("*")):
            raise RuntimeError("Refusing symlinked world backup")
        shutil.copytree(world, backup)
        source = {str(p.relative_to(world)): digest(p) for p in world.rglob("*") if p.is_file()}
        copied = {str(p.relative_to(backup)): digest(p) for p in backup.rglob("*") if p.is_file()}
        if source != copied or not source:
            raise RuntimeError("Backup verification failed: " + name)
        hashes[name] = source
    (RUN / "backup-manifest.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")
    artifacts = {}
    for name, directory, _ in SERVERS:
        proxy = name == "Velocity"
        source = REPO / ("VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar" if proxy
                         else "aiSector/target/aiSector-2.0.0-SNAPSHOT.jar")
        target = directory / "plugins" / ("aiSector-Velocity.jar" if proxy else "aiSector.jar")
        shutil.copy2(source, RUN / (name + "-new.jar"))
        shutil.copy2(target, RUN / (name + "-before.jar"))
        shutil.copy2(source, target)
        if digest(source) != digest(target): raise RuntimeError("Artifact mismatch")
        artifacts[name] = digest(target)
    (RUN / "artifact-hashes.json").write_text(json.dumps(artifacts, indent=2), encoding="utf-8")
    command([JAVA / "javac.exe", "--add-modules", "jdk.attach", "-d", RUN,
             REPO / "tools/runtime/StagingSmokeAgent.java", REPO / "tools/runtime/AttachSmoke.java"])
    manifest = RUN / "MANIFEST.MF"
    manifest.write_text("Manifest-Version: 1.0\nAgent-Class: StagingSmokeAgent\n\n", encoding="ascii")
    agent = RUN / "smoke-agent.jar"
    command([JAVA / "jar.exe", "cfm", agent, manifest, "-C", RUN, "StagingSmokeAgent.class", "-C", RUN,
             "StagingSmokeAgent$InspectStream.class"])
    processes, logs, clients, fixture_jars = {}, [], [], []
    client_by_name = {}
    started_redis = False
    if player_smoke:
        command([JAVA / "javac.exe", "-cp", prepared_client[1], "-d", RUN,
                 REPO / "tools/runtime/ClientControlAgent.java"])
        client_manifest = RUN / "CLIENT-MANIFEST.MF"
        client_manifest.write_text("Manifest-Version: 1.0\nAgent-Class: ClientControlAgent\n\n", encoding="ascii")
        client_agent = RUN / "client-control.jar"
        command([JAVA / "jar.exe", "cfm", client_agent, client_manifest, "-C", RUN, "ClientControlAgent.class"])
        report = ET.parse(REPO / "aiSector/target/surefire-reports/TEST-ai.aisector.user.HomeSlotRepositoryTest.xml")
        helper_cp = next(prop.attrib["value"] for prop in report.findall("./properties/property")
                         if prop.attrib["name"] == "java.class.path")
        helper_classes = RUN / "player-helper"
        helper_classes.mkdir()
        command([JAVA / "javac.exe", "-cp", helper_cp, "-d", helper_classes,
                 REPO / "tools/runtime/PlayerSmokePlugin.java"])
        (helper_classes / "plugin.yml").write_text("name: RewritePlayerSmoke\nversion: '1'\nmain: PlayerSmokePlugin\n"
                "api-version: '26.1'\ndepend: [aiSECTOR]\ncommands:\n  rwsmoke: {}\n", encoding="utf-8")
        helper_jar = RUN / "player-helper.jar"
        command([JAVA / "jar.exe", "cf", helper_jar, "-C", helper_classes, "."])
        for _, directory, _ in SERVERS[:-1]:
            target = directory / "plugins" / "rewrite-player-smoke.jar"
            if target.exists(): raise RuntimeError("Refusing to overwrite existing player fixture plugin")
            fixture_jars.append(target)
            shutil.copy2(helper_jar, target)

    def probe(server, action, tag):
        output = RUN / (server + "-" + action + "-" + str(time.monotonic_ns()) + ".result")
        command([JAVA / "java.exe", "--add-modules", "jdk.attach", "-cp", RUN, "AttachSmoke",
                 processes[server].pid, agent, action + "|" + tag + "|" + str(output)])
        deadline = time.monotonic() + 30
        while not output.exists() and time.monotonic() < deadline: time.sleep(.1)
        value = output.read_text() if output.exists() else "TIMEOUT"
        print(server, action, value, flush=True)
        return value

    def player_request(server, action, name, *args):
        request = uuid.uuid4().hex
        port = next(port for n, _, port in SERVERS if n == server)
        path = RUN / (str(port) + "-" + request + ".json")
        processes[server].stdin.write("rwsmoke " + " ".join([request, action, name, *map(str, args)]) + "\n")
        processes[server].stdin.flush()
        deadline = time.monotonic() + 12
        while not path.exists() and time.monotonic() < deadline: time.sleep(.1)
        if not path.exists(): raise RuntimeError("Fixture reply timeout: " + server + " " + action)
        value = json.loads(path.read_text(encoding="utf-8"))
        if "error" in value: raise RuntimeError(value["error"])
        return value

    def locate(name, expected=None, timeout=45):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for server, _, _ in SERVERS[:-1]:
                value = player_request(server, "state", name)
                if value.get("online") and value.get("ready") and not value.get("dead") and (expected is None or expected == server):
                    # A stationary fixture avoids testing gravity instead of warmup movement cancellation.
                    value = player_request(server, "steady", name)
                    return server, value
            time.sleep(.5)
        raise RuntimeError("Player did not become ready: " + name + " " + str(expected))

    def check_inventory(before, after):
        for field in ["inventoryHash", "held", "level", "totalExperience", "gameMode"]:
            if before[field] != after[field]:
                raise AssertionError("Transfer changed " + field + ": " + str(before) + " -> " + str(after))
        if abs(before["exp"] - after["exp"]) > .0001: raise AssertionError("Transfer changed XP progress")

    def client_control(name, action, value=""):
        output = RUN / (uuid.uuid4().hex + "-client.result")
        payload = action + "|" + base64.b64encode(value.encode()).decode() + "|" + str(output)
        command([JAVA / "java.exe", "--add-modules", "jdk.attach", "-cp", RUN, "AttachSmoke",
                 client_by_name[name].pid, client_agent, payload])
        deadline = time.monotonic() + 15
        while not output.exists() and time.monotonic() < deadline: time.sleep(.1)
        result = output.read_text() if output.exists() else "TIMEOUT"
        if result != "OK": raise RuntimeError("Client control " + action + ": " + result)

    def wait_gui(server, name, prefix, timeout=10):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            gui = player_request(server, "gui", name)
            if gui["title"].startswith(prefix):
                time.sleep(.6)  # Allow the actual client's container state packet to arrive.
                return gui
            time.sleep(.2)
        raise RuntimeError("GUI did not open: " + prefix)

    def restart_backend(server):
        process = processes[server]
        process.stdin.write("stop\n")
        process.stdin.flush()
        process.wait(timeout=65)
        directory = next(directory for name, directory, _ in SERVERS if name == server)
        log_path = RUN / (server + "-restart.log")
        log = log_path.open("w", encoding="utf-8")
        logs.append(log)
        properties = ["-Daisector.smoke.output=" + str(RUN), "-Daisector.smoke.players=" + ",".join(names)]
        processes[server] = subprocess.Popen([str(JAVA / "java.exe"), "-Xms256M", "-Xmx768M", *properties,
                "-jar", "paper.jar", "--nogui"], cwd=directory, env=env,
                stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if processes[server].poll() is not None: raise RuntimeError("Restart failed")
            if "Done (" in log_path.read_text(encoding="utf-8", errors="replace"): return
            time.sleep(.5)
        raise RuntimeError("Restart timeout")

    try:
        command(["wsl", "-d", "Ubuntu", "-u", "root", "--", "redis-server", "--port", "6380",
                 "--bind", "127.0.0.1", "--protected-mode", "yes", "--save", "", "--appendonly", "no",
                 "--daemonize", "yes", "--pidfile", "/var/run/aisector-staging-6380.pid"])
        started_redis = True
        if redis("PING") != "PONG": raise RuntimeError("Staging Redis did not start")
        report = ET.parse(REPO / "aiSector/target/surefire-reports/TEST-ai.aisector.user.HomeSlotRepositoryTest.xml")
        classpath = next(prop.attrib["value"] for prop in report.findall("./properties/property")
                         if prop.attrib["name"] == "java.class.path")
        classpath += os.pathsep + str(REPO / "VelocityPlugin/target/classes")
        for smoke in ["HomeSlotMongoSmoke.java", "UserIdentityMongoSmoke.java", "MembershipFenceMongoSmoke.java", "RedisLifecycleSmoke.java"]:
            result = command([JAVA / "java.exe", "-cp", classpath, REPO / "tools/runtime" / smoke])
            (RUN / (smoke + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
            print(result.stdout.strip(), flush=True)
        env = dict(os.environ, AISECTOR_REDIS_HOST="127.0.0.1", AISECTOR_REDIS_PORT="6380",
                   AISECTOR_MONGODB_URI="mongodb://127.0.0.1:27017", AISECTOR_MONGODB_DATABASE="aisector_staging")
        for name, directory, _ in SERVERS:
            log = (RUN / (name + ".log")).open("w", encoding="utf-8")
            logs.append(log)
            properties = ["-Daisector.smoke.output=" + str(RUN), "-Daisector.smoke.players=" + ",".join(names)] if player_smoke else []
            processes[name] = subprocess.Popen([str(JAVA / "java.exe"), "-Xms256M", "-Xmx768M", *properties, "-jar",
                    "velocity.jar" if name == "Velocity" else "paper.jar", *([] if name == "Velocity" else ["--nogui"])],
                    cwd=directory, env=env, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            if any(p.poll() is not None for p in processes.values()): raise RuntimeError("Server exited during startup")
            ready = all(("Done (" in (RUN / (name + ".log")).read_text(encoding="utf-8", errors="replace"))
                        for name, _, _ in SERVERS)
            if ready: break
            time.sleep(1)
        else: raise RuntimeError("Startup timeout")
        if redis("PUBSUB", "NUMSUB", "sector-transfer").splitlines()[-1] != "1":
            raise RuntimeError("Proxy transfer subscriber missing")
        tag = "RS" + os.urandom(3).hex().upper()
        if not probe("Sector1", "create", tag).startswith("OK:Guild"): raise RuntimeError("Guild create failed")
        if not probe("Sector2", "load", tag).startswith("OK:Guild"): raise RuntimeError("Guild load failed")
        if probe("Sector2", "remember", tag) != "OK:REMEMBERED": raise RuntimeError("Could not remember generation")
        if not probe("Sector1", "save", tag).startswith("OK:"): raise RuntimeError("Guild save failed")
        if not probe("Sector2", "save", tag).startswith("ERROR:"): raise RuntimeError("Stale save accepted")
        if probe("Sector1", "delete", tag) != "OK:true": raise RuntimeError("Guild delete failed")
        time.sleep(2)
        if probe("Sector2", "probe", tag) != "OK:MISSING": raise RuntimeError("Guild invalidation failed")
        if not probe("Sector1", "create", tag).startswith("OK:Guild"): raise RuntimeError("Guild recreate failed")
        if not probe("Sector2", "load", tag).startswith("OK:Guild"): raise RuntimeError("Recreated guild load failed")
        if not probe("Sector2", "saveRemembered", tag).startswith("ERROR:"):
            raise RuntimeError("Old generation snapshot overwrote recreated guild")
        if probe("Sector1", "delete", tag) != "OK:true": raise RuntimeError("Recreated guild cleanup failed")
        if probe("Sector1", "inventory", tag) != "OK:INVENTORY_ROUNDTRIP_OK": raise RuntimeError("Item roundtrip failed")
        if probe("Sector1", "profile", tag) != "OK:PROFILE_CACHE_VALIDATION_OK": raise RuntimeError("Profile cache validation failed")
        if player_smoke:
            for name in names:
                log = (RUN / (name + "-client.log")).open("w", encoding="utf-8")
                logs.append(log)
                clients.append(subprocess.Popen(official_client.arguments(prepared_client, RUN / name, name),
                        stdout=log, stderr=subprocess.STDOUT, cwd=RUN))
                client_by_name[name] = clients[-1]
                server, value = locate(name, timeout=100)
                print("PLAYER_LOGIN_READY", name, server, value["uuid"], flush=True)
                time.sleep(4)  # Respect Velocity's existing connection-rate limit, do not change its policy.
            metadata_corpus = player_request("Sector1", "legacy-corpus", names[0])
            if metadata_corpus["metadataCases"] != 6: raise AssertionError("Legacy metadata corpus incomplete")
            time.sleep(3)
            server, _ = locate(names[0])
            baseline = player_request(server, "seed", names[0])
            rejected_profile = player_request(server, "profile-transfer-reject", names[0])
            if not rejected_profile["rejected"] or rejected_profile["frozen"]:
                raise AssertionError("A rejected unpublished profile write did not release the source guard")
            (RUN / "player-baseline.json").write_text(json.dumps(baseline, indent=2), encoding="utf-8")
            for target in ["Sector2", "Sector3", "Sector1"]:
                flags = target != "Sector3"
                player_request(server, "profile-flags", names[0], str(flags).lower(), str(flags).lower())
                published = player_request(server, "transfer", names[0], target, "delayed-profile")
                if published["elapsedMillis"] < 1500:
                    raise AssertionError("Transfer published before the delayed profile queue drained")
                if not published["mutationsBlocked"]:
                    raise AssertionError("Source actions were not frozen during delayed profile persistence")
                server, after = locate(names[0], target)
                time.sleep(2)
                after = player_request(server, "state", names[0])
                check_inventory(baseline, after)
                if after["profileFlying"] != flags or after["profileGodMode"] != flags:
                    raise AssertionError("Destination loaded stale fly/god profile flags")
                print("PLAYER_TRANSFER_INVENTORY_OK", server, after["inventoryHash"], flush=True)
            a, b = names
            player_request(server, "steady", a)
            b_server, _ = locate(b)
            player_request(b_server, "steady", b)
            time.sleep(1)
            results = {"names": names, "transferInventory": True, "profileFlushBeforeTransfer": True,
                       "sourceTransferMutationGuard": True}
            player_request(server, "profile-flags", names[0], "false", "false")
            client_control(a, "command", "sethome")
            wait_gui(server, a, "§8Ustaw swój dom")
            client_control(a, "screenshot")
            client_control(a, "click", "2:1")
            time.sleep(1)
            homes = player_request(server, "homes", a)["homes"]
            if len(homes) != 1: raise AssertionError("Home was not persisted")
            player_request(server, "transfer", a, "Sector2")
            server, _ = locate(a, "Sector2")
            player_request(server, "steady", a)
            time.sleep(1)
            client_control(a, "command", "home")
            wait_gui(server, a, "§8Wybierz dom do teleportacji")
            client_control(a, "click", "2:0")
            server, after = locate(a, "Sector1", timeout=25)
            check_inventory(baseline, after)
            results["homeGuiCrossBackend"] = True
            print("PLAYER_HOME_GUI_CROSS_BACKEND_OK", flush=True)
            client_control(a, "command", "sethome")
            wait_gui(server, a, "§8Ustaw swój dom")
            player_request(server, "home-recreate", a)
            latest_home = player_request(server, "homes", a)["homes"][0]
            client_control(a, "click", "2:1")
            time.sleep(1)
            if player_request(server, "homes", a)["homes"][0]["_revision"] != latest_home["_revision"]:
                raise AssertionError("Stale menu deleted/replaced the recreated home")
            client_control(a, "close")
            client_control(a, "command", "sethome")
            wait_gui(server, a, "§8Ustaw swój dom")
            client_control(a, "click", "2:1")
            time.sleep(1)
            if player_request(server, "homes", a)["homes"]: raise AssertionError("Current menu did not delete its observed home")
            results["staleHomeMenuFence"] = True
            time.sleep(4)
            target_server, target_state = locate(b)
            client_control(a, "command", "tpa " + b)
            time.sleep(1)
            client_control(b, "command", "tpaccept")
            time.sleep(7)
            server, after = locate(a, target_server)
            if abs(after["x"] - target_state["x"]) > 2 or abs(after["z"] - target_state["z"]) > 2:
                raise AssertionError("Local TPA target mismatch")
            check_inventory(baseline, after)
            results["localTpa"] = True
            player_request(target_server, "transfer", b, "Sector3")
            target_server, target_state = locate(b, "Sector3")
            player_request(target_server, "steady", b)
            player_request(server, "steady", a)
            time.sleep(4)
            client_control(a, "command", "tpa " + b)
            time.sleep(1)
            client_control(b, "command", "tpaccept")
            server, after = locate(a, "Sector3", timeout=25)
            check_inventory(baseline, after)
            results["crossBackendTpa"] = True
            print("PLAYER_TPA_LOCAL_AND_CROSS_BACKEND_OK", flush=True)
            time.sleep(4)
            client_control(b, "command", "setspawnsector")
            time.sleep(2)
            player_request(server, "transfer", a, "Sector1")
            server, _ = locate(a, "Sector1")
            public_spawn_player = player_request(server, "deny-admin-spawn-permission", a)
            if public_spawn_player["op"] or public_spawn_player["spawnAdminPermission"]:
                raise AssertionError("Public spawn test is not an unprivileged fixture")
            client_control(a, "command", "spawn")
            server, after = locate(a, "Sector3")
            check_inventory(baseline, after)
            results["spawn"] = True
            results["publicSpawnWithoutAdminPermission"] = True
            baseline = player_request(server, "seed", a, 19)
            after = baseline  # Make the reconnect detect stale inventory on the temporary initial backend.
            client_control(a, "reconnect")
            previous_session = after["session"]
            time.sleep(3)
            server, after = locate(a, "Sector3", timeout=35)
            if after["session"] == previous_session: raise AssertionError("Reconnect did not replace the Player session")
            check_inventory(baseline, after)
            results["reconnect"] = True
            print("PLAYER_SPAWN_AND_RECONNECT_OK", flush=True)
            time.sleep(6)
            client_control(a, "command", "v")
            time.sleep(1)
            if player_request(server, "state", b)["visibleOther"]: raise AssertionError("Vanish did not hide player")
            player_request(server, "permission-see", b, "true")
            time.sleep(2)
            if not player_request(server, "state", b)["visibleOther"]: raise AssertionError("Permission grant did not reveal vanish")
            player_request(server, "permission-see", b, "false")
            time.sleep(2)
            if player_request(server, "state", b)["visibleOther"]: raise AssertionError("Permission revoke did not hide vanish")
            if player_request(server, "state", b)["vanishTags"]: raise AssertionError("Revoked viewer retained staff tags")
            player_request("Sector1", "generator-register", a)
            time.sleep(2)
            restart_backend("Sector1")
            player_request(server, "transfer", b, "Sector1")
            b_server, _ = locate(b, "Sector1")
            generator = player_request(b_server, "generator-state", b)
            if not generator["registered"] or generator["block"] != "STONE": raise AssertionError("Generator did not survive restart")
            rejected = player_request(b_server, "generator-reject", b)
            if not rejected["rejected"] or not rejected["registered"] or rejected["block"] != "STONE" or rejected["rewards"] != 0:
                raise AssertionError("Rejected generator delete mutated world/cache/reward")
            cancelled = player_request(b_server, "generator-cancelled-break", b)
            if not cancelled["registered"] or cancelled["block"] != "STONE" or cancelled["rewards"] != 0:
                raise AssertionError("Cancelled protection event mutated generator")
            player_request(b_server, "generator-break", b, "diamond")
            time.sleep(3)
            if player_request(b_server, "generator-state", b)["block"] != "STONE": raise AssertionError("Generator did not regenerate")
            player_request(b_server, "generator-break", b, "gold")
            time.sleep(2)
            dismantled = player_request(b_server, "generator-state", b)
            if dismantled["registered"] or dismantled["block"] != "AIR" or dismantled["rewards"] != 1:
                raise AssertionError("Generator dismantle/reward mismatch: " + str(dismantled))
            player_request(b_server, "permission-see", b, "true")
            time.sleep(6)
            if a not in player_request(b_server, "state", b)["vanishTags"]: raise AssertionError("Cold backend missed vanish snapshot")
            results["generatorRestartRejectionRegenDismantle"] = True
            results["vanishPermissionAndColdBackend"] = True
            print("PLAYER_GENERATOR_LIFECYCLE_AND_COLD_VANISH_OK", flush=True)
            player_request(b_server, "transfer", b, "Sector3")
            locate(b, "Sector3")
            client_control(a, "reconnect")
            time.sleep(3)
            server, _ = locate(a, "Sector3", timeout=35)
            time.sleep(1)
            if player_request(server, "state", b)["visibleOther"]: raise AssertionError("Reconnect lost vanish")
            client_control(a, "command", "v")
            time.sleep(1)
            if not player_request(server, "state", b)["visibleOther"]: raise AssertionError("Unvanish did not reveal player")
            results["vanishReconnect"] = True
            print("PLAYER_VANISH_RECONNECT_OK", flush=True)
            time.sleep(6)
            normal_pvp = player_request(server, "pvp", a, b)
            blocked_pvp = player_request(server, "pvp", a, b, "missing-guild")
            if normal_pvp["healthAfter"] >= 20 or blocked_pvp["healthAfter"] != 20:
                raise AssertionError("PvP cache policy mismatch: " + str(normal_pvp) + " " + str(blocked_pvp))
            arrow_allowed = player_request(server, "projectile-pvp", a, b)
            arrow_blocked = player_request(server, "projectile-pvp", a, b, "missing-guild")
            if arrow_allowed["cancelled"] or not arrow_blocked["cancelled"]: raise AssertionError("Projectile PvP policy mismatch")
            results["pvpDirectAndMissingGuild"] = True
            results["pvpProjectile"] = True
            protected_role = player_request(server, "stale-owner", a)
            if not protected_role["protected"] or not protected_role["cleaned"]:
                raise AssertionError("A stale profile role authorized deletion of another generation's guild")
            results["staleOwnerRoleBlocked"] = True
            baseline = player_request(server, "seed", a, 19)
            client_control(b, "command", "invsee " + a)
            wait_gui(server, b, "§8Ekwipunek gracza:")
            client_control(b, "screenshot")
            client_control(b, "click", "27:0")
            time.sleep(.5)
            client_control(b, "close")
            time.sleep(.5)
            if any(item.startswith("0:DIAMOND_SWORD") for item in player_request(server, "state", a)["items"]):
                raise AssertionError("Existing editable invsee did not persist its edit on close")
            results["invseeEditableBaseline"] = True
            baseline = player_request(server, "seed", a, 19)
            client_control(a, "chat", "Rewrite smoke | unicode ąę test")
            time.sleep(2)
            client_control(b, "screenshot")
            if "unicode ąę test" not in (RUN / (b + "-client.log")).read_text(encoding="utf-8", errors="replace"):
                raise AssertionError("Global chat did not reach the other actual client")
            results["unicodeGlobalChat"] = True
            # Death/respawn and restore are exercised with real clients and a canonical backup GUI click.
            player_request(server, "transfer", a, "Sector1")
            server, _ = locate(a, "Sector1")
            player_request(server, "die", a)
            time.sleep(2)
            client_control(a, "respawn")
            time.sleep(2)
            server, _ = locate(a, "Sector3")
            client_control(b, "command", "backup " + a)
            wait_gui(server, b, "§8Backupy gracza:")
            client_control(b, "screenshot")
            client_control(b, "click", "0:0")
            time.sleep(2)
            after = player_request(server, "state", a)
            if after["inventoryHash"] != baseline["inventoryHash"]: raise AssertionError("Death backup restore changed inventory")
            results["deathRespawnOnlineBackup"] = True
            print("PLAYER_DEATH_RESPAWN_BACKUP_OK", flush=True)
            player_request(server, "die", a)
            time.sleep(1)
            client_control(a, "respawn")
            time.sleep(2)
            server, _ = locate(a, "Sector3")
            client_control(a, "disconnect")
            time.sleep(2)
            client_control(b, "command", "backup " + a)
            wait_gui(server, b, "§8Backupy gracza:")
            client_control(b, "click", "0:0")
            time.sleep(2)
            claim = player_request(server, "backup-status", b, a)["records"][0]
            if claim.get("restored_by") != b or not claim.get("restore_claim"):
                raise AssertionError("Offline backup was not claimed")
            client_control(a, "reconnect")
            time.sleep(3)
            server, after = locate(a, "Sector3", timeout=35)
            if after["inventoryHash"] != baseline["inventoryHash"]: raise AssertionError("Pending offline backup was not restored")
            results["offlinePendingBackup"] = True
            print("PLAYER_OFFLINE_PENDING_BACKUP_OK", flush=True)
            player_request(server, "die", a)
            time.sleep(1)
            client_control(a, "respawn")
            time.sleep(2)
            server, _ = locate(a, "Sector3")
            client_control(a, "disconnect")
            time.sleep(2)
            if player_request(server, "pending-placeholder", b, a)["status"] != "OK": raise AssertionError("Cannot set isolated pending conflict fixture")
            client_control(b, "command", "backup " + a)
            wait_gui(server, b, "§8Backupy gracza:")
            client_control(b, "click", "0:0")
            time.sleep(2)
            rolled_back = player_request(server, "backup-status", b, a)["records"][0]
            if rolled_back.get("restored_by") != "Nikt" or rolled_back.get("restore_claim") is not None:
                raise AssertionError("Definite pending conflict did not roll back its own claim")
            if player_request(server, "pending-clear-placeholder", b, a)["removed"] != 1:
                raise AssertionError("Existing pending payload was overwritten")
            client_control(b, "close")
            client_control(b, "command", "backup " + a)
            wait_gui(server, b, "§8Backupy gracza:")
            client_control(b, "click", "0:0")
            time.sleep(2)
            client_control(a, "reconnect")
            time.sleep(3)
            server, after = locate(a, "Sector3", timeout=35)
            if after["inventoryHash"] != baseline["inventoryHash"]: raise AssertionError("Restore after definite conflict failed")
            results["pendingCollisionClaimRollback"] = True
            print("PLAYER_PENDING_CONFLICT_ROLLBACK_OK", flush=True)
            (RUN / "player-results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
        print("STAGING_SMOKE_OK", RUN, flush=True)
    finally:
        # These are our own isolated multiplayer clients, never the user's existing launcher/game process.
        for name, client in client_by_name.items():
            if client.poll() is None:
                try:
                    client_control(name, "exit")
                    client.wait(timeout=15)
                except Exception:
                    if client.poll() is None:
                        client.terminate()
                        client.wait(timeout=15)
        # Console input belongs to these processes; never kill unrelated servers.
        for name in ["Velocity", "Sector1", "Sector2", "Sector3"]:
            process = processes.get(name)
            if process is not None and process.poll() is None:
                process.stdin.write("shutdown\n" if name == "Velocity" else "stop\n")
                process.stdin.flush()
                try: process.wait(timeout=65)
                except subprocess.TimeoutExpired:
                    print("SHUTDOWN_TIMEOUT", name, process.pid, flush=True)
        for log in logs: log.close()
        if all(p.poll() is not None for p in processes.values()) and started_redis:
            redis("SHUTDOWN", "NOSAVE")
        if all(p.poll() is not None for p in processes.values()):
            for jar in fixture_jars:
                jar.unlink(missing_ok=True)
        print("RUN_EVIDENCE", RUN, flush=True)


if __name__ == "__main__":
    main()
