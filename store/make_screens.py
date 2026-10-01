"""Impagina le schermate per il Play Store: titolo in alto, telefono con la schermata, cielo magico.

Metti le schermate del telefono in store/screenshots/<lingua>/ (it, en, de, fr, es) con i nomi elencati
in LAYOUT (anche .jpg) e lancia:

    python store/make_screens.py          # tutte le lingue
    python store/make_screens.py de       # solo tedesco

Le immagini finite (1080x1920, formato accettato da Google Play) finiscono in store/output/<lingua>/.
Serve Microsoft Edge o Google Chrome installato: disegna l'HTML e ne salva la schermata.
"""
import subprocess
import sys
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

# Colori (cielo alto, cielo basso, accento) e schermate di ogni immagine: i nomi dei file in
# store/screenshots/<lingua>/ (senza estensione); due nomi = due telefoni affiancati.
# Si ottengono dalla modalità demo (Impostazioni > Sviluppatore > Modalità demo per gli screenshot).
LAYOUT = [
    ("#3B1F73", "#2E7FBF", "#F2D43D", ["home"]),
    ("#1A1446", "#7A3FB8", "#F2D43D", ["quick", "voice"]),
    ("#1F4E8C", "#5FC3C9", "#F2D43D", ["calendar-day"]),
    ("#0F5F73", "#3FA9E0", "#F2D43D", ["trip"]),
    ("#6B2E1F", "#E0873A", "#F2D43D", ["items", "car"]),
    ("#0E4D3A", "#3FB88A", "#F2D43D", ["expenses"]),
    ("#2A1450", "#2E7FBF", "#FF9E7A", ["sharing", "backup"]),
    ("#0F5F73", "#58C9A8", "#F2D43D", ["people", "person"]),
]

