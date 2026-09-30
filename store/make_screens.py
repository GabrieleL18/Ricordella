"""Impagina le schermate per il Play Store: titolo in alto, telefono con la schermata, cielo magico.

Metti le schermate del telefono in store/screenshots/ con i nomi elencati in SCREENS (anche .jpg) e lancia:

    python store/make_screens.py

Le immagini finite (1080x1920, formato accettato da Google Play) finiscono in store/output/.
Serve Microsoft Edge o Google Chrome installato: disegna l'HTML e ne salva la schermata.
"""
import subprocess
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FONTS = ROOT.parent / "app" / "src" / "main" / "res" / "font"
OUT = ROOT / "output"
BROWSERS = [
    Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
]

# Titolo, sottotitolo, colori (cielo alto, cielo basso, accento) e schermate di ogni immagine.
# Le schermate sono i nomi dei file in store/screenshots/ (senza estensione): due nomi = due telefoni affiancati.
# Si ottengono dalla modalità demo (Impostazioni > Sviluppatore > Modalità demo per gli screenshot).
SCREENS = [
    ("La tua giornata,<br>sotto controllo", "Il saluto giusto, l'ora e tutto quello che c'è da fare oggi", "#3B1F73", "#2E7FBF", "#F2D43D", ["home"]),
    ("Tutto il mese<br>a portata di dito", "Sfoglia giorni e mesi con uno swipe, viaggi e feste compresi", "#1F4E8C", "#5FC3C9", "#F2D43D", ["calendar"]),
    ("Ogni promemoria<br>al suo posto", "Oggi, in arrivo o scaduti: li trovi in un tocco", "#2A1450", "#7A3FB8", "#FF9E7A", ["reminders"]),
    ("Il viaggio,<br>tutto in un posto", "Voli, hotel e prenotazioni. E un tocco per arrivarci", "#0F5F73", "#3FA9E0", "#F2D43D", ["trip"]),
    ("Auto, casa<br>e documenti", "Bollo, tagliando e garanzie: ti avviso io", "#6B2E1F", "#E0873A", "#F2D43D", ["items", "car"]),
    ("La tua cerchia<br>magica", "Ogni persona ha il suo maghetto e i suoi promemoria", "#0F5F73", "#58C9A8", "#F2D43D", ["people", "person"]),
    ("Bevi, e il mago<br>si riempie", "L'acqua di oggi in pozioni. Se esageri, gli si gonfia la pancia", "#0B3D6B", "#3FA9E0", "#F2D43D", ["potions", "potions-full"]),
    ("Sveglie<br>incantate", "Di giorno o di notte, il maghetto ti sveglia col sorriso", "#1A1446", "#E0873A", "#F2D43D", ["alarm-day", "alarm-night"]),
]

# Parte alta delle schermate da tagliare (la barra di stato di Android), in proporzione alla larghezza.
STATUS_BAR = 0.135

PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Fredoka; src: url('{fonts}/fredoka.ttf'); }}
@font-face {{ font-family: Jakarta; src: url('{fonts}/plus_jakarta_sans.ttf'); }}
html, body {{ margin: 0; width: 1080px; height: 1920px; overflow: hidden; }}
body {{ background: linear-gradient(160deg, {top}, {bottom}); font-family: Jakarta, sans-serif; color: white; position: relative; }}
.prop {{ position: absolute; z-index: 0; filter: drop-shadow(0 18px 24px rgba(0,0,0,.35)); }}
.prop svg {{ width: 100%; height: 100%; overflow: visible; display: block; }}
.star {{ position: absolute; background: white; clip-path: polygon(50% 0, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0 50%, 38% 38%); }}
h1, p, .brand, .phone {{ z-index: 1; }}
h1, p {{ position: relative; text-wrap: balance; }}
h1 {{ font-family: Fredoka, sans-serif; font-weight: 600; font-size: 92px; line-height: 1.05; margin: 0; padding: 120px 80px 0; }}
h1 span {{ color: {accent}; }}
p {{ font-size: 40px; margin: 28px 80px 0; opacity: .9; line-height: 1.3; }}
.phone {{ position: absolute; left: 50%; top: 640px; width: 700px; height: 1480px; transform: translateX(-50%) rotate(-2deg);
         background: #111319; border-radius: 76px; padding: 22px; box-sizing: border-box;
         box-shadow: 0 40px 90px rgba(0,0,0,.45), 0 0 0 6px rgba(255,255,255,.12); }}
