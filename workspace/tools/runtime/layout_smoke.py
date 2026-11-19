"""Isolated sector layout smoke. Uses pre-verified Paper/Velocity JARs, loopback and staging only."""
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET

import official_client
from staging_smoke import JAVA, REPO, TEMP, command, listening, redis

INPUT = TEMP / "aisector-layout-20261119"
SERVER_HASHES = {
    "paper": "c86baf83b1fb0eb0d572f39484c242210896c6adf9cc9342d5c16c4de70c9bdc",
    "velocity": "0844bcabdcdb4f5b875906fd4c3365bc1bb76918e42bc595ed88ad195258dd75",
}


def digest_tree(path):
    results = {}
    for file in path.rglob("*"):
        if file.is_file() and file.name != "session.lock":
            with file.open("rb") as stream:
                results[file.relative_to(path).as_posix()] = hashlib.file_digest(stream, "sha256").hexdigest()
    return results


def world_files(path, port, seed, extra=""):
    path.mkdir()
    (path / "eula.txt").write_text("eula=true\n")
    (path / "server.properties").write_text(f"""server-ip=127.0.0.1
server-port={port}
online-mode=false
enforce-secure-profile=false
level-name=world
level-seed={seed}
view-distance=3
simulation-distance=3
spawn-protection=0
white-list=true
network-compression-threshold=256
{extra}""", encoding="ascii")


