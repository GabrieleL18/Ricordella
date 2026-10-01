"""Grafiche per Instagram: una storia in italiano (accesso anticipato) e tre post in inglese.

    python store/make_social.py

Riusa mascotte, colori e render di make_screens.py. Output in store/output/social/.
Storia 1080x1920, post 1080x1350 (4:5).
"""
import random

import make_screens as ms

OUT = ms.OUT / "social"
EMAIL = "lannilab.support@gmail.com"

PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Fredoka; src: url('{fonts}/fredoka.ttf'); }}
@font-face {{ font-family: Jakarta; src: url('{fonts}/plus_jakarta_sans.ttf'); }}
html, body {{ margin: 0; width: 1080px; height: {h}px; overflow: hidden; }}
body {{ background: linear-gradient(160deg, {top}, {bottom}); font-family: Jakarta, sans-serif; color: white; position: relative; }}
.prop {{ position: absolute; filter: drop-shadow(0 18px 24px rgba(0,0,0,.35)); }}
.prop svg {{ width: 100%; height: 100%; overflow: visible; display: block; }}
.star {{ position: absolute; background: white; clip-path: polygon(50% 0, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0 50%, 38% 38%); }}
.abs {{ position: absolute; left: 0; right: 0; text-align: center; }}
h1 {{ font-family: Fredoka, sans-serif; font-weight: 600; font-size: {hs}px; line-height: 1.05; margin: 0; text-wrap: balance; }}
h1 span {{ color: {accent}; }}
.sub {{ font-size: 38px; line-height: 1.3; opacity: .92; margin: 0 90px; text-wrap: balance; }}
.pill {{ display: inline-block; background: {accent}; color: #2B1B12; font-weight: 800; font-size: 34px; letter-spacing: .06em;
        padding: 14px 34px; border-radius: 999px; box-shadow: 0 8px 0 rgba(0,0,0,.22); }}
.card {{ display: flex; align-items: center; gap: 26px; background: rgba(14,16,26,.88); border-radius: 38px; padding: 26px 34px;
        box-shadow: 0 18px 40px rgba(0,0,0,.35); text-align: left; }}
