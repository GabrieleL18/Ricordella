"""
Genera EnglishStrings.kt da tools/i18n/en.txt e segnala i testi del codice ancora senza traduzione.

    python tools/i18n/generate.py
"""
import os
import re

root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
src = os.path.join(root, 'app', 'src', 'main', 'java', 'com', 'ricordella', 'app')
out = os.path.join(src, 'core', 'i18n', 'EnglishStrings.kt')

pairs = {}
for line in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'en.txt'), encoding='utf-8'):
    line = line.rstrip('\n')
    if not line or line.startswith('#') or ' ||| ' not in line:
        continue
    it, en = line.split(' ||| ', 1)
    pairs[it.replace('⏎', '\n')] = en.replace('⏎', '\n')

# Chiavi usate nel codice (valore a runtime delle stringhe dentro tr()/trf()).
CALL = re.compile(r'(?<![\w.])trf?\("((?:[^"\\]|\\.)*)"')


def unescape(k):
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t'}.get(m.group(1), m.group(1)), k)


used = set()
for d, _, fs in os.walk(src):
    for f in fs:
        if f.endswith('.kt') and f != 'EnglishStrings.kt':
            used.update(unescape(k) for k in CALL.findall(open(os.path.join(d, f), encoding='utf-8').read()))
cats = open(os.path.join(src, 'data', 'local', 'database', 'BuiltInCategories.kt'), encoding='utf-8').read()
used.update(re.findall(r'ItemKind\.\w+ to "([^"]+)"', cats))


def kotlin(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', '\\n') + '"'


entries = [f'    {kotlin(k)} to {kotlin(v)},' for k, v in pairs.items() if k != v]
with open(out, 'w', encoding='utf-8') as f:
    f.write('package com.ricordella.app.core.i18n\n\n')
    f.write('/*\n * Traduzioni inglesi: chiave = testo italiano come scritto nel codice.\n')
    f.write(' * FILE GENERATO da tools/i18n/generate.py a partire da tools/i18n/en.txt: non modificarlo a mano.\n */\n')
    f.write('internal val EnglishStrings: Map<String, String> = hashMapOf(\n')
    f.write('\n'.join(entries))
    f.write('\n)\n')

missing = sorted(k for k in used if k not in pairs and re.search(r'[a-zàèéìòù]{2}', k))
unused = sorted(k for k in pairs if k not in used)
print(f'{len(entries)} traduzioni scritte; mancanti: {len(missing)}; non più usate: {len(unused)}')
for k in missing:
    print('MISSING:', repr(k))