def main():
    if any(listening(p) for p in (25565, 25566, 25567, 25568, 6380)) or not listening(27017):
        raise RuntimeError("Requires free loopback test ports and available staging Mongo")
    for name, expected in SERVER_HASHES.items():
        with (INPUT / f"{name}.verified.jar").open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise RuntimeError(f"Unverified {name} jar")
    run = Path(tempfile.mkdtemp(prefix="aisector-layout-smoke-", dir=TEMP))
    print("LAYOUT_EVIDENCE", run, flush=True)
    logs, children = [], {}
    started_redis = False
    paper = INPUT / "paper.verified.jar"
    velocity = INPUT / "velocity.verified.jar"
    player = "RwSL" + uuid.uuid4().hex[:6]
    fixture = REPO / "tools/runtime"
    env = dict(os.environ, AISECTOR_REDIS_HOST="127.0.0.1", AISECTOR_REDIS_PORT="6380",
               AISECTOR_MONGODB_URI="mongodb://127.0.0.1:27017", AISECTOR_MONGODB_DATABASE="aisector_staging",
               AISECTOR_LOCKED_SPAWN="Spawn")

    def start(name, directory, jar, properties=()):
        log = (run / (name + ".log")).open("w", encoding="utf-8")
        logs.append(log)
        children[name] = subprocess.Popen([str(JAVA / "java.exe"), "-Xms256M", "-Xmx1024M",
                "-XX:ActiveProcessorCount=4", *properties, "-jar", str(jar),
                *([] if name == "Velocity" else ["--nogui"])], cwd=directory, env=env,
                stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)

    def wait(test, label, timeout=100):
        for _ in range(timeout * 4):
            result = test()
            if result: return result
            if any(p.poll() is not None for n, p in children.items() if n != "client"):
                raise RuntimeError("A server exited: " + label)
            time.sleep(.25)
        raise RuntimeError("Timeout: " + label)

    def ready(name):
        return "Done (" in (run / (name + ".log")).read_text(encoding="utf-8", errors="replace")

    def send(name, text):
        children[name].stdin.write(text + "\n")
        children[name].stdin.flush()

    def stop(name):
        process = children[name]
        if process.poll() is None:
            if name != "Velocity":
                # Console /stop is not valid before the overworld is initialized.
                for _ in range(240):
                    if process.poll() is not None or ready(name): break
                    time.sleep(.25)
            if process.poll() is not None:
                children.pop(name)
                return
            send(name, "shutdown" if name == "Velocity" else "stop")
            process.wait(timeout=70)
        children.pop(name)

    def request(name, action, *args):
        token = uuid.uuid4().hex
        send(name, "layoutprobe " + " ".join([token, player, action, *map(str, args)]))
        output = run / (token + ".json")
        def result():
            try:
                return json.loads(output.read_text(encoding="utf-8"))
            except (FileNotFoundError, json.JSONDecodeError):
                return None
        data = wait(result, action, 20)
        if data.get("error"): raise RuntimeError(data["error"])
        return data

    def client(action, value=""):
        reply = run / (uuid.uuid4().hex + ".client")
        payload = action + "|" + base64.b64encode(value.encode()).decode() + "|" + str(reply)
        command([JAVA / "java.exe", "--add-modules", "jdk.attach", "-cp", run, "AttachSmoke",
                 children["client"].pid, run / "client.jar", payload])
        wait(reply.exists, "client " + action, 15)
        response = reply.read_text()
        if response != "OK": raise RuntimeError(response)

    try:
        prepared = official_client.prepare(TEMP / "aisector-official-client-26.3")
        command([JAVA / "javac.exe", "--add-modules", "jdk.attach", "-cp", prepared[1], "-d", run,
                 fixture / "AttachSmoke.java", fixture / "ClientControlAgent.java"])
        manifest = run / "client.mf"
        manifest.write_text("Manifest-Version: 1.0\nAgent-Class: ClientControlAgent\n\n")
        command([JAVA / "jar.exe", "cfm", run / "client.jar", manifest, "-C", run, "ClientControlAgent.class"])
        report = ET.parse(REPO / "aiSector/target/surefire-reports/TEST-ai.aisector.user.HomeSlotRepositoryTest.xml")
        cp = next(x.attrib["value"] for x in report.findall("./properties/property") if x.attrib["name"] == "java.class.path")
        helper = run / "fixture"
        helper.mkdir()
        command([JAVA / "javac.exe", "-cp", cp, "-d", helper, fixture / "LayoutSmokePlugin.java"])
        (helper / "plugin.yml").write_text("name: LayoutSmoke\nversion: '1'\nmain: LayoutSmokePlugin\n"
                "api-version: '26.1'\ndepend: [aiSECTOR]\ncommands:\n  layoutprobe: {}\n")
        command([JAVA / "jar.exe", "cf", run / "fixture.jar", "-C", helper, "."])

        template = run / "game-template"
        world_files(template, 25566, 2718281828)
        start("Template", template, paper)
        wait(lambda: ready("Template"), "template readiness", 180)
        stop("Template")
        source = template / "world"
        backup = run / "backup" / "game"
        shutil.copytree(source, backup, ignore=shutil.ignore_patterns("session.lock"))
        original_hashes = digest_tree(source)
        assert original_hashes and digest_tree(backup) == original_hashes

        spawn_template = run / "spawn-template"
        world_files(spawn_template, 25566, 31415926)
        start("SpawnTemplate", spawn_template, paper)
        wait(lambda: ready("SpawnTemplate"), "spawn template readiness", 150)
        stop("SpawnTemplate")
        spawn_source = spawn_template / "world"
        spawn_backup = run / "backup" / "spawn"
        shutil.copytree(spawn_source, spawn_backup, ignore=shutil.ignore_patterns("session.lock"))
        spawn_hashes = digest_tree(spawn_source)
        assert spawn_hashes and digest_tree(spawn_backup) == spawn_hashes
        (run / "backup-manifest.json").write_text(json.dumps({"game": original_hashes, "spawn": spawn_hashes}, indent=2))

        command(["wsl", "-d", "Ubuntu", "-u", "root", "--", "redis-server", "--port", "6380", "--bind",
                 "127.0.0.1", "--protected-mode", "yes", "--save", "", "--appendonly", "no", "--daemonize",
                 "yes", "--pidfile", "/var/run/aisector-layout-6380.pid"])
        started_redis = True
        assert redis("PING") == "PONG"
        for name, port in (("Spawn", 25566), ("GameWest", 25567), ("GameEast", 25568)):
            directory = run / name
            spawn = name == "Spawn"
            world_files(directory, port, 31415926 if spawn else 2718281828)
            shutil.copytree(spawn_backup if spawn else backup, directory / "world")
            assert digest_tree(directory / "world") == (spawn_hashes if spawn else original_hashes)
            plugins = directory / "plugins"
            config = plugins / "aiSECTOR"
            config.mkdir(parents=True)
            fixture_uuid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + player).encode()).digest(), version=3)
            (directory / "whitelist.json").write_text(json.dumps([{"uuid": str(fixture_uuid), "name": player}]))
            (config / "config.yml").write_text(f'this-sector-name: "{name}"\nredis:\n  host: "127.0.0.1"\n'
                    '  port: 6380\nmongodb:\n  uri: "mongodb://127.0.0.1:27017"\n  database: "aisector_staging"\n')
            shutil.copy2(REPO / "aiSector/src/main/resources/sectors-locked-spawn.yml", config / "sectors.yml")
            shutil.copy2(REPO / "aiSector/target/aiSector-2.0.0-SNAPSHOT.jar", plugins / "aiSector.jar")
            shutil.copy2(run / "fixture.jar", plugins / "fixture.jar")
            start(name, directory, paper, ["-Dlayout.smoke.output=" + str(run), "-Dlayout.smoke.player=" + player])

        proxy = run / "proxy"
        (proxy / "plugins").mkdir(parents=True)
        shutil.copy2(REPO / "VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar", proxy / "plugins/VelocityPlugin.jar")
        (proxy / "velocity.toml").write_text('config-version = "2.9"\nbind = "127.0.0.1:25565"\n'
                'online-mode = false\nforce-key-authentication = false\nplayer-info-forwarding-mode = "none"\n'
                '[servers]\nSpawn = "127.0.0.1:25566"\nGameWest = "127.0.0.1:25567"\n'
                'GameEast = "127.0.0.1:25568"\ntry = ["Spawn"]\n'
                '[forced-hosts]\n"127.0.0.1" = ["Spawn"]\n')
        start("Velocity", proxy, velocity)
        wait(lambda: all(ready(name) for name in ("Spawn", "GameWest", "GameEast", "Velocity")), "layout startup", 180)
        request("Spawn", "prepare")
        log = (run / "client.log").open("w", encoding="utf-8")
        logs.append(log)
        children["client"] = subprocess.Popen(official_client.arguments(prepared, run / player, player),
                                               stdout=log, stderr=subprocess.STDOUT, cwd=run)
        wait(lambda: request("Spawn", "state").get("ready"), "new player joins Spawn")
        assert request("Spawn", "escape")["blocked"]
        client("command", "server GameWest")
        time.sleep(2)
        assert request("Spawn", "state")["ready"]
        request("Spawn", "position", 2050.5, 2050.5)
        fixture_state = request("Spawn", "seed")
        client("block", fixture_state["ordinary"])
        time.sleep(1)
        assert request("Spawn", "state")["ready"]
        start_time = time.monotonic()
        client("block", fixture_state["button"])

        def arrival():
            for sector in ("GameWest", "GameEast"):
                state = request(sector, "state")
                if state.get("ready") and -500 <= state["z"] < 500 and (
                        -500 <= state["x"] < 0 if sector == "GameWest" else 0 <= state["x"] < 500):
                    return sector, state
            return None

        sector, state = wait(arrival, "safe button arrival")
        assert state["floorSafe"] and state["inventory"] == fixture_state["inventory"] and state["level"] == fixture_state["level"]
        assert not request("Spawn", "state")["ready"]
        print("SPONGE_EXIT_OK", sector, state["x"], state["y"], state["z"],
              f"{time.monotonic() - start_time:.2f}s including agent startup/polling", flush=True)
        crossing = request(sector, "crossing-setup")
        assert crossing["border"] and crossing["sameBorder"]
        client("move-to", crossing["clientTarget"])
        other = "GameEast" if sector == "GameWest" else "GameWest"
        def arrived_other():
            value = request(other, "state")
            return value if value.get("ready") and (value["x"] >= 0 if other == "GameEast" else value["x"] < 0) else None
        after = wait(arrived_other, "physical game sector seam")
        assert after["inventory"] == fixture_state["inventory"] and after["level"] == fixture_state["level"]
        (run / "results.json").write_text(json.dumps({"spawnEscapeBlocked": True, "ordinaryButtonDidNotTransfer": True,
            "spawnButtonArrived": state, "crossingArrived": after, "seamBorder": crossing}, indent=2))
        print("LAYOUT_SMOKE_OK", run, flush=True)
    finally:
        if "client" in children:
            try:
                if children["client"].poll() is None: client("exit")
                children["client"].wait(timeout=10)
            except Exception:
                if children["client"].poll() is None: children["client"].terminate()
                children["client"].wait(timeout=10)
            children.pop("client")
        for name in list(reversed(children)):
            try: stop(name)
            except Exception as error: print("Shutdown failed", name, error, flush=True)
        if started_redis and all(p.poll() is not None for p in children.values()): redis("SHUTDOWN", "NOSAVE")
        for log in logs: log.close()


if __name__ == "__main__":
    main()
