"""
Genera le traduzioni (EnglishStrings.kt, GermanStrings.kt, FrenchStrings.kt, SpanishStrings.kt) dai file
tools/i18n/en.txt, de.txt, fr.txt, es.txt e segnala i testi del codice ancora senza traduzione.

    python tools/i18n/generate.py          # riepilogo per lingua
    python tools/i18n/generate.py de       # elenca anche i testi mancanti in tedesco
"""
import os
import re
import sys

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.dirname(os.path.dirname(here))
src = os.path.join(root, 'app', 'src', 'main', 'java', 'com', 'ricordella', 'app')
LANGS = [('en', 'EnglishStrings', 'inglesi'), ('de', 'GermanStrings', 'tedesche'), ('fr', 'FrenchStrings', 'francesi'), ('es', 'SpanishStrings', 'spagnole')]
GENERATED = {f'{name}.kt' for _, name, _ in LANGS}

# Chiavi usate nel codice (valore a runtime delle stringhe dentro tr()/trf()).
CALL = re.compile(r'(?<![\w.])trf?\("((?:[^"\\]|\\.)*)"')


def unescape(k):
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t'}.get(m.group(1), m.group(1)), k)


used = set()
for d, _, fs in os.walk(src):
    for f in fs:
        if f.endswith('.kt') and f not in GENERATED:
            used.update(unescape(k) for k in CALL.findall(open(os.path.join(d, f), encoding='utf-8').read()))
# Tutte le stringhe letterali del codice: servono a riconoscere i testi tradotti tramite variabile.
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
used_or_literal = set()
for d, _, fs in os.walk(src):
    for f in fs:
        if f.endswith('.kt') and f not in GENERATED:
            used_or_literal.update(unescape(k) for k in LITERAL.findall(open(os.path.join(d, f), encoding='utf-8').read()))
english = set()
cats = open(os.path.join(src, 'data', 'local', 'database', 'BuiltInCategories.kt'), encoding='utf-8').read()
used.update(re.findall(r'ItemKind\.\w+ to "([^"]+)"', cats))


def kotlin(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', '\\n') + '"'


show = sys.argv[1:]
for code, name, adjective in LANGS:
    pairs = {}
    path = os.path.join(here, f'{code}.txt')
    if os.path.exists(path):
        for line in open(path, encoding='utf-8'):
            line = line.rstrip('\n')
            if not line or line.startswith('#') or ' ||| ' not in line:
                continue
            it, translated = line.split(' ||| ', 1)
            # Gli spazi all'inizio e alla fine contano (frasi composte a pezzi): si copiano dall'italiano,
            # così non si perdono se un editor toglie gli spazi a fine riga.
            lead = it[:len(it) - len(it.lstrip(' '))]
            trail = it[len(it.rstrip(' ')):]
            pairs[it.replace('⏎', '\n')] = (lead + translated.strip(' ') + trail).replace('⏎', '\n')
    # Anche le traduzioni uguali all'italiano: altrimenti si ricadrebbe sull'inglese.
    entries = [f'    {kotlin(k)} to {kotlin(v)},' for k, v in pairs.items() if k != v or code != 'en']
    with open(os.path.join(src, 'core', 'i18n', f'{name}.kt'), 'w', encoding='utf-8') as f:
        f.write('package com.ricordella.app.core.i18n\n\n')
        f.write(f'/*\n * Traduzioni {adjective}: chiave = testo italiano come scritto nel codice.\n')
        f.write(f' * FILE GENERATO da tools/i18n/generate.py a partire da tools/i18n/{code}.txt: non modificarlo a mano.\n */\n')
        f.write(f'internal val {name}: Map<String, String> = hashMapOf(\n')
        f.write('\n'.join(entries))
        f.write('\n)\n')
    missing = sorted(k for k in used if k not in pairs and re.search(r'[A-Za-zÀ-ÿ]{2}', k))
    if code == 'en':
        english = set(pairs)
    else:
        # Anche i testi passati a tr() tramite variabile (es. etichette di enum): sono nel file inglese.
        missing = sorted(set(missing) | {k for k in english if k not in pairs and k in used_or_literal})
    unused = sorted(k for k in pairs if k not in used)
    print(f'{code}: {len(entries)} traduzioni scritte; mancanti: {len(missing)}; non più usate: {len(unused)}')
    if code in show:
        for k in missing:
            print('  MISSING:', repr(k))
