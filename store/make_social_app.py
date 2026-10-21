"""Immagini social di Remindella (in inglese, per cercare tester Android): storia Instagram 1080x1920 e immagine Facebook 1200x630: cerco tester Android.

Usa le schermate di store/screenshots/en/ (le stesse dello store) e la grafica di make_screens.py.
Le immagini finite finiscono in store/output/social/. Serve Microsoft Edge o Google Chrome.

    python store/make_social_app.py
"""
import make_screens as ms

SHOTS = ms.ROOT / "screenshots"
LANG = "en"
OUT = ms.OUT / "social"
ASPECT = 2424 / 1080  # le schermate sono 1080x2424


def phone(name, left, top, width, rotate=0, z=1):
    """Telefono con la schermata [name] (senza barra di stato), largo [width] px."""
    pad = width * 0.03
    inner = width - 2 * pad
    height = inner * (ASPECT - ms.STATUS_BAR) + 2 * pad
    path = next(p for e in ("png", "jpg") if (p := SHOTS / LANG / f"{name}.{e}").exists())
    return (f'<div style="position:absolute;left:{left}px;top:{top}px;width:{width}px;height:{height:.0f}px;z-index:{z};'
            f'transform:rotate({rotate}deg);background:#111319;border-radius:{width * 0.108:.0f}px;padding:{pad:.0f}px;box-sizing:border-box;'
            f'box-shadow:0 30px 70px rgba(0,0,0,.45),0 0 0 {max(3, width * 0.008):.0f}px rgba(255,255,255,.14)">'
            f'<div style="width:100%;height:100%;border-radius:{width * 0.08:.0f}px;overflow:hidden">'
            f'<img src="{path.as_uri()}" style="width:100%;display:block;margin-top:-{ms.STATUS_BAR * 100:.1f}%"></div></div>')


PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Fredoka; src: url('{fonts}/fredoka.ttf'); }}
@font-face {{ font-family: Jakarta; src: url('{fonts}/plus_jakarta_sans.ttf'); }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; }}
body {{ background: linear-gradient(160deg, #3B1F73, #2E7FBF); font-family: Jakarta, sans-serif; color: white; position: relative; }}
.star {{ position: absolute; background: white; clip-path: polygon(50% 0, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0 50%, 38% 38%); }}
.prop {{ position: absolute; filter: drop-shadow(0 14px 18px rgba(0,0,0,.35)); }}
.prop svg {{ width: 100%; height: 100%; overflow: visible; display: block; }}
h1 {{ font-family: Fredoka, sans-serif; font-weight: 600; line-height: 1.05; margin: 0; position: absolute; text-wrap: balance; }}
h1 span {{ color: #F2D43D; }}
p {{ position: absolute; margin: 0; opacity: .92; line-height: 1.3; text-wrap: balance; }}
.cta {{ position: absolute; background: #F2D43D; color: #1E0E40; font-weight: 800; border-radius: 999px; box-shadow: 0 6px 0 #B39400; text-align: center; }}
{title_css}
</style></head><body>
{body}
</body></html>"""


def sky(seed, count, w, h, keep_clear):
    import random
    rnd = random.Random(seed)
    out = []
    while len(out) < count:
        size, x, y = rnd.randint(10, 30), rnd.randint(0, w - 30), rnd.randint(0, h - 30)
        if keep_clear(x, y):
            continue
        out.append(f'<div class="star" style="left:{x}px;top:{y}px;width:{size}px;height:{size}px;opacity:{rnd.uniform(.25, .8):.2f}"></div>')
    return "".join(out)


STORY_TEXTS = {
    "en": ("LOOKING FOR ANDROID TESTERS", "Help me test<br><span>Remindella</span>",
           "Reminders, birthdays, trips, expenses and loyalty cards in one app. Try it for 14 days", "Send me a DM to join"),
    "it": ("CERCO TESTER ANDROID", "Aiutami a testare<br><span>Remindella</span>",
           "Promemoria, compleanni, viaggi, spese e carte fedeltà in un’unica app. Provala per 14 giorni", "Scrivimi in DM per partecipare"),
}


def story(lang="en"):
    global LANG
    LANG = lang
    pill, title, sub, cta = STORY_TEXTS[lang]
    body = (
        sky(3, 30, 1080, 1920, lambda x, y: 240 < y < 830)
        + ms.prop(ms.hat(), 860, 190, 160, 14) + ms.prop(ms.wand(), 20, 840, 150, -10)
        # Zona sicura di Instagram: niente testo importante nei primi ~250 px e negli ultimi ~340.
        + f'<div style="position:absolute;left:0;right:0;top:250px;display:flex;flex-direction:column;align-items:center">{ms.brand_title(78)}</div>'
        + f'<div style="position:absolute;left:0;right:0;top:400px;text-align:center"><span style="display:inline-block;background:#F2D43D;color:#1E0E40;font-weight:800;font-size:34px;letter-spacing:.06em;padding:12px 32px;border-radius:999px;box-shadow:0 6px 0 #B39400">{pill}</span></div><h1 style="left:0;right:0;top:490px;text-align:center;font-size:104px">{title}</h1>'
        + f'<p style="left:90px;right:90px;top:735px;text-align:center;font-size:38px">{sub}</p>'
        + phone("expenses", -40, 1060, 420, -8, 1)
        + phone("cards", 700, 1060, 420, 8, 1)
        + phone("home", 290, 880, 500, -2, 2)
        + '<div style="position:absolute;left:0;right:0;bottom:0;height:520px;z-index:3;background:linear-gradient(transparent,#2A1860 70%)"></div>'
        + f'<div class="cta" style="z-index:4;left:150px;right:150px;top:1600px;font-size:46px;padding:34px 0">{cta}</div>'
    )
    return PAGE.format(fonts=ms.FONTS.as_uri(), title_css=ms.TITLE_CSS, w=1080, h=1920, body=body)


def facebook():
    global LANG
    LANG = "en"
    body = (
        sky(5, 22, 1200, 630, lambda x, y: x < 600 and 40 < y < 590)
        + f'<div style="position:absolute;left:64px;top:46px">{ms.brand_title(40)}</div>'
        + '<div style="position:absolute;left:64px;top:132px;background:#F2D43D;color:#1E0E40;font-weight:800;font-size:19px;letter-spacing:.06em;padding:8px 20px;border-radius:999px;box-shadow:0 3px 0 #B39400">LOOKING FOR ANDROID TESTERS</div><h1 style="left:64px;top:190px;width:520px;font-size:74px">Help me test<br><span>Remindella</span></h1>'
        + '<p style="left:64px;top:382px;width:480px;font-size:25px">Reminders, birthdays, trips, expenses and loyalty cards in one app. Try it for 14 days</p>'
        + '<div class="cta" style="left:64px;top:512px;width:330px;font-size:25px;padding:18px 0;box-shadow:0 4px 0 #B39400">Message me to join</div>'
        + phone("expenses", 590, 95, 250, -6, 1)
        + phone("cards", 950, 100, 250, 7, 1)
        + phone("home", 735, 45, 300, 0, 2)
    )
    return PAGE.format(fonts=ms.FONTS.as_uri(), title_css=ms.TITLE_CSS, w=1200, h=630, body=body)


if __name__ == "__main__":
    ms.render(story("it"), OUT / "instagram-storia.png", 1080, 1920)
    ms.render(facebook(), OUT / "facebook.png", 1200, 630)
