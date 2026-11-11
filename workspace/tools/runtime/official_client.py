"""Prepare an isolated official 26.3 offline test client; never read launcher accounts or tokens."""
import hashlib
import json
import os
import re
import urllib.request
from pathlib import Path

VERSION = "26.3"
META_URL = "https://piston-meta.mojang.com/v1/packages/5df1795e16eea19d75b923d66ecca9ce183353c5/26.3.json"
META_SHA1 = "5df1795e16eea19d75b923d66ecca9ce183353c5"
MINECRAFT = Path(os.environ["APPDATA"]) / ".minecraft"
JAVA = Path(r"C:\Program Files\Java\jdk-25.0.2\bin\java.exe")


def sha1(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha1").hexdigest()


def verified_artifact(info, existing, target):
    if existing.is_file() and sha1(existing) == info["sha1"]:
        return existing
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or sha1(target) != info["sha1"]:
        with urllib.request.urlopen(info["url"], timeout=60) as response, target.open("wb") as output:
            import shutil
            shutil.copyfileobj(response, output)
    if sha1(target) != info["sha1"]:
        raise RuntimeError("Official artifact hash mismatch: " + target.name)
    return target


def allowed(library):
    if "rules" not in library:
        return True
    allow = False
    for rule in library["rules"]:
        os_rule = rule.get("os", {})
        if os_rule.get("name", "windows") != "windows":
            continue
        if "arch" in os_rule and os_rule["arch"] not in ("x86_64", "amd64"):
            continue
        if "version" in os_rule and not re.search(os_rule["version"], "10.0"):
            continue
        if rule.get("features"):
            continue
        allow = rule["action"] == "allow"
    return allow


def prepare(cache):
    cache.mkdir(parents=True, exist_ok=True)
    data = urllib.request.urlopen(META_URL, timeout=30).read()
    if hashlib.sha1(data).hexdigest() != META_SHA1:
        raise RuntimeError("Official version metadata hash mismatch")
    manifest = json.loads(data)
    (cache / "26.3.json").write_bytes(data)
    client = verified_artifact(manifest["downloads"]["client"], MINECRAFT / "versions/26.3/26.3.jar", cache / "client.jar")
    libraries = []
    for library in manifest["libraries"]:
        if allowed(library):
            artifact = library["downloads"]["artifact"]
            libraries.append(verified_artifact(artifact, MINECRAFT / "libraries" / artifact["path"],
                                               cache / "libraries" / artifact["path"]))
    index = manifest["assetIndex"]
    assets = MINECRAFT / "assets"
    verified_artifact(index, assets / "indexes" / (index["id"] + ".json"), cache / "assets/indexes" / (index["id"] + ".json"))
    # Installed assets are only reused read-only; missing objects are reported by the game, never replaced in the user's directory.
    return manifest, os.pathsep.join(map(str, [*libraries, client])), assets


def arguments(prepared, directory, name, port=25565):
    if not re.fullmatch(r"RwS[A-Za-z0-9]{4,13}", name):
        raise ValueError("Only generated test identities are accepted")
    if port != 25565:
        raise ValueError("Client is restricted to local staging proxy")
    manifest, classpath, assets = prepared
    directory.mkdir(parents=True, exist_ok=False)
    # Low rendering load, no external telemetry/resource packs; all options belong to the isolated test directory.
    (directory / "options.txt").write_text("renderDistance:2\nsimulationDistance:5\nmaxFps:20\nsoundCategory_master:0.0\n"
                                          "pauseOnLostFocus:false\nonboardAccessibility:false\njoinedFirstServer:true\n", encoding="utf-8")
    import uuid
    raw = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    raw[6] = (raw[6] & 15) | 48
    raw[8] = (raw[8] & 63) | 128
    player_uuid = str(uuid.UUID(bytes=bytes(raw)))
    natives = directory / "natives"
    return [str(JAVA), "-Xms256M", "-Xmx768M", "-XX:StackShadowPages=32", "--enable-native-access=ALL-UNNAMED",
            "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-Djava.library.path=" + str(natives / "java"),
            "-Djna.tmpdir=" + str(natives / "jna"), "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + str(natives / "lwjgl"),
            "-Dio.netty.native.workdir=" + str(natives / "netty"), "-Dminecraft.launcher.brand=aiSector-local-smoke",
            "-Dminecraft.launcher.version=1", "-cp", classpath, manifest["mainClass"],
            "--username", name, "--version", VERSION, "--versionType", "release", "--gameDir", str(directory),
            "--assetsDir", str(assets), "--assetIndex", manifest["assetIndex"]["id"], "--uuid", player_uuid,
            "--accessToken", "0", "--clientId", "", "--xuid", "", "--width", "640", "--height", "360",
            "--quickPlayPath", str(directory / "quickplay.json"), "--quickPlayMultiplayer", "127.0.0.1:25565"]
