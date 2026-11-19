"""Build a new, separate spawn and two identical pre-generated game-world copies from layout smoke backups."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile

from layout_smoke import INPUT, SERVER_HASHES, digest_tree, world_files
from staging_smoke import JAVA, REPO, TEMP, command, listening


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("smoke_run", type=Path, help="Successful isolated layout smoke evidence")
    args = parser.parse_args()
    source = args.smoke_run.resolve()
    if source.parent != TEMP or not source.name.startswith("aisector-layout-smoke-") or not (source / "results.json").is_file():
        raise RuntimeError("Requires a successful isolated layout smoke directory")
    if listening(25566):
        raise RuntimeError("Expected free loopback Paper port 25566")
    manifest = json.loads((source / "backup-manifest.json").read_text(encoding="utf-8"))
    for key in ("game", "spawn"):
        if not manifest[key] or digest_tree(source / "backup" / key) != manifest[key]:
            raise RuntimeError("Backup integrity mismatch: " + key)
    paper = INPUT / "paper.verified.jar"
    with paper.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != SERVER_HASHES["paper"]:
            raise RuntimeError("Unverified Paper JAR")
    run = Path(tempfile.mkdtemp(prefix="aisector-map-package-", dir=TEMP))
    print("MAP_PACKAGE_RUN", run, flush=True)
    report = ET.parse(REPO / "aiSector/target/surefire-reports/TEST-ai.aisector.user.HomeSlotRepositoryTest.xml")
    cp = next(x.attrib["value"] for x in report.findall("./properties/property") if x.attrib["name"] == "java.class.path")
    helper = run / "generator"
    helper.mkdir()
    command([JAVA / "javac.exe", "-cp", cp, "-d", helper, REPO / "tools/runtime/PrepareLayoutWorld.java"])
    (helper / "plugin.yml").write_text("name: PrepareLayoutWorld\nversion: '1'\nmain: PrepareLayoutWorld\n"
            "api-version: '26.1'\ncommands:\n  layoutgenerate: {}\n")
    jar = run / "generator.jar"
    command([JAVA / "jar.exe", "cf", jar, "-C", helper, "."])

    def run_paper(name, configure):
        directory = run / name
        world_files(directory, 25566, 2718281828 if name == "game" else 31415926)
        shutil.copytree(source / "backup" / name, directory / "world")
        if digest_tree(directory / "world") != manifest[name]: raise RuntimeError("Copied world hash mismatch")
        if name == "game":
            plugins = directory / "plugins"
            plugins.mkdir()
            shutil.copy2(jar, plugins / "generator.jar")
        log_file = run / (name + ".log")
        with log_file.open("w", encoding="utf-8") as log:
            process = subprocess.Popen([JAVA / "java.exe", "-Xms512M", "-Xmx2500M", "-XX:ActiveProcessorCount=6",
                "-jar", paper, "--nogui"], cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            def read_log():
                return log_file.read_text(encoding="utf-8", errors="replace")
            def send(text):
                process.stdin.write(text + "\n")
                process.stdin.flush()
            def wait(condition, timeout):
                deadline = time.monotonic() + timeout
                while not condition():
                    if process.poll() is not None or time.monotonic() >= deadline:
                        raise RuntimeError("Paper generation did not complete: " + str(log_file))
                    time.sleep(1)
            ready = False
            try:
                wait(lambda: "Done (" in read_log(), 120)
                ready = True
                configure(directory, send, wait, read_log)
                send("save-all flush")
                wait(lambda: "Saved the game" in read_log(), 120)
            finally:
                if process.poll() is None:
                    if ready: send("stop")
                    else: process.terminate()
                    process.wait(timeout=120)
        return directory / "world"

    def generate_map(directory, send, wait, read_log):
        send("layoutgenerate")
        wait(lambda: (directory / "chunks-ready.txt").is_file(), 780)
        print((directory / "chunks-ready.txt").read_text().strip(), flush=True)

    def prepare_spawn(directory, send, wait, read_log):
        send("forceload add 2038 2038 2062 2062")
        def spawn_loaded():
            if "SPAWN_AREA_READY" in read_log(): return True
            send("execute if loaded 2038 125 2038 if loaded 2062 125 2062 run say SPAWN_AREA_READY")
            return False
        wait(spawn_loaded, 50)
        for text in (
                "fill 2038 125 2038 2062 125 2062 minecraft:smooth_stone",
                "fill 2038 126 2038 2062 127 2038 minecraft:glass",
                "fill 2038 126 2062 2062 127 2062 minecraft:glass",
                "fill 2038 126 2039 2038 127 2061 minecraft:glass",
                "fill 2062 126 2039 2062 127 2061 minecraft:glass",
                "setblock 2050 126 2046 minecraft:sponge",
                "setblock 2050 127 2046 minecraft:stone_button[face=floor,facing=south]",
                "setworldspawn 2050 126 2050 180 0",
                "execute if block 2050 126 2046 minecraft:sponge if block 2050 127 2046 minecraft:stone_button[face=floor] run say SPONGE_BUTTON_READY"):
            send(text)
        wait(lambda: "SPONGE_BUTTON_READY" in read_log(), 40)
        wait(lambda: re.search(r"spawn point.*2050.*126.*2050", read_log(), re.IGNORECASE), 20)

    game = run_paper("game", generate_map)
    spawn = run_paper("spawn", prepare_spawn)
    bundle = run / "aisector-mapa-1000x1000.zip"
    outputs = {"GameWest": game, "GameEast": game, "Spawn": spawn}
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
        for sector, world in outputs.items():
            for relative in digest_tree(world):
                archive.write(world / relative, f"{sector}/world/{relative}")
        archive.write(REPO / "aiSector/src/main/resources/sectors-locked-spawn.yml", "sectors.yml")
        archive.write(REPO / "aiSector/SECTOR_LAYOUT.md", "SECTOR_LAYOUT.md")
    with zipfile.ZipFile(bundle) as archive:
        for sector, world in outputs.items():
            for path, expected in digest_tree(world).items():
                with archive.open(f"{sector}/world/{path}") as stream:
                    if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                        raise RuntimeError("Archive integrity mismatch: " + path)
    with bundle.open("rb") as stream:
        print("MAP_PACKAGE_READY", bundle, hashlib.file_digest(stream, "sha256").hexdigest(), flush=True)


if __name__ == "__main__":
    main()
