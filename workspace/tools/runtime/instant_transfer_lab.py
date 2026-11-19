"""Test the experimental 26.3 proxy on private loopback ports without stopping aiSector."""
import base64
import hashlib
import json
from pathlib import Path
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
    ports = {"GameWest": 25576, "GameEast": 25577, "Proxy": 25575}
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
    prepared = prepare(TEMP / "aisector-official-client-26.3")
    command([JAVA / "javac.exe", "--add-modules", "jdk.attach", "-cp", prepared[1], "-d", run,
             REPO / "tools/runtime/AttachSmoke.java", REPO / "tools/runtime/ClientControlAgent.java"])
    manifest = run / "client.mf"
    manifest.write_text("Manifest-Version: 1.0\nAgent-Class: ClientControlAgent\n\n")
    agent = run / "client.jar"
    command([JAVA / "jar.exe", "cfm", agent, manifest, "-C", run, "ClientControlAgent.class"])
    processes, logs = {}, {}
    client = None

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
                stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)

    def control(action, value=""):
        output = run / (uuid.uuid4().hex + ".result")
        payload = action + "|" + base64.b64encode(value.encode()).decode() + "|" + str(output)
        command([JAVA / "java.exe", "--add-modules", "jdk.attach", "-cp", run, "AttachSmoke", client.pid, agent, payload])
        wait(output.exists, "agent result", 15)
        return output.read_text()

    try:
        with zipfile.ZipFile(archive) as data:
            for sector in ("GameWest", "GameEast"):
                base = run / sector
                world_files(base, ports[sector], 2718281828)
                data.extractall(run, [path for path in data.namelist() if path.startswith(sector + "/world/")])
                plugins = base / "plugins"
                plugins.mkdir()
                (base / "whitelist.json").write_text(json.dumps([{"uuid": str(ident), "name": name}]))
                shutil.copy2(review / "packetevents-spigot-2.14.0.jar", plugins)
                shutil.copy2(review / "backend-agent/spigot/target/original-instanttransfer-agent-0.1.0.jar",
                             plugins / "InstantTransferAgent-0.1.0.jar")
                start(sector, base, paper, (), "--nogui")
        base = run / "proxy"
        (base / "plugins").mkdir(parents=True)
        shutil.copy2(review / "packetevents-velocity-2.14.0.jar", base / "plugins")
        shutil.copy2(review / "proxy-plugin/target/InstantTransfer-0.1.0.jar", base / "plugins")
        (base / "velocity.toml").write_text('config-version = "2.9"\nbind = "127.0.0.1:25575"\nonline-mode = false\n'
            'force-key-authentication = false\nplayer-info-forwarding-mode = "none"\n[servers]\n'
            'GameWest = "127.0.0.1:25576"\nGameEast = "127.0.0.1:25577"\ntry = ["GameWest"]\n'
            '[forced-hosts]\n"127.0.0.1" = ["GameWest"]\n')
        start("Proxy", base, proxy_jar, ("-Dvelocity.seamlessSwitch=true",))
        wait(lambda: all("Done (" in log(sector) and listening(port) for sector, port in ports.items()), "startup")
        if any("Error occurred while enabling" in log(sector) for sector in ("GameWest", "GameEast")):
            raise RuntimeError("Backend agent failed to enable")
        opts = arguments(prepared, run / name, name)
        assert opts[-1] == "127.0.0.1:25565"
        opts[-1] = "127.0.0.1:25575"
        logs["client"] = (run / "client.log").open("w", encoding="utf-8")
        client = subprocess.Popen(opts, cwd=run, stdout=logs["client"], stderr=subprocess.STDOUT)
        wait(lambda: name + " -> GameWest has connected" in log("Proxy"), "first join")
        time.sleep(4)
        results = {}
        for target in ("GameEast", "GameWest"):
            connection = name + " -> " + target + " has connected"
            before = log("Proxy").count(connection)
            results[target] = control("trace-command", "server " + target)
            print(target, results[target], sep="\n", flush=True)
            assert len(results[target].splitlines()) == 1 and results[target].endswith(" PLAY"), results[target]
            assert log("Proxy").count(connection) == before + 1
        assert log("Proxy").count("zero-screen") >= 2
        (run / "results.json").write_text(json.dumps(results, indent=2))
        print("NO_LOADING_SCREENS_OBSERVED", run)
    finally:
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


if __name__ == "__main__":
    main()
