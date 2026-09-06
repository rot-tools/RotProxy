"""Check the Git index, not the user's machine, for accidental private files."""
from pathlib import PurePosixPath
import re
import subprocess
import sys

paths = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
forbidden_dirs = {".port-tools", "original", "run", "config", "logs", "crash-reports", ".gradle", "build"}
forbidden_suffixes = {".key", ".pem", ".p12", ".pfx", ".jks", ".log", ".hprof", ".zip"}
patterns = [
    rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,})",
    rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    rb"(?:AKIA|ASIA)[A-Z0-9]{16}",
]
problems = []
for name in filter(None, paths):
    path = PurePosixPath(name)
    if (path.parts[0] in forbidden_dirs or path.suffix in forbidden_suffixes
            or path.name.startswith((".env", "rotproxy_profiles", "servers.dat", "launcher_accounts"))
            or (path.suffix == ".jar" and name != "gradle/wrapper/gradle-wrapper.jar")):
        problems.append(name + ": private/generated file type")
        continue
    content = subprocess.check_output(["git", "show", ":" + name])
    if any(re.search(pattern, content) for pattern in patterns):
        problems.append(name + ": possible credential (value redacted)")
if problems:
    print("Public-file check failed:\n" + "\n".join(problems))
    sys.exit(1)
print("Public-file check passed; no forbidden tracked files or recognized credentials.")
