"""Check seam, command and locked Spawn transfers on the local 26.3 layout."""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

from official_client import arguments, prepare
from staging_smoke import JAVA, REPO, TEMP, command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=TEMP / "aisector-layout-live-db7j3scu")
    parser.add_argument("--flight", action="store_true", help="Test continuous flight without the transfer suite")
    args = parser.parse_args()
    run = args.run
    marker = run / "READY.json"
    src = REPO / "tools/runtime"
    end = time.monotonic() + 120
    while (run / "STOP").exists() or not marker.exists() or not json.loads(marker.read_text()).get("instant"):
        if time.monotonic() >= end: raise RuntimeError("Experimental layout not ready")
        time.sleep(1)
    pids = json.loads(marker.read_text())["pids"]
    name = "RwSEast" + os.urandom(3).hex()
    output = run / (name + "-instant")
    output.mkdir()
    prepared = prepare(TEMP / "aisector-official-client-26.3")
    report = ET.parse(REPO / "aiSector/target/surefire-reports/TEST-ai.aisector.user.HomeSlotRepositoryTest.xml")
    cp = next(x.attrib["value"] for x in report.findall("./properties/property") if x.attrib["name"] == "java.class.path")
    probe_class = "EastJoinProbe" + name.removeprefix("RwSEast")
    probe_source = output / (probe_class + ".java")
    probe_source.write_text((src / "EastJoinProbe.java").read_text(encoding="utf-8").replace("EastJoinProbe", probe_class),
                            encoding="utf-8")
    command([JAVA / "javac.exe", "--add-modules", "jdk.attach", "-cp", prepared[1], "-d", output,
             src / "AttachSmoke.java", src / "ClientControlAgent.java", probe_source])
    for agent in ("ClientControlAgent", probe_class):
        manifest = output / (agent + ".mf")
        manifest.write_text(f"Manifest-Version: 1.0\nAgent-Class: {agent}\n\n")
        command([JAVA / "jar.exe", "cfm", output / (agent + ".jar"), manifest, "-C", output, agent + ".class"])

    def call(pid, agent, payload):
        file = output / (uuid.uuid4().hex + ".result")
        command([JAVA / "java.exe", "--add-modules", "jdk.attach", "-cp", output, "AttachSmoke",
                 pid, output / (agent + ".jar"), payload(file)])
        end = time.monotonic() + 14
        while not file.is_file() and time.monotonic() < end: time.sleep(.1)
        if not file.is_file(): raise RuntimeError(f"Probe {agent} did not respond")
        result = file.read_text()
        if agent == "ClientControlAgent" and file.with_name(file.name + ".x").is_file():
            (output / "last-crossing.x").write_text(file.with_name(file.name + ".x").read_text())
        return result

    def probe(role, action="state"):
        return call(pids[role], probe_class, lambda file: f"{name}|{file}|{action}")

    bounds = {"Spawn": (2000, 2100, 2000, 2100), "GameWest": (-500, 0, -500, 500),
              "GameEast": (0, 500, -500, 500)}

    def await_ready(*roles):
        end = time.monotonic() + 35
        while time.monotonic() < end:
            for role in roles:
                answer = probe(role)
                if answer != "offline":
                    x, y, z, speed = map(float, answer.split())
                    min_x, max_x, min_z, max_z = bounds[role]
                    if speed > 0 and min_x <= x < max_x and min_z <= z < max_z:
                        return role, (x, y, z)
            time.sleep(.5)
        raise RuntimeError("Player not ready on " + "/".join(roles))

    def fixture(action):
        command([JAVA / "java.exe", "-cp", cp, src / "EastJoinFixture.java", action, name])

    fixture("seed")
    with (output / "client.log").open("w", encoding="utf-8") as log:
        proc = subprocess.Popen(arguments(prepared, output / name, name), cwd=run, stdout=log, stderr=subprocess.STDOUT)

        def client(action, value=""):
            return call(proc.pid, "ClientControlAgent",
                        lambda file: f"{action}|{base64.b64encode(value.encode()).decode()}|{file}")

        try:
            await_ready("GameEast")
            assert probe("GameEast", "grant-god") == "OK"
            assert client("command", "god") == "OK"
            baseline = probe("GameEast", "seed")
            assert baseline.split()[1:3] == ["7", "0.42"], baseline
            if args.flight:
                assert probe("GameEast", "grant-fly") == "OK"
                assert probe("GameEast", "flight-start") == "OK"
                assert client("command", "fly") == "OK"
                flight = [line.split() for line in client("trace-flight").splitlines()]
                zs = [float(row[1]) for row in flight]
                assert zs[-1] - zs[0] > 32 and all(row[2] == "true" for row in flight), flight
                assert all(new >= old - .5 for old, new in zip(zs, zs[1:])), flight
                (output / "results.json").write_text(json.dumps(
                    {"flight": {"blocks": zs[-1] - zs[0], "backward_correction": False}}, indent=2))
                print("INSTANT_LIVE_FLIGHT_OK", output, flush=True)
                return
            results = {}

            def check_transfer(action, value, *targets):
                trace = client(action, value)
                print(action, value, repr(trace), flush=True)
                assert len(trace.splitlines()) == 1 and trace.endswith(" PLAY"), trace
                target, pos = await_ready(*targets)
                arrived = probe(target, "inventory")
                assert arrived == baseline, (target, baseline, arrived)
                bars = int(client("boundary-bars"))
                assert bars <= (0 if target == "Spawn" else 1), (target, bars)
                return {"target": target, "trace": trace, "position": pos, "boundary_bars": bars,
                        "inventory_and_xp": True}

            directions = (("GameEast", "GameWest", "-0.35"), ("GameWest", "GameEast", "0.35"))
            for index, (source, target, x) in enumerate(directions * 3):
                assert probe(source, "seam") == "OK"
                time.sleep(.8)
                trace = client("trace-seam", x)
                print(source, target, repr(trace), flush=True)
                assert len(trace.splitlines()) == 1 and trace.endswith(" PLAY"), trace
                trajectory = [float(line.split()[1]) for line in (output / "last-crossing.x").read_text().splitlines()]
                assert trajectory and all(value * float(x) >= -.01 for value in trajectory), trajectory
                _, pos = await_ready(target)
                arrived_inventory = probe(target, "inventory")
                assert arrived_inventory == baseline, (target, baseline, arrived_inventory)
                center, _, size = map(float, client("border-state").split())
                assert size == 4096 and (center + size / 2 if target == "GameWest" else center - size / 2) == (
                    2 if target == "GameWest" else -2), target
                assert client("boundary-bars") == "1", f"Duplicate or lost bossbar after crossing {index + 1}"
                results[str(index + 1)] = {"target": target, "trace": trace, "position": pos,
                                           "inventory_and_xp": True, "border": True, "boundary_bars": 1,
                                           "source_side_return": False}
            flat_y, flat_z = pos[1:]
            results["spawn"] = check_transfer("trace-command", "spawn", "Spawn")
            assert probe("Spawn", "grant-tppos") == "OK"
            assert client("command", f"tppos 0.5 {flat_y} {flat_z}") == "OK"
            time.sleep(2)
            await_ready("Spawn")
            assert probe("GameWest") == probe("GameEast") == "offline"
            assert probe("Spawn", "spawn-button") == "OK"
            results["sponge"] = check_transfer("trace-block", "2050,127,2046", "GameWest", "GameEast")
            source = results["sponge"]["target"]
            target = "GameEast" if source == "GameWest" else "GameWest"
            assert probe(source, "grant-tppos") == "OK"
            target_x = "0.5" if target == "GameEast" else "-0.5"
            results["tppos"] = check_transfer("trace-command", f"tppos {target_x} {flat_y} {flat_z}", target)
            results["spawn_again"] = check_transfer("trace-command", "spawn", "Spawn")
            (output / "results.json").write_text(json.dumps(results, indent=2))
            print("INSTANT_LIVE_SEAM_OK", output, flush=True)
        finally:
            if proc.poll() is None:
                try:
                    client("exit")
                    proc.wait(timeout=10)
                except Exception:
                    if proc.poll() is None: proc.terminate(); proc.wait(timeout=10)
            time.sleep(2)
            fixture("cleanup")


if __name__ == "__main__":
    main()