.phone.duo {{ width: 520px; height: 1100px; top: 700px; border-radius: 60px; padding: 16px; }}
.phone.duo .screen {{ border-radius: 46px; }}
.phone.left {{ left: 300px; transform: translateX(-50%) rotate(-5deg); }}
.phone.right {{ left: 770px; top: 780px; transform: translateX(-50%) rotate(4deg); }}
.screen {{ width: 100%; height: 100%; border-radius: 56px; overflow: hidden; background: #111319; }}
.screen img {{ width: 100%; height: auto; display: block; margin-top: -{crop}%; }}
.missing {{ height: 100%; display: flex; align-items: center; justify-content: center; color: #7A7766; font-size: 44px; border: 6px dashed #D6D0BA; border-radius: 56px; box-sizing: border-box; }}
.brand {{ position: absolute; right: 70px; top: 60px; font-family: Fredoka, sans-serif; font-size: 40px;
          background: linear-gradient(90deg, #FFC400, #FF7A00); -webkit-background-clip: text; color: transparent; }}
</style></head><body>
{stars}
{props}
<div class="brand">Remindella</div>
<h1>{title}</h1>
<p>{subtitle}</p>
{phones}
</body></html>"""


def stars(seed):
    import random
    rnd = random.Random(seed)
    items = []
    while len(items) < 26:
        size = rnd.randint(10, 34)
        x, y = rnd.randint(0, 1060), rnd.randint(0, 1900)
        # Niente stelle sopra titolo e sottotitolo: disturberebbero la lettura.
        if x < 960 and y < 470:
            continue
        items.append(
            f'<div class="star" style="left:{x}px;top:{y}px;'
            f'width:{size}px;height:{size}px;opacity:{rnd.uniform(0.25, 0.8):.2f}"></div>'
        )
    return "\n".join(items)


# Oggetti magici disegnati nello stesso stile flat dell'app (colori del tema: bolt, lavanda,
# azzurro, corallo, menta). Ogni funzione restituisce l'SVG in un riquadro 100x100.
BOLT, LAVENDER, CYAN, CORAL, MINT, WOOD = "#F2D43D", "#9B6BE6", "#3FA9E0", "#FF7A6B", "#4CC38A", "#8A5A3B"


def star_path(cx, cy, r):
    i = r * 0.32
    return (f"M{cx} {cy - r} L{cx + i} {cy - i} L{cx + r} {cy} L{cx + i} {cy + i} "
            f"L{cx} {cy + r} L{cx - i} {cy + i} L{cx - r} {cy} L{cx - i} {cy - i} Z")


def hat(color=CYAN):
    return (f'<path d="M14 80 Q38 46 70 6 Q56 46 86 80 Z" fill="{color}"/>'
            f'<path d="M70 6 Q60 30 64 50" stroke="white" stroke-opacity=".25" stroke-width="4" fill="none" stroke-linecap="round"/>'
            f'<rect x="4" y="74" width="92" height="14" rx="7" fill="{color}" style="filter:brightness(.8)"/>'
            f'<path d="{star_path(46, 54, 11)}" fill="{BOLT}"/>')


def wand():
    return (f'<line x1="14" y1="88" x2="66" y2="36" stroke="{WOOD}" stroke-width="8" stroke-linecap="round"/>'
            f'<line x1="58" y1="44" x2="66" y2="36" stroke="white" stroke-width="8" stroke-linecap="round"/>'
            f'<path d="{star_path(74, 26, 17)}" fill="{BOLT}"/>'
            f'<path d="{star_path(92, 8, 6)}" fill="white"/>'
            f'<path d="{star_path(94, 40, 5)}" fill="{CORAL}"/>'
            f'<path d="{star_path(56, 10, 5)}" fill="{CYAN}"/>')


def crystal_ball():
    return ('<defs><radialGradient id="glass" cx="38%" cy="32%" r="70%">'
            '<stop offset="0" stop-color="#CFF2FF"/><stop offset=".55" stop-color="#6CC8F0"/><stop offset="1" stop-color="#2E8FD0"/></radialGradient></defs>'
            f'<path d="M22 96 L30 74 H70 L78 96 Z" fill="{LAVENDER}"/>'
            '<circle cx="50" cy="44" r="36" fill="url(#glass)"/>'
            '<path d="M32 30 A22 22 0 0 1 48 18" stroke="white" stroke-width="5" fill="none" stroke-linecap="round" opacity=".85"/>'
            f'<path d="M54 26 L40 48 H50 L44 64 L60 40 H50 Z" fill="{BOLT}"/>')


def spell_book():
    return (f'<path d="M6 30 Q28 22 50 32 Q72 22 94 30 V84 Q72 76 50 86 Q28 76 6 84 Z" fill="{LAVENDER}"/>'
            '<path d="M10 28 Q30 20 50 30 V80 Q30 72 10 80 Z" fill="#FBF6E6"/>'
            '<path d="M90 28 Q70 20 50 30 V80 Q70 72 90 80 Z" fill="#F1E8CF"/>'
            '<path d="M18 40 Q30 36 42 42 M18 50 Q30 46 42 52 M18 60 Q30 56 38 60" stroke="#C9B98E" stroke-width="3" fill="none" stroke-linecap="round"/>'
            f'<path d="{star_path(70, 52, 12)}" fill="{BOLT}"/>'
            f'<path d="{star_path(62, 6, 6)}" fill="{BOLT}"/>'
            f'<path d="{star_path(82, 14, 4)}" fill="white"/>')


def moon():
    return (f'<path d="M58 6 A44 44 0 1 0 94 70 A36 36 0 1 1 58 6 Z" fill="{BOLT}"/>'
            f'<path d="{star_path(84, 20, 7)}" fill="white"/>')


def potion():
    return (f'<rect x="40" y="4" width="20" height="14" rx="4" fill="{WOOD}"/>'
            '<path d="M42 16 H58 V34 Q84 44 84 66 A34 30 0 0 1 16 66 Q16 44 42 34 Z" fill="white" fill-opacity=".25" stroke="white" stroke-width="3"/>'
            f'<path d="M20 64 Q34 56 50 62 Q66 68 80 60 A32 28 0 0 1 20 64 Z" fill="{MINT}"/>'
            '<circle cx="40" cy="72" r="4" fill="white" opacity=".7"/><circle cx="58" cy="78" r="3" fill="white" opacity=".7"/>')


SKIN, BEAR, BEAR_DARK, BEAR_LIGHT, INK = "#FFD9B8", "#8B5A3C", "#6B4129", "#D9A57A", "#2B1B12"


def wizard():
    """Il maghetto della sveglia: tunica lavanda, cappello azzurro, barba bianca e bacchetta che fa scintille."""
    return (f'<ellipse cx="48" cy="93" rx="28" ry="4" fill="black" opacity=".25"/>'
            f'<path d="M22 92 L48 40 L74 92 Z" fill="{LAVENDER}"/>'
            f'<rect x="20" y="88" width="56" height="6" rx="2" fill="{BOLT}"/>'
            f'<line x1="58" y1="60" x2="78" y2="50" stroke="{LAVENDER}" stroke-width="7" stroke-linecap="round"/>'
            f'<line x1="80" y1="49" x2="93" y2="33" stroke="{WOOD}" stroke-width="3" stroke-linecap="round"/>'
            f'<circle cx="79" cy="49" r="4" fill="{SKIN}"/>'
            f'<path d="{star_path(95, 29, 7)}" fill="{BOLT}"/>'
            f'<path d="{star_path(86, 20, 3)}" fill="white"/><path d="{star_path(99, 42, 2.5)}" fill="{CORAL}"/>'
            f'<circle cx="48" cy="37" r="10" fill="{SKIN}"/>'
            '<circle cx="41" cy="41" r="2" fill="#FF9EAE"/><circle cx="55" cy="41" r="2" fill="#FF9EAE"/>'
            f'<path d="M42 36 q2.5 -2.5 5 0 M49 36 q2.5 -2.5 5 0" stroke="{INK}" stroke-width="1.4" fill="none" stroke-linecap="round"/>'
            '<path d="M38 42 Q48 64 58 42 Q54 47 48 46 Q42 47 38 42 Z" fill="white"/>'
            '<circle cx="40" cy="45" r="4" fill="white"/><circle cx="56" cy="45" r="4" fill="white"/><circle cx="48" cy="52" r="6" fill="#ECE8F4"/>'
            f'<path d="M36 28 L60 28 L56 2 Q48 18 36 28 Z" fill="{CYAN}"/>'
            f'<rect x="32" y="26" width="32" height="4" rx="2" fill="#6A56D8"/>'
            f'<path d="{star_path(50, 20, 4)}" fill="{BOLT}"/>')


def sleeping_bear():
    """L'orso che dorme della sveglia di giorno, sdraiato con la testa a sinistra e le zeta che salgono."""
    return (f'<ellipse cx="55" cy="90" rx="42" ry="4" fill="black" opacity=".25"/>'
            f'<ellipse cx="60" cy="74" rx="34" ry="16" fill="{BEAR}"/>'
            f'<circle cx="88" cy="81" r="8" fill="{BEAR_DARK}"/>'
            f'<circle cx="17" cy="59" r="5" fill="{BEAR}"/><circle cx="17" cy="59" r="2.5" fill="{BEAR_LIGHT}"/>'
            f'<circle cx="35" cy="58" r="5" fill="{BEAR}"/><circle cx="35" cy="58" r="2.5" fill="{BEAR_LIGHT}"/>'
            f'<circle cx="26" cy="72" r="15" fill="{BEAR}"/>'
            f'<path d="M17 69 q3 3 6 0 M29 69 q3 3 6 0" stroke="{INK}" stroke-width="1.6" fill="none" stroke-linecap="round"/>'
            f'<ellipse cx="26" cy="79" rx="8" ry="6" fill="{BEAR_LIGHT}"/><ellipse cx="26" cy="76.5" rx="3" ry="2" fill="{INK}"/>'
            f'<ellipse cx="12" cy="86" rx="7" ry="4.5" fill="{BEAR_LIGHT}"/>'
            '<g font-family="Fredoka, sans-serif" font-weight="600" fill="white">'
            '<text x="42" y="50" font-size="9" opacity=".6">z</text><text x="50" y="38" font-size="12" opacity=".8">z</text>'
            '<text x="60" y="24" font-size="16">z</text></g>')


def prop(svg, left, top, size, rotate=0, flip=False):
    scale = " scaleX(-1)" if flip else ""
    return (f'<div class="prop" style="left:{left}px;top:{top}px;width:{size}px;height:{size}px;transform:rotate({rotate}deg){scale}">'
            f'<svg viewBox="0 0 100 100">{svg}</svg></div>')


# Per ogni immagine una combinazione diversa, negli spazi liberi e in parte dietro il telefono.
PROPS = {
    1: [prop(hat(), 830, 440, 210, 14), prop(wizard(), -70, 1500, 320, -4), prop(wand(), -20, 820, 190, -10)],
    2: [prop(moon(), 70, 470, 150, -12), prop(spell_book(), 850, 1560, 250, 10), prop(wand(), 890, 800, 180, 80)],
    3: [prop(hat(LAVENDER), 830, 450, 200, 12), prop(sleeping_bear(), 40, 390, 330, 0), prop(wand(), -20, 800, 190, -10)],
    4: [prop(wizard(), 800, 1460, 320, 4, flip=True), prop(moon(), 880, 430, 150, 10), prop(hat(CORAL), -40, 1180, 220, -16)],
    5: [prop(spell_book(), 810, 430, 230, -10), prop(crystal_ball(), -40, 1540, 280, -8), prop(potion(), 880, 1180, 200, 12)],
    6: [prop(hat(MINT), 840, 440, 200, 12), prop(sleeping_bear(), 250, 420, 300, 0)],
    7: [prop(potion(), 850, 420, 190, 12), prop(wand(), -30, 1640, 200, -10)],
    8: [prop(moon(), 860, 420, 170, 10), prop(wand(), -30, 1640, 200, -10)],
}


def screenshot(name):
    for ext in ("png", "jpg", "jpeg", "webp"):
        path = ROOT / "screenshots" / f"{name}.{ext}"
        if path.exists():
            return f'<img src="{path.as_uri()}">'
    return f'<div class="missing">{name}</div>'


def phones(names):
    if len(names) == 1:
        return f'<div class="phone"><div class="screen">{screenshot(names[0])}</div></div>'
    return "\n".join(
        f'<div class="phone duo {side}"><div class="screen">{screenshot(name)}</div></div>' for side, name in zip(("left", "right"), names)
    )


def main():
    browser = next((b for b in BROWSERS if b.exists()), None)
    if browser is None:
        raise SystemExit("Serve Microsoft Edge o Google Chrome per creare le immagini.")
    OUT.mkdir(exist_ok=True)
    for n, (title, subtitle, top, bottom, accent, names) in enumerate(SCREENS, start=1):
        # La prima riga del titolo resta bianca, la seconda prende il colore d'accento.
        first, _, second = title.partition("<br>")
        html = PAGE.format(
            fonts=FONTS.as_uri(), crop=f"{STATUS_BAR * 100:.1f}", top=top, bottom=bottom, accent=accent, stars=stars(n),
            props="\n".join(PROPS.get(n, [])),
            title=f"{first}<br><span>{second}</span>", subtitle=subtitle, phones=phones(names),
        )
        page = OUT / f"_{n}.html"
        page.write_text(html, encoding="utf-8")
        target = OUT / f"remindella-{n}.png"
        target.unlink(missing_ok=True)
        # Profilo separato per ogni immagine: se il browser è già aperto, senza questo passerebbe
        # il comando alla finestra esistente e non salverebbe nulla.
        profile = tempfile.mkdtemp(prefix="remindella-store-")
        subprocess.run(
            [str(browser), "--headless=new", "--disable-gpu", f"--user-data-dir={profile}", "--hide-scrollbars", "--allow-file-access-from-files",
             "--force-device-scale-factor=1", "--window-size=1080,1920", f"--screenshot={target}", page.as_uri()],
            check=True, capture_output=True,
        )
        # Il browser risponde subito e salva la schermata poco dopo: si aspetta il file.
        for _ in range(120):
            if target.exists() and target.stat().st_size > 0:
                break
            time.sleep(0.25)
        else:
            raise SystemExit(f"Il browser non ha creato {target.name}")
        time.sleep(0.5)
        page.unlink()
        print("creata", target.relative_to(ROOT.parent))


if __name__ == "__main__":
    main()
