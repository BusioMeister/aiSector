"""Run the prepared layout locally until a STOP file appears in its run directory."""
import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import zipfile

from layout_smoke import INPUT, SERVER_HASHES, digest_tree, world_files
from staging_smoke import JAVA, REPO, TEMP, listening


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("sha256")
    parser.add_argument("--resume", type=Path, help="Restart an existing stopped local layout without replacing worlds or ops")
    parser.add_argument("--keep-plugin-jars", action="store_true",
                        help="Resume with the already deployed aiSector and VelocityPlugin jars")
    parser.add_argument("--headless", action="store_true", help="Start child processes without Windows console windows")
    parser.add_argument("--instant", action="store_true", help="Opt in to the local 26.3 InstantTransfer prototype")
    parser.add_argument("--update-instant", action="store_true", help="Back up and replace changed local InstantTransfer plugins")
    parser.add_argument("--public-bind", type=ipaddress.IPv4Address,
                        help="Use the LAN address of an already configured online-mode layout")
    args = parser.parse_args()
    creation_flags = subprocess.CREATE_NO_WINDOW if args.headless and os.name == "nt" else 0

    def command(argv):
        return subprocess.run(argv, check=True, capture_output=True, text=True, creationflags=creation_flags)

    def redis(*argv):
        return command(["wsl", "-d", "Ubuntu", "-u", "root", "--", "redis-cli", "-p", "6380", *argv]).stdout.strip()
    if args.keep_plugin_jars and not args.resume:
        parser.error("--keep-plugin-jars requires --resume")
    if args.update_instant and not (args.resume and args.instant):
        parser.error("--update-instant requires --resume and --instant")
    if args.public_bind and (not args.resume or not args.public_bind.is_private or args.public_bind.is_loopback
                             or args.public_bind.is_unspecified or args.public_bind.is_link_local):
        parser.error("--public-bind requires a stopped authenticated layout and a private LAN IPv4")
    join_host = str(args.public_bind or "127.0.0.1")
    with args.archive.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != args.sha256:
            raise RuntimeError("World backup checksum mismatch")
    ports = {"Spawn": 25566, "GameWest": 25567, "GameEast": 25568, "Velocity": 25565}
    def port_ready(role):
        if role != "Velocity" or not args.public_bind:
            return listening(ports[role])
        import socket
        try:
            with socket.create_connection((join_host, ports[role]), timeout=1):
                return True
        except OSError:
            return False

    if any(port_ready(role) for role in ports) or listening(6380) or not listening(27017):
        raise RuntimeError("Expected free local service ports and running staging Mongo")
    for name, expected in SERVER_HASHES.items():
        with (INPUT / f"{name}.verified.jar").open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise RuntimeError(f"Unverified {name} jar")
    run = args.resume.resolve() if args.resume else Path(tempfile.mkdtemp(prefix="aisector-layout-live-", dir=TEMP))
    if args.resume:
        if run.parent != TEMP or not run.name.startswith("aisector-layout-live-") or not (run / "STOP").is_file():
            raise RuntimeError("Resume requires a stopped local layout")
        for name in ("STOP", "READY.json", "Spawn.log", "GameWest.log", "GameEast.log", "Velocity.log"):
            old = run / name
            if old.exists(): old.rename(run / f"{name}.before-{time.time_ns()}")
    print("LAYOUT_RUN", run, flush=True)
    env = dict(os.environ, AISECTOR_REDIS_HOST="127.0.0.1", AISECTOR_REDIS_PORT="6380", AISECTOR_REDIS_PASSWORD="",
               AISECTOR_MONGODB_URI="mongodb://127.0.0.1:27017", AISECTOR_MONGODB_DATABASE="aisector_staging",
               AISECTOR_LOCKED_SPAWN="Spawn")
    processes, logs = {}, {}
    started_redis = False
    proxy_jar = INPUT / "velocity.verified.jar"
    instant_proxy_opts = []
    instant_owned = {
        "packetevents-spigot-2.14.0.jar": "Spawn GameWest GameEast",
        "InstantTransferAgent.local.jar": "Spawn GameWest GameEast",
        "packetevents-velocity-2.14.0.jar": "proxy",
        "InstantTransfer.local.jar": "proxy",
    }

    def log_text(name):
        return (run / f"{name}.log").read_text(encoding="utf-8", errors="replace")

    def start(name, directory, jar, memory, flags=(), jvm_flags=()):
        logs[name] = (run / f"{name}.log").open("w", encoding="utf-8")
        processes[name] = subprocess.Popen(
            [JAVA / "java.exe", "-Xms256M", f"-Xmx{memory}M", "-XX:ActiveProcessorCount=4", *jvm_flags, "-jar", jar, *flags],
            cwd=directory, env=env, stdin=subprocess.PIPE, stdout=logs[name], stderr=subprocess.STDOUT,
            text=True, creationflags=creation_flags
        )

    try:
        with zipfile.ZipFile(args.archive) as archive:
            for sector in ("Spawn", "GameWest", "GameEast"):
                directory = run / sector
                if args.resume:
                    props = (directory / "server.properties").read_text()
                    config = (directory / "plugins/aiSECTOR/config.yml").read_text()
                    if not (directory / "world/level.dat").is_file() or not all(
                            item in props.splitlines() for item in ("server-ip=127.0.0.1", "online-mode=false", f"server-port={ports[sector]}")):
                        raise RuntimeError("Unexpected resume backend: " + sector)
                    if not all(item in config for item in ('database: "aisector_staging"', 'port: 6380', '127.0.0.1')):
                        raise RuntimeError("Unexpected resume data endpoints")
                    jar = directory / "plugins/aiSector.jar"
                    if args.keep_plugin_jars:
                        if not jar.is_file():
                            raise RuntimeError("Missing deployed backend plugin: " + str(jar))
                    else:
                        shutil.copy2(jar, run / f"{sector}-before-{time.time_ns()}.jar")
                        shutil.copy2(REPO / "aiSector/target/aiSector-2.0.0-SNAPSHOT.jar", jar)
                    continue
                world_files(directory, ports[sector], 31415926 if sector == "Spawn" else 2718281828)
                props = directory / "server.properties"
                props.write_text(props.read_text().replace("white-list=true", "white-list=false"))
                for entry in archive.infolist():
                    if entry.filename.startswith(f"{sector}/world/"):
                        target = (run / entry.filename).resolve()
                        target.relative_to(directory / "world")
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with archive.open(entry) as stream, target.open("xb") as output:
                            shutil.copyfileobj(stream, output)
                plugins = directory / "plugins"
                config = plugins / "aiSECTOR"
                config.mkdir(parents=True)
                (config / "config.yml").write_text(f'this-sector-name: "{sector}"\nredis:\n  host: "127.0.0.1"\n'
                    '  port: 6380\nmongodb:\n  uri: "mongodb://127.0.0.1:27017"\n  database: "aisector_staging"\n')
                (config / "sectors.yml").write_bytes(archive.read("sectors.yml"))
                shutil.copy2(REPO / "aiSector/target/aiSector-2.0.0-SNAPSHOT.jar", plugins / "aiSector.jar")

        proxy = run / "proxy"
        (proxy / "plugins").mkdir(parents=True, exist_ok=bool(args.resume))
        proxy_plugin = proxy / "plugins/VelocityPlugin.jar"
        if args.keep_plugin_jars:
            if not proxy_plugin.is_file():
                raise RuntimeError("Missing deployed proxy plugin: " + str(proxy_plugin))
        else:
            shutil.copy2(REPO / "VelocityPlugin/target/aiSector-Velocity-2.0.0-SNAPSHOT.jar", proxy_plugin)
        proxy_config = ('config-version = "2.9"\nbind = "127.0.0.1:25565"\n'
            'online-mode = false\nforce-key-authentication = false\nplayer-info-forwarding-mode = "none"\n'
            '[servers]\nSpawn = "127.0.0.1:25566"\nGameWest = "127.0.0.1:25567"\n'
            'GameEast = "127.0.0.1:25568"\ntry = ["Spawn"]\n[forced-hosts]\n"127.0.0.1" = ["Spawn"]\n')
        if not args.resume:
            (proxy / "velocity.toml").write_text(proxy_config)
        else:
            import tomllib
            settings = tomllib.loads((proxy / "velocity.toml").read_text())
            if settings.get("bind") != f"{join_host}:25565":
                raise RuntimeError("Unexpected resume proxy binding")
            if args.public_bind:
                if (settings.get("online-mode") is not True
                        or settings.get("player-info-forwarding-mode") != "modern"
                        or settings.get("force-key-authentication") is not True):
                    raise RuntimeError("Public proxy requires authenticated modern forwarding")
                secret = proxy / str(settings.get("forwarding-secret-file", "forwarding.secret"))
                if not secret.is_file() or len(secret.read_text().strip()) < 32:
                    raise RuntimeError("Missing or weak forwarding secret")
        if args.instant:
            if args.resume:
                backup = run / f"before-instant-{time.time_ns()}"
                for sector in ("Spawn", "GameWest", "GameEast"):
                    source = run / sector / "world"
                    target = backup / sector / "world"
                    shutil.copytree(source, target, ignore=shutil.ignore_patterns("session.lock"))
                    actual = digest_tree(source)
                    if not actual or actual != digest_tree(target):
                        raise RuntimeError("Could not verify pre-InstantTransfer world backup")
                print("VERIFIED_WORLD_BACKUP", backup, flush=True)
            review = TEMP / "instant-transfer-review"
            extra = {
                "packetevents-spigot-2.14.0.jar": review / "packetevents-spigot-2.14.0.jar",
                "InstantTransferAgent.local.jar": review / "backend-agent/spigot/target/original-instanttransfer-agent-0.1.0.jar",
                "packetevents-velocity-2.14.0.jar": review / "packetevents-velocity-2.14.0.jar",
                "InstantTransfer.local.jar": review / "proxy-plugin/target/InstantTransfer-0.1.0.jar",
            }
            proxy_jar = TEMP / "velocity-instant-263/proxy/build/libs/velocity-proxy-4.3.1-all.jar"
            if not all(p.is_file() for p in (*extra.values(), proxy_jar)):
                raise RuntimeError("Experimental components have not been built")
            # Gradle may rebuild its output while the current JVM still needs classes from it.
            # Keep each running instance on its own immutable copy instead.
            proxy_copy = run / f"velocity-instant-{time.time_ns()}.jar"
            shutil.copy2(proxy_jar, proxy_copy)
            proxy_jar = proxy_copy
            for name, directories in instant_owned.items():
                for directory in directories.split():
                    dest = run / directory / "plugins" / name
                    if dest.exists() and dest.read_bytes() != extra[name].read_bytes():
                        if not (args.update_instant and name in {"InstantTransfer.local.jar", "InstantTransferAgent.local.jar"}):
                            raise RuntimeError("Refusing to replace existing experimental plugin: " + str(dest))
                        dest.rename(run / f"{directory}-before-{time.time_ns()}-{name}")
                    if not dest.exists(): shutil.copy2(extra[name], dest)
            instant_proxy_opts = ["-Dvelocity.seamlessSwitch=true", "-Dvelocity.seamlessSpawn=true"]
        elif args.resume:
            for name, directories in instant_owned.items():
                for directory in directories.split():
                    dest = run / directory / "plugins" / name
                    if dest.exists():
                        dest.rename(run / f"rollback-{directory}-{time.time_ns()}-{name}")
        command(["wsl", "-d", "Ubuntu", "-u", "root", "--", "redis-server", "--port", "6380", "--bind", "127.0.0.1",
                 "--protected-mode", "yes", "--save", "", "--appendonly", "no", "--daemonize", "yes",
                 "--pidfile", "/var/run/aisector-layout-live-6380.pid"])
        started_redis = True
        global_spawn = json.dumps(dict(sector="Spawn", x=2050.5, y=126, z=2050.5, yaw=180, pitch=0))
        if redis("SET", "aisector:global_spawn", global_spawn) != "OK": raise RuntimeError("Staging Redis not ready")
        for sector in ("Spawn", "GameWest", "GameEast"):
            start(sector, run / sector, INPUT / "paper.verified.jar", 1800, ["--nogui"])
        # The generated platform can exist even if the vanilla spawn-setting command failed.
        # Do not expose the proxy until Paper has actually accepted the coordinates.
        deadline = time.monotonic() + 100
        while "Done (" not in log_text("Spawn"):
            if processes["Spawn"].poll() is not None or time.monotonic() >= deadline:
                raise RuntimeError("Spawn did not start; see " + str(run))
            time.sleep(.5)
        processes["Spawn"].stdin.write("setworldspawn 2050 126 2050 180 0\n")
        processes["Spawn"].stdin.flush()
        deadline = time.monotonic() + 20
        while not re.search(r"spawn point.*2050.*126.*2050", log_text("Spawn"), re.IGNORECASE):
            if processes["Spawn"].poll() is not None or time.monotonic() >= deadline:
                raise RuntimeError("Paper did not confirm the world spawn; see " + str(run))
            time.sleep(.5)
        start("Velocity", proxy, proxy_jar, 768, jvm_flags=instant_proxy_opts)
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            if any(p.poll() is not None for p in processes.values()):
                raise RuntimeError("Server exited at startup; see " + str(run))
            if all(port_ready(name) and "Done (" in log_text(name) for name in ports):
                if args.instant and any("Error occurred while enabling" in log_text(name)
                                        for name in ("Spawn", "GameWest", "GameEast", "Velocity")):
                    raise RuntimeError("Plugin failed to enable in the InstantTransfer layout")
                if redis("PUBSUB", "NUMSUB", "sector-transfer").splitlines()[-1] == "1": break
            time.sleep(1)
        else:
            raise RuntimeError("Startup timeout; see " + str(run))
        (run / "READY.json").write_text(json.dumps({"join": f"{join_host}:25565", "ports": ports, "instant": args.instant,
                                             "pids": {name: p.pid for name, p in processes.items()}}, indent=2))
        print("READY", run, f"{join_host}:25565", flush=True)
        stop = run / "STOP"
        while True:
            if any(p.poll() is not None for p in processes.values()):
                raise RuntimeError("Server exited unexpectedly; see " + str(run))
            if stop.exists():
                baseline = len(log_text("Velocity"))
                if "Planned shutdown checkpoints enabled." not in log_text("Velocity"):
                    break
                processes["Velocity"].stdin.write("redeploy-stop 15\n")
                processes["Velocity"].stdin.flush()
                deadline = time.monotonic() + 66
                while time.monotonic() < deadline:
                    if any(p.poll() is not None for p in processes.values()):
                        raise RuntimeError("Server exited during checkpoint; see " + str(run))
                    outcome = log_text("Velocity")[baseline:]
                    if "SAFE_SHUTDOWN_READY " in outcome or "SAFE_SHUTDOWN_ABORTED " in outcome: break
                    time.sleep(.5)
                if "SAFE_SHUTDOWN_READY " in log_text("Velocity")[baseline:]:
                    break
                stop.rename(run / f"STOP.aborted-{time.time_ns()}")
                print("STOP_ABORTED live sectors were not shut down", flush=True)
            time.sleep(2)
    finally:
        # Stop Paper first: Bukkit must recognize a planned shutdown before its
        # players disconnect, so combat logout does not penalize them.
        for name in ("Spawn", "GameWest", "GameEast"):
            process = processes.get(name)
            if process is not None and process.poll() is None:
                try:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                except OSError:
                    print("Could not signal stop", name, flush=True)
        for name, process in processes.items():
            if process.poll() is None:
                try:
                    if name == "Velocity":
                        process.stdin.write("shutdown\n")
                        process.stdin.flush()
                    process.wait(timeout=80)
                except (OSError, subprocess.TimeoutExpired):
                    print("Could not stop", name, "PID", process.pid, flush=True)
        if started_redis and all(p.poll() is not None for p in processes.values()):
            redis("SHUTDOWN", "NOSAVE")
        for log in logs.values():
            log.close()


if __name__ == "__main__":
    main()