# Titolo (con <br>: la seconda riga prende il colore d'accento) e sottotitolo, per lingua.
TEXTS = {
    "it": [
        ("La tua giornata,<br>sotto controllo", "Il saluto giusto, l'ora e tutto quello che c'è da fare oggi"),
        ("Scrivilo o<br>dillo a voce", "«Cena con Luca sabato alle 20» e ci penso io: giorno, ora e ripetizioni"),
        ("Tutto il mese<br>a portata di dito", "Tocca un giorno e vedi i suoi impegni, viaggi e feste compresi"),
        ("Il viaggio,<br>tutto in un posto", "Voli, hotel e prenotazioni. E un tocco per arrivarci"),
        ("Auto, casa<br>e documenti", "Bollo, tagliando e garanzie: ti avviso io"),
        ("Le spese,<br>finalmente chiare", "Mese per mese, per tipo e per cosa, con il confronto con l'anno prima"),
        ("I tuoi dati,<br>dove vuoi tu", "Scegli dove salvare il backup, condividilo e sincronizzati con chi vuoi"),
        ("La tua cerchia<br>magica", "Ogni persona ha il suo maghetto e i suoi promemoria"),
    ],
    "en": [
        ("Your day,<br>under control", "The right greeting, the time and everything you need to do today"),
        ("Type it or<br>just say it", "\"Dinner with Luca Saturday at 8pm\" and I take care of it: day, time and repeats"),
        ("The whole month<br>at your fingertips", "Tap a day to see its plans, trips and holidays included"),
        ("Your trip,<br>all in one place", "Flights, hotels and bookings. And one tap to get there"),
        ("Car, home<br>and documents", "Road tax, services and warranties: I'll remind you"),
        ("Expenses,<br>finally clear", "Month by month, by type and by item, compared with last year"),
        ("Your data,<br>where you want it", "Choose where to save your backup, share it and sync with whoever you like"),
        ("Your magical<br>circle", "Everyone gets their own little wizard and reminders"),
    ],
    "de": [
        ("Dein Tag,<br>im Griff", "Der passende Gruß, die Uhrzeit und alles, was heute ansteht"),
        ("Tippen oder<br>einfach sagen", "„Essen mit Luca Samstag um 20“ und ich kümmere mich: Tag, Uhrzeit, Wiederholung"),
        ("Der ganze Monat<br>im Blick", "Tippe auf einen Tag und sieh seine Termine, Reisen und Feiertage"),
        ("Die Reise,<br>alles an einem Ort", "Flüge, Hotels und Buchungen. Und ein Tippen bis zum Ziel"),
        ("Auto, Zuhause<br>und Dokumente", "Kfz-Steuer, Inspektion und Garantien: Ich erinnere dich"),
        ("Ausgaben,<br>endlich klar", "Monat für Monat, nach Typ und Ding, mit Vorjahresvergleich"),
        ("Deine Daten,<br>wo du willst", "Wähle, wo das Backup liegt, teile es und synchronisiere mit wem du willst"),
        ("Dein magischer<br>Kreis", "Jede Person hat ihren kleinen Zauberer und ihre Erinnerungen"),
    ],
    "fr": [
        ("Ta journée,<br>sous contrôle", "Le bon salut, l'heure et tout ce qu'il y a à faire aujourd'hui"),
        ("Écris-le ou<br>dis-le", "« Dîner avec Luca samedi à 20h » et je m'en occupe : jour, heure et répétitions"),
        ("Tout le mois<br>sous les doigts", "Touche un jour et vois ses rendez-vous, voyages et fêtes compris"),
        ("Le voyage,<br>tout au même endroit", "Vols, hôtels et réservations. Et un geste pour y aller"),
        ("Voiture, maison<br>et documents", "Taxe, révision et garanties : je te préviens"),
        ("Les dépenses,<br>enfin claires", "Mois par mois, par type et par objet, comparées à l'an dernier"),
        ("Tes données,<br>où tu veux", "Choisis où enregistrer la sauvegarde, partage-la et synchronise-toi avec qui tu veux"),
        ("Ton cercle<br>magique", "Chaque personne a son petit magicien et ses rappels"),
    ],
    "es": [
        ("Tu día,<br>bajo control", "El saludo justo, la hora y todo lo que hay que hacer hoy"),
        ("Escríbelo o<br>dilo en voz alta", "«Cena con Luca el sábado a las 20» y yo me encargo: día, hora y repeticiones"),
        ("Todo el mes<br>a mano", "Toca un día y ve sus planes, viajes y festivos incluidos"),
        ("El viaje,<br>todo en un sitio", "Vuelos, hoteles y reservas. Y un toque para llegar"),
        ("Coche, casa<br>y documentos", "Impuesto, revisión y garantías: yo te aviso"),
        ("Los gastos,<br>por fin claros", "Mes a mes, por tipo y por cosa, comparados con el año anterior"),
        ("Tus datos,<br>donde quieras", "Elige dónde guardar la copia, compártela y sincronízate con quien quieras"),
        ("Tu círculo<br>mágico", "Cada persona tiene su pequeño mago y sus recordatorios"),
    ],
}

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
.brand {{ position: absolute; right: 60px; top: 44px; }}
{title_css}
</style></head><body>
{stars}
{props}
<div class="brand">{brand}</div>
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


# Il nome dell'app come nella grafica in primo piano (anche in make_logo.py).
TITLE_CSS = """.title { font-family: Fredoka, sans-serif; font-weight: 700; line-height: 1.1; white-space: nowrap; filter: drop-shadow(0 .07em 0 #1E0E40) drop-shadow(0 0 .22em rgba(255,196,0,.45)); }
.letter { display: inline-block; position: relative; }
.letter .edge { position: absolute; left: 0; top: 0; color: #3B1F73; -webkit-text-stroke: 0.14em #3B1F73; }
.letter .fill { position: relative; background: linear-gradient(#FFF6B0, #FFD23D 45%, #FF8A00); -webkit-background-clip: text; background-clip: text; color: transparent; }
"""


