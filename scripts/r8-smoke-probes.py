#!/usr/bin/env python3
"""Fail closed if R8 removed SMBJ's reflection contract; emit names for runtime smoke tests."""
import argparse
import hashlib
import json
from pathlib import Path
import re

CONTRACTS = (
    ('com.hierynomus.smbj.SMBClient', 'connectionClosed', 'com.hierynomus.smbj.event.ConnectionClosed'),
    ('com.hierynomus.smbj.connection.Connection', 'sessionLogoff', 'com.hierynomus.smbj.event.SessionLoggedOff'),
    ('com.hierynomus.smbj.session.Session', 'disconnectTree', 'com.hierynomus.smbj.event.TreeDisconnected'),
)


def probes(mapping):
    classes, methods = {}, {}
    owner = None
    for line in mapping.splitlines():
        match = re.fullmatch(r'(\S+) -> (\S+):', line)
        if match:
            owner, renamed = match.groups()
            classes[owner] = renamed
            methods[owner] = []
        elif owner and line.startswith('    ') and not line.lstrip().startswith('#'):
            methods[owner].append(line.strip())
    result = []
    for owner, method, event in CONTRACTS:
        matches = [re.search(r'void ' + re.escape(method) + r'\(' + re.escape(event) + r'\)(?::\d+)* -> (\S+)$', line)
                   for line in methods.get(owner, [])]
        names = {m.group(1) for m in matches if m}
        if len(names) != 1 or event not in classes:
            raise ValueError(f'Minified SMB callback missing or ambiguous: {owner}.{method}({event})')
        result.append({'owner': classes[owner], 'method': names.pop(), 'event': classes[event]})
    annotation = 'net.engio.mbassy.listener.Handler'
    if annotation not in classes:
        raise ValueError('Runtime SMB handler annotation is absent')
    invocation = 'net.engio.mbassy.dispatch.ReflectiveHandlerInvocation'
    context = 'net.engio.mbassy.subscription.SubscriptionContext'
    constructor = re.compile(r'void <init>\(' + re.escape(context) + r'\)(?::\d+)* -> <init>$')
    if context not in classes or not any(constructor.search(line) for line in methods.get(invocation, [])):
        raise ValueError('Minified SMB reflective invocation constructor is absent')
    return {'handlers': result, 'annotation': classes[annotation],
            'invocation': {'owner': classes[invocation], 'context': classes[context]},
            'sevenzip': 'net.sf.sevenzipjbinding.SevenZip' in classes,
            'mappingSha256': hashlib.sha256(mapping.encode()).hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mapping', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = probes(args.mapping.read_text())
    except ValueError as error:
        parser.error(str(error))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    else:
        print(json.dumps(result, indent=2))