.ico {{ flex: none; width: 92px; height: 92px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 48px; }}
.card b {{ display: block; font-size: 40px; line-height: 1.15; }}
.card small {{ display: block; font-size: 29px; opacity: .72; margin-top: 4px; }}
.tag {{ margin-left: auto; flex: none; font-weight: 800; font-size: 28px; padding: 10px 22px; border-radius: 22px; }}
.chip {{ display: flex; align-items: center; gap: 16px; background: rgba(14,16,26,.78); border-radius: 30px; padding: 16px 22px; font-weight: 700; font-size: 31px; line-height: 1.15; text-align: left; }}
.chip .ico {{ width: 66px; height: 66px; font-size: 34px; }}
.tile {{ background: rgba(14,16,26,.82); border-radius: 44px; padding: 20px 40px; display: flex; align-items: center; gap: 30px; text-align: left; box-shadow: 0 18px 40px rgba(0,0,0,.3); }}
.tile b {{ display: block; font-family: Fredoka, sans-serif; font-weight: 600; font-size: 48px; }}
.tile small {{ display: block; font-size: 31px; opacity: .8; line-height: 1.3; margin-top: 6px; }}
.tile svg {{ flex: none; width: 120px; height: 120px; overflow: visible; }}
.step {{ flex: 1; text-align: center; font-weight: 700; font-size: 31px; line-height: 1.2; }}
.step i {{ display: block; margin: 0 auto 12px; width: 62px; height: 62px; line-height: 62px; border-radius: 50%; background: {accent}; color: #2B1B12; font: 800 36px/62px Fredoka, sans-serif; font-style: normal; }}
.sq .card {{ padding: 18px 28px; gap: 20px; border-radius: 30px; }}
.sq .ico {{ width: 68px; height: 68px; font-size: 36px; }}
.sq .card b {{ font-size: 32px; }} .sq .card small {{ font-size: 23px; }}
.sq .tag {{ font-size: 22px; padding: 7px 16px; border-radius: 16px; }}
.sq .tile {{ padding: 14px 32px; border-radius: 34px; gap: 24px; }}
.sq .tile b {{ font-size: 38px; }} .sq .tile small {{ font-size: 25px; }} .sq .tile svg {{ width: 90px; height: 90px; }}
.sq .sub {{ font-size: 30px; margin: 0 110px; }}
{title_css}
</style></head><body>
{stars}
{props}
{body}
</body></html>"""

# Colori delle icone, dal tema dell'app.
BG = dict(bolt="#F2D43D", cyan="#3FA9E0", coral="#FF7A6B", mint="#4CC38A", lav="#9B6BE6", amber="#E0873A")


def ico(emoji, color):
    return f'<div class="ico" style="background:{color}33">{emoji}</div>'


def stars(seed, h, clear=None):
    rnd, out = random.Random(seed), []
    while len(out) < 24:
        size, x, y = rnd.randint(10, 32), rnd.randint(0, 1060), rnd.randint(0, h - 20)
        if clear and clear[0] < y < clear[1]:
            continue
        out.append(f'<div class="star" style="left:{x}px;top:{y}px;width:{size}px;height:{size}px;opacity:{rnd.uniform(.25, .75):.2f}"></div>')
    return "\n".join(out)


def brand(size, top):
    return f'<div class="abs" style="top:{top}px;display:flex;flex-direction:column;align-items:center;left:0;right:0">{ms.brand_title(size)}</div>'


def page(n, name, h, body, props, top, bottom, accent, hs=100, clear=None):
    html = PAGE.format(fonts=ms.FONTS.as_uri(), title_css=ms.TITLE_CSS, h=h, hs=hs, top=top, bottom=bottom, accent=accent,
                       stars=stars(n, h, clear), props=props, body=body)
    if h == 1080:
        html = html.replace('<body>', '<body class="sq">', 1)
    ms.render(html, OUT / f"{name}.png", 1080, h)


def story():
    feats = [("⚡", "Promemoria e notifiche", BG["bolt"]), ("🗓️", "Calendario e ricorrenze", BG["cyan"]),
             ("✈️", "Viaggi e prenotazioni", BG["mint"]), ("🚗", "Auto, casa e garanzie", BG["amber"]),
             ("🎂", "Persone e compleanni", BG["coral"]), ("💧", "Acqua a pozioni", BG["cyan"]),
             ("⏰", "Sveglie incantate", BG["lav"]), ("🔒", "Offline, senza account", BG["mint"])]
    chips = "".join(f'<div class="chip">{ico(e, c)}<span>{t}</span></div>' for e, t, c in feats)
    steps = "".join(f'<div class="step"><i>{i}</i>{t}</div>' for i, t in
                    enumerate(["Scrivimi<br>in DM", "Installa<br>l'app", "Provala<br>14 giorni"], 1))
    body = (
        brand(54, 215)
        + '<div class="abs" style="top:345px"><span class="pill">ACCESSO ANTICIPATO</span></div>'
        + '<h1 class="abs" style="top:445px;font-size:104px;padding:0 60px;box-sizing:border-box">Testa Remindella<br>per <span>14 giorni</span></h1>'
        + '<p class="abs sub" style="top:690px;left:0;right:0">L\'app che ricorda tutto, con un maghetto dentro. Aiutaci a farla diventare perfetta.</p>'
        + f'<div style="position:absolute;left:60px;right:60px;top:840px;display:grid;grid-template-columns:1fr 1fr;gap:20px">{chips}</div>'
        + f'<div class="card" style="position:absolute;left:60px;right:60px;top:1335px;padding:32px 30px;justify-content:space-between">{steps}</div>'
        + '<div class="abs" style="top:1590px;font-size:34px;font-weight:700">Solo per Android</div>'
        + '<div class="abs" style="top:1645px;font-size:30px;opacity:.9">Scrivimi in DM per entrare 💌</div>'
    )
    props = (ms.prop(ms.hat(), 870, 320, 150, 14) + ms.prop(ms.wand(), 40, 580, 150, -12)
             + ms.prop(ms.wizard(), 20, 1570, 250, -4) + ms.prop(ms.moon(), 70, 330, 110, -12))
    page(1, "story-accesso-anticipato", 1920, body, props, "#3B1F73", "#2E7FBF", "#F2D43D", clear=(200, 1560))


def mock(emoji, color, title, sub, tag, tag_bg, tag_fg="white"):
    return (f'<div class="card">{ico(emoji, color)}<div><b>{title}</b><small>{sub}</small></div>'
            f'<span class="tag" style="background:{tag_bg};color:{tag_fg}">{tag}</span></div>')


def post(n, title, sub, accent, top, bottom, content, props):
    body = (
        brand(34, 26)
        + f'<h1 class="abs" style="top:100px;padding:0 70px;box-sizing:border-box">{title}</h1>'
        + f'<p class="abs sub" style="top:272px">{sub}</p>'
        + content
        + '<div class="abs" style="top:868px"><span class="pill" style="font-size:30px">ONLY ON ANDROID</span></div>'
        + '<div class="abs" style="top:960px;font-size:27px;opacity:.9">Early access: test it for 14 days · DM us to join</div>'
    )
    page(10 + n, f"post-{n}", 1080, body, props, top, bottom, accent, hs=76, clear=(0, 380))


def posts():
    stack = lambda *cards, y=415: f'<div style="position:absolute;left:90px;right:90px;top:{y}px;display:grid;gap:18px">{"".join(cards)}</div>'

    post(1, "Your day,<br><span>under control</span>", "Reminders, events and deadlines in one magical place.",
         "#F2D43D", "#3B1F73", "#2E7FBF",
         stack(mock("💵", BG["mint"], "Pay the electricity bill", "Overdue by 2 days", "2 days ago", "#8E1427"),
               mock("🔧", BG["cyan"], "Car service", "Fiat Panda", "in 6 days", "#0B4D74"),
               mock("🎂", BG["coral"], "Anna's birthday", "Saturday, all day", "in 3 days", "#5B3A9E")),
         ms.prop(ms.hat(), 910, 10, 110, 14))

    post(2, "Cars, trips &<br><span>the people you love</span>", "Warranties, insurance, flights and birthdays, linked to the right person or thing.",
         "#F2D43D", "#0F5F73", "#3FA9E0",
         stack(mock("🚗", BG["amber"], "Car insurance", "Renews in 8 days", "8 days", "#0B4D74"),
               mock("🏠", BG["mint"], "Washing machine warranty", "Expires in 3 months", "active", "#1E6B47"),
               mock("✈️", BG["cyan"], "Rome → Naples", "Sat 09:40 · Cabin 204", "trip", "#5B3A9E")),
         ms.prop(ms.spell_book(), 920, 14, 100, 10))

    def tile(svg, title, text):
        return f'<div class="tile"><svg viewBox="0 0 100 100">{svg}</svg><div><b>{title}</b><small>{text}</small></div></div>'
    post(3, "Magic that<br><span>respects you</span>", "Water in potions, enchanted alarms and total privacy: no account, no ads.",
         "#F2D43D", "#1A1446", "#E0873A",
         stack(tile(ms.potion(), "Drink, and the wizard fills up", "Track your daily water with little potions."),
               tile(ms.sleeping_bear(), "Enchanted alarms", "Wake up with a smile, day or night."),
               tile(ms.crystal_ball(), "100% offline", "Your data never leaves your phone.")),
         ms.prop(ms.moon(), 930, 14, 90, 10))



def reddit():
    """Immagine 1200x675 in inglese per chiedere tester su Reddit."""
    chips = "".join(f'<div class="chip" style="font-size:28px;padding:12px 20px">{ico(e, c)}<span>{t}</span></div>' for e, t, c in [
        ("⚡", "Reminders, calendar & recurrences", BG["bolt"]),
        ("🚗", "Cars, warranties, trips & birthdays", BG["amber"]),
        ("💧", "Water potions & enchanted alarms", BG["cyan"])])
    body = (
        '<div style="position:absolute;left:520px;right:50px;top:34px;text-align:left">'
        '<span class="pill" style="font-size:28px">LOOKING FOR ANDROID TESTERS</span>'
        '<h1 style="font-size:72px;margin-top:14px;text-wrap:nowrap">Test Remindella<br><span>for 14 days</span></h1>'
        f'<div style="display:grid;gap:14px;margin-top:22px">{chips}</div>'
        '<div style="font-size:28px;font-weight:700;margin-top:20px;opacity:.95">100% offline · no account · no ads</div></div>'
        '<div style="position:absolute;left:50px;top:40px">' + ms.brand_title(44) + '</div>'
    )
    props = ms.prop(ms.wizard(), 60, 190, 420, -3) + ms.prop(ms.hat(), 400, 40, 90, 14)
    html = PAGE.format(fonts=ms.FONTS.as_uri(), title_css=ms.TITLE_CSS, h=675, hs=78, top="#3B1F73", bottom="#2E7FBF", accent="#F2D43D",
                       stars=stars(7, 675, (0, 0)), props=props, body=body).replace("width: 1080px", "width: 1200px")
    ms.render(html, OUT / "reddit-tester-request.png", 1200, 675)


if __name__ == "__main__":
    story()
    posts()
    reddit()
