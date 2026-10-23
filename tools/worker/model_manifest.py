"""Immutable model revisions and hash verification. No credentials are read here."""
from pathlib import Path
import hashlib, json
ROOT = Path(__file__).resolve().parent
MANIFEST = json.loads((ROOT / "model-manifest.json").read_text(encoding="utf-8"))
def identity(repo):
    if repo not in MANIFEST: raise ValueError(f"No reviewed model pin for {repo}; add its revision and hashes first")
    return hashlib.sha256(json.dumps(MANIFEST[repo], sort_keys=True).encode()).hexdigest()[:16]
def download(repo, file):
    from huggingface_hub import hf_hub_download
    entry = MANIFEST[repo]
    expected = entry["sha256"][file]
    path = hf_hub_download(repo, file, revision=entry["revision"], cache_dir=str(ROOT / "models"))
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""): digest.update(chunk)
    if digest.hexdigest() != expected: raise ValueError(f"Model integrity check failed: {repo}/{file}")
    return path