def brand_title(size):
    """ "Remindella" a lettere che ballano, dorate col bordo viola, con una stella al posto del puntino della i e una scia magica sotto."""
    letters = []
    for n, ch in enumerate("Remindella"):
        lift = (-1) ** n * size * 0.05
        tilt = (-1) ** (n + 1) * 5
        star = ""
        if ch == "i":
            ch = "ı"  # i senza puntino: il puntino lo fa la stella.
            star = (f'<svg viewBox="0 0 20 20" style="position:absolute;left:50%;top:{size * 0.12}px;width:{size * 0.3}px;transform:translateX(-50%) rotate(15deg);'
                    f'overflow:visible;filter:drop-shadow(0 0 {size * 0.08}px #FFE066)"><path d="{star_path(10, 10, 10)}" fill="white"/></svg>')
        letters.append(f'<span class="letter" style="transform:translateY({lift}px) rotate({tilt}deg)"><span class="edge">{ch}</span><span class="fill">{ch}</span>{star}</span>')
    trail = (f'<svg viewBox="0 0 400 40" style="display:block;width:{size * 5.4}px;margin-top:{-size * 0.05}px;overflow:visible">'
             '<path d="M6 22 Q120 44 250 24 T392 10" stroke="url(#trail)" stroke-width="5" fill="none" stroke-linecap="round"/>'
             '<defs><linearGradient id="trail"><stop offset="0" stop-color="#FFE066" stop-opacity="0"/><stop offset="1" stop-color="#FFE066"/></linearGradient></defs>'
             f'<path d="{star_path(392, 10, 13)}" fill="{BOLT}"/><path d="{star_path(300, 36, 5)}" fill="white" opacity=".8"/>'
             f'<path d="{star_path(200, 8, 4)}" fill="white" opacity=".6"/></svg>')
    return f'<div class="title" style="font-size:{size}px">{"".join(letters)}</div>{trail}'


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


