# Scans the whole git history (all branches and tags) for committed secrets and sensitive files.
# Prints only a redacted fingerprint (first 4 characters and the length), never a secret value.
# Usage: python -I tools/security/secretscan.py .             (history)
#        python -I tools/security/secretscan.py . --worktree  (uncommitted changes and new, not-ignored files: run it
#                                                               before a commit)
import re, subprocess, sys, collections, os
repo = sys.argv[1]
worktree = '--worktree' in sys.argv[2:]
pats = {
  'private-key-block': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----'),
  'gcp-service-account': re.compile(r'"type"\s*:\s*"service_account"'),
  'json-private_key': re.compile(r'"private_key"\s*:\s*"-----'),
  'google-api-key': re.compile(r'AIza[0-9A-Za-z_\-]{35}'),
  'github-token': re.compile(r'\b(?:ghp|gho|ghu|ghs|ghr)_[0-9A-Za-z]{36}\b|github_pat_[0-9A-Za-z_]{40,}'),
  'aws-key': re.compile(r'\bAKIA[0-9A-Z]{16}\b'),
  'slack-token': re.compile(r'xox[baprs]-[0-9A-Za-z-]{10,}'),
  'password-assignment': re.compile(r'(?i)\b(?:password|passwd|pwd|storePassword|keyPassword)\b\s*[:=]\s*["\']([^"\'\s$%{}<>]{6,})["\']'),
  'firebase-oauth-client-secret': re.compile(r'GOCSPX-[0-9A-Za-z_\-]{20,}'),
  'maptiler-key-literal': re.compile(r'(?i)maptiler[^\n]{0,40}key[^\n]{0,10}[=:]\s*["\']?([0-9A-Za-z]{20,})'),
  'base64-keystore': re.compile(r'(?i)keystore[_A-Z]*\s*[:=]\s*["\']?[A-Za-z0-9+/]{200,}'),
}
sens_files = re.compile(r'(?i)(\.jks|\.keystore|\.p12|\.pem|keystore\.properties|google-services\.json|GoogleService-Info\.plist|worker\.local\.json|demo-accounts\.local\.json|test-accounts\.local\.txt|(^|/)\.env(\.[a-z]+)?$|service[-_]?account.*\.json)$')


def git(*args):
    return subprocess.run(['git', '-C', repo, *args], capture_output=True, text=True, encoding='utf-8', errors='replace').stdout


if worktree:
    # The same format as the history log below, built from the uncommitted diff and the new files.
    untracked = [f for f in git('ls-files', '--others', '--exclude-standard').splitlines() if f]
    changed = [f for f in git('diff', 'HEAD', '--name-only').splitlines() if f]
    names = '\n'.join(['@@worktree'] + untracked + changed)
    parts = ['@@worktree', git('diff', 'HEAD', '-U0', '--no-color')]
    for f in untracked:
        try:
            with open(os.path.join(repo, f), encoding='utf-8', errors='replace') as fh:
                body = fh.read()
        except OSError:
            continue
        if '\x00' in body[:4096]:
            continue  # binary
        parts.append('+++ b/' + f)
        parts.extend('+' + line for line in body.splitlines())
    log = '\n'.join(parts)
else:
    # sensitive file names ever added
    names = git('log', '--all', '--diff-filter=A', '--name-only', '--format=@@%h')
    log = git('log', '--all', '-p', '--no-color', '--format=@@%h', '-U0')

cur = None; files = []
for line in names.splitlines():
    if line.startswith('@@'): cur = line[2:]; continue
    if line and sens_files.search(line): files.append((cur, line))
print('== sensitive file names', 'in uncommitted changes:' if worktree else 'ever committed:', len(files))
for c, f in files: print('  ', c, f)
commit = None; path = None; hits = collections.OrderedDict()
for line in log.splitlines():
    if line.startswith('@@') and not line.startswith('@@ '): commit = line[2:]; continue
    if line.startswith('+++ '): path = line[6:] if line.startswith('+++ b/') else line[4:]; continue
    if not line.startswith('+') or line.startswith('+++'): continue
    for name, p in pats.items():
        for m in p.finditer(line):
            v = m.group(1) if m.groups() else m.group(0)
            red = v[:4] + '...' + f'(len {len(v)})'
            key = (name, path, red)
            hits.setdefault(key, []).append(commit)
print('== pattern hits (value redacted):', len(hits))
for (name, path, red), cs in hits.items():
    print(f'  {name:28} {path}  {red}  first:{cs[-1]} n={len(cs)}')
