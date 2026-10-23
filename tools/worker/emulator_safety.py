"""Fail closed before any destructive test or emulator-only adapter opens a client."""
from urllib.parse import urlparse


def require_emulator(project, host):
    if not project.startswith('demo-'): raise ValueError('A demo-* project is required')
    parsed = urlparse('http://' + host)
    if parsed.hostname not in ('localhost', '127.0.0.1', '::1') or not parsed.port or parsed.username or parsed.password:
        raise ValueError('A loopback emulator with an explicit port is required')
    return parsed


def storage_deleter(project, host, bucket):
    require_emulator(project, host)
    if not bucket.startswith(project + '.'): raise ValueError('Bucket must belong to the demo project')
    def delete(path):
        from urllib.parse import quote
        import requests
        if not path.startswith('videos/') or len(path.split('/')) != 3: raise ValueError('Unexpected video path')
        result = requests.delete(f'http://{host}/v0/b/{quote(bucket, safe="")}/o/{quote(path, safe="")}', headers={'Authorization':'Bearer owner'}, timeout=10)
        if result.status_code != 404: result.raise_for_status()
    return delete