def crystal_ball(stand=True):
    """Palla di vetro con la saetta; senza [stand] fluttua da sola."""
    return ('<defs><radialGradient id="glass" cx="38%" cy="32%" r="70%">'
            '<stop offset="0" stop-color="#CFF2FF"/><stop offset=".55" stop-color="#6CC8F0"/><stop offset="1" stop-color="#2E8FD0"/></radialGradient></defs>'
            + (f'<path d="M22 96 L30 74 H70 L78 96 Z" fill="{LAVENDER}"/>' if stand else '')
            + '<circle cx="50" cy="44" r="36" fill="url(#glass)"/>'
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


def wizard(amazed=False, arm=True):
    """Il maghetto dell'app, ricalcato da drawWizard in Wizard.kt (riquadro 100x100, colori del tema scuro).

    [arm]: False toglie braccio e bacchetta (per i primi piani).
    [amazed]: occhi spalancati che guardano a destra, sopracciglia alzate, bocca a "o" e braccio alzato per lo stupore.
    """
    lav, cyan, stand, ink, beard, shade = "#B98CF0", "#5BC0F5", "#7E62E6", "#2B2140", "#FFFFFF", "#E6E4EF"
    skin = "#FFD7B5"
    puffs = [(37.6, 45.4, beard), (46, 48.4, beard), (54.4, 45.4, beard), (41.8, 51.4, shade), (50.2, 51.4, shade), (46, 55.6, shade)]

    def eye(x):
        return f'M{x - 2.255:.3f} 38.623 A2.4 1.92 0 0 1 {x + 2.255:.3f} 38.623'
    return (
        '<ellipse cx="46" cy="93.5" rx="26" ry="3.5" fill="black" opacity=".25"/>'
        f'<path d="M34 50 L58 50 L72 93 L20 93 Z" fill="{lav}"/>'
        f'<rect x="20" y="88" width="52" height="5" fill="{BOLT}"/>'
        # Braccio con la bacchetta, inclinato come nell'app.
        + (f'<g transform="rotate({-70 if amazed else -35} 56 58)">'
        f'<line x1="56" y1="58" x2="76" y2="58" stroke="{lav}" stroke-width="7" stroke-linecap="round"/>'
        f'<line x1="78" y1="58" x2="98" y2="56" stroke="{WOOD}" stroke-width="2.5" stroke-linecap="round"/>'
        f'<circle cx="78" cy="58" r="4" fill="{skin}"/>'
        f'<path d="{star_path(98, 56, 6.5)}" fill="{BOLT}"/>'
        f'<path d="{star_path(101, 51.3, 2.3)}" fill="{BOLT}"/><path d="{star_path(105, 47.9, 1.6)}" fill="{CYAN}"/>'
        f'<path d="{star_path(103.5, 60, 1.4)}" fill="{CORAL}"/>'
        '</g>' if arm else '')
        # Testa: viso, barba a nuvola, occhi chiusi felici, guance, sorriso.
        + f'<circle cx="46" cy="40" r="12" fill="{skin}"/>'
        + "".join(f'<circle cx="{x}" cy="{y}" r="6.6" fill="{c}"/>' for x, y, c in puffs)
        + (
            # Stupito: occhi tondi che guardano la palla, sopracciglia alte e bocca a "o".
            '<ellipse cx="40.96" cy="38" rx="2.6" ry="3" fill="white"/><ellipse cx="51.04" cy="38" rx="2.6" ry="3" fill="white"/>'
            f'<circle cx="42" cy="38.4" r="1.5" fill="{ink}"/><circle cx="52.1" cy="38.4" r="1.5" fill="{ink}"/>'
            f'<path d="M38.4 33.6 Q41 32 43.4 33.4 M48.6 33.4 Q51 32 53.6 33.6" stroke="{ink}" stroke-width="1.1" fill="none" stroke-linecap="round"/>'
            '<circle cx="38.2" cy="43" r="1.92" fill="#FF9E9E" opacity=".7"/><circle cx="53.8" cy="43" r="1.92" fill="#FF9E9E" opacity=".7"/>'
            f'<ellipse cx="46.5" cy="45.6" rx="1.7" ry="2.3" fill="{ink}"/>'
            if amazed else
            f'<path d="{eye(40.96)} {eye(51.04)}" stroke="{ink}" stroke-width="1.44" fill="none" stroke-linecap="round"/>'
            '<circle cx="38.56" cy="42.64" r="1.92" fill="#FF9E9E" opacity=".7"/><circle cx="53.44" cy="42.64" r="1.92" fill="#FF9E9E" opacity=".7"/>'
            f'<path d="M49.157 43.821 A3.36 2.4 0 0 1 42.843 43.821" stroke="{ink}" stroke-width="1.2" fill="none" stroke-linecap="round"/>'
        )
        # Cappello a punta un po' storto, falda viola e stella.
        + f'<path d="M33.4 31.6 Q43.6 17.2 56.8 2.8 Q50.8 19.6 58.6 31.6 Z" fill="{cyan}"/>'
        f'<rect x="29.8" y="30.16" width="32.4" height="3.6" rx="1.8" fill="{stand}"/>'
        f'<path d="{star_path(46.6, 22, 3.7)}" fill="{BOLT}"/>'
    )


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


def amazed_bear():
    """L'orso sveglio, seduto, con le zampe sulle guance e gli occhi spalancati verso sinistra."""
    return (f'<ellipse cx="50" cy="95" rx="30" ry="4" fill="black" opacity=".25"/>'
            f'<ellipse cx="50" cy="72" rx="28" ry="24" fill="{BEAR}"/>'
            f'<ellipse cx="50" cy="76" rx="17" ry="16" fill="{BEAR_LIGHT}"/>'
            f'<ellipse cx="34" cy="92" rx="10" ry="5.5" fill="{BEAR_DARK}"/><ellipse cx="66" cy="92" rx="10" ry="5.5" fill="{BEAR_DARK}"/>'
            f'<circle cx="34" cy="22" r="7" fill="{BEAR}"/><circle cx="34" cy="22" r="3.5" fill="{BEAR_LIGHT}"/>'
            f'<circle cx="66" cy="22" r="7" fill="{BEAR}"/><circle cx="66" cy="22" r="3.5" fill="{BEAR_LIGHT}"/>'
            f'<circle cx="50" cy="38" r="20" fill="{BEAR}"/>'
            '<circle cx="42" cy="34" r="5" fill="white"/><circle cx="58" cy="34" r="5" fill="white"/>'
            f'<circle cx="40" cy="34.6" r="2.5" fill="{INK}"/><circle cx="56" cy="34.6" r="2.5" fill="{INK}"/>'
            f'<path d="M37 26 Q41.5 23 46 25.5 M54 25.5 Q58.5 23 63 26" stroke="{INK}" stroke-width="1.6" fill="none" stroke-linecap="round"/>'
            f'<ellipse cx="50" cy="47" rx="9" ry="7.5" fill="{BEAR_LIGHT}"/><ellipse cx="50" cy="42.5" rx="3" ry="2" fill="{INK}"/>'
            f'<ellipse cx="50" cy="49.5" rx="2.6" ry="3.2" fill="{INK}"/>'
            f'<circle cx="31" cy="47" r="6.5" fill="{BEAR}"/><circle cx="69" cy="47" r="6.5" fill="{BEAR}"/>'
            f'<circle cx="31" cy="48" r="3" fill="{BEAR_LIGHT}"/><circle cx="69" cy="48" r="3" fill="{BEAR_LIGHT}"/>')


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


LANG = "it"


def screenshot(name):
    for ext in ("png", "jpg", "jpeg", "webp"):
        path = ROOT / "screenshots" / LANG / f"{name}.{ext}"
        if path.exists():
            return f'<img src="{path.as_uri()}">'
    return f'<div class="missing">{name}</div>'


def phones(names):
    if len(names) == 1:
        return f'<div class="phone"><div class="screen">{screenshot(names[0])}</div></div>'
    return "\n".join(
        f'<div class="phone duo {side}"><div class="screen">{screenshot(name)}</div></div>' for side, name in zip(("left", "right"), names)
    )


def render(html, target, width, height):
    """Disegna [html] con Edge/Chrome senza finestra e ne salva la schermata in [target]."""
    browser = next((b for b in BROWSERS if b.exists()), None)
    if browser is None:
        raise SystemExit("Serve Microsoft Edge o Google Chrome per creare le immagini.")
    target.parent.mkdir(parents=True, exist_ok=True)
    page = target.with_name(f"_{target.stem}.html")
    page.write_text(html, encoding="utf-8")
    target.unlink(missing_ok=True)
    # Profilo separato per ogni immagine: se il browser è già aperto, senza questo passerebbe
    # il comando alla finestra esistente e non salverebbe nulla.
    profile = tempfile.mkdtemp(prefix="remindella-store-")
    subprocess.run(
        [str(browser), "--headless=new", "--disable-gpu", f"--user-data-dir={profile}", "--hide-scrollbars", "--allow-file-access-from-files",
         "--force-device-scale-factor=1", f"--window-size={width},{height}", f"--screenshot={target}", page.as_uri()],
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
    print("creata", target.name)


def main():
    global LANG
    for LANG in (sys.argv[1:] or list(TEXTS)):
        for n, ((top, bottom, accent, names), (title, subtitle)) in enumerate(zip(LAYOUT, TEXTS[LANG]), start=1):
            # La prima riga del titolo resta bianca, la seconda prende il colore d'accento.
            first, _, second = title.partition("<br>")
            html = PAGE.format(
                fonts=FONTS.as_uri(), title_css=TITLE_CSS, brand=brand_title(46), crop=f"{STATUS_BAR * 100:.1f}", top=top, bottom=bottom, accent=accent, stars=stars(n),
                props="\n".join(PROPS.get(n, [])),
                title=f"{first}<br><span>{second}</span>", subtitle=subtitle, phones=phones(names),
            )
            render(html, OUT / LANG / f"remindella-{n}.png", 1080, 1920)


if __name__ == "__main__":
    main()
