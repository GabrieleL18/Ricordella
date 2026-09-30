"""Grafica per il Play Store: il maghetto, la palla di vetro con la saetta e l'orso che dorme.

    python store/make_logo.py

Crea in store/output/:
- feature-graphic.png (1024x500): "Grafica in primo piano", con nome e motto;
- icon-512.png (512x512): icona con la scena completa;
- icon-sfera.png, icon-mago.png, icon-mago-sfera.png (512x512): icone semplici.
"""
from make_screens import BOLT, FONTS, OUT, TITLE_CSS, crystal_ball, prop, render, sleeping_bear, brand_title, wizard

MOTTO = "Ogni impegno al suo posto,<br>come per magia."


STYLE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Fredoka; src: url('{fonts}/fredoka.ttf'); }}
@font-face {{ font-family: Jakarta; src: url('{fonts}/plus_jakarta_sans.ttf'); }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; }}
body {{ background: radial-gradient(circle at 30% 45%, #7A4FD0 0, transparent 55%), linear-gradient(150deg, #2A1450, #3B1F73 45%, #2E7FBF);
        font-family: Jakarta, sans-serif; color: white; position: relative; }}
.prop {{ position: absolute; filter: drop-shadow(0 10px 14px rgba(0,0,0,.35)); }}
.prop svg {{ width: 100%; height: 100%; overflow: visible; display: block; }}
.star {{ position: absolute; background: white; clip-path: polygon(50% 0, 62% 38%, 100% 50%, 62% 62%, 50% 100%, 38% 62%, 0 50%, 38% 38%); }}
.hill {{ position: absolute; border-radius: 50%; background: linear-gradient(#4A2A8C, #2A1450); }}
{title_css}
.glow {{ position: absolute; border-radius: 50%; background: radial-gradient(circle, rgba(242,212,61,.55), transparent 65%); }}
</style></head><body>{body}</body></html>"""


def stars(positions):
    return "".join(
        f'<div class="star" style="left:{x}px;top:{y}px;width:{s}px;height:{s}px;opacity:{o}"></div>' for x, y, s, o in positions
    )


def moon(left, top, size):
    return prop(f'<path d="M58 6 A44 44 0 1 0 94 70 A36 36 0 1 1 58 6 Z" fill="{BOLT}"/>', left, top, size, -10)


def scene(x, y, k):
    """Mago, palla e orso a partire da ([x], [y]), scalati di [k]."""
    def at(dx, dy, size, *rest, **kw):
        return (x + dx * k, y + dy * k, size * k, *rest)
    parts = [
        f'<div class="hill" style="left:{x - 60 * k}px;top:{y + 330 * k}px;width:{600 * k}px;height:{220 * k}px"></div>',
        f'<div class="glow" style="left:{x + 230 * k}px;top:{y + 10 * k}px;width:{260 * k}px;height:{260 * k}px"></div>',
        prop(crystal_ball(), *at(265, 50, 210, -4)),
        prop(wizard(), *at(0, 40, 320, 0)),
        prop(sleeping_bear(), *at(250, 245, 230, 0)),
    ]
    return "".join(parts)


def feature_graphic():
    body = (
        stars([(560, 40, 18, .8), (980, 70, 12, .6), (720, 440, 14, .5), (470, 30, 10, .5), (40, 40, 14, .6), (950, 420, 20, .7), (620, 110, 8, .5)])
        + moon(880, 40, 90)
        + scene(10, 20, 0.95)
        + '<div style="position:absolute;left:525px;top:110px;width:480px">'
        + brand_title(80)
        + f'<div style="font-size:34px;font-weight:600;line-height:1.25;margin-top:18px">{MOTTO}</div>'
        '</div>'
    )
    render(STYLE.format(fonts=FONTS.as_uri(), title_css=TITLE_CSS, w=1024, h=500, body=body), OUT / "feature-graphic.png", 1024, 500)


def icon():
    """Il mago tocca con la bacchetta la palla di vetro, grande in alto a destra; l'orso dorme in primo piano."""
    # Google Play applica da sé gli angoli arrotondati: si riempie tutto il quadrato e si sta lontani dai bordi.
    body = (
        stars([(210, 40, 14, .7), (470, 330, 10, .5), (150, 70, 8, .5), (470, 40, 12, .6), (40, 250, 9, .4)])
        + moon(30, 26, 72)
        + '<div class="hill" style="left:-60px;top:400px;width:640px;height:240px"></div>'
        + '<div class="glow" style="left:195px;top:25px;width:290px;height:290px"></div>'
        + prop(crystal_ball(stand=False), 212, 62, 256, -4)
        + prop(wizard(), -14, 118, 300, 0)
        + prop(sleeping_bear(), 225, 262, 250, 0)
    )
    render(STYLE.format(fonts=FONTS.as_uri(), title_css=TITLE_CSS, w=512, h=512, body=body), OUT / "icon-512.png", 512, 512)


def simple_icons():
    """Icone semplici, un solo soggetto grande al centro: la sfera, il mago, il mago con la sfera."""
    sky = stars([(60, 70, 16, .7), (440, 60, 12, .6), (450, 420, 14, .5), (70, 430, 10, .5), (400, 250, 8, .4), (110, 250, 8, .4)])
    icons = {
        "icon-sfera": '<div class="glow" style="left:56px;top:40px;width:400px;height:400px"></div>' + prop(crystal_ball(), 66, 50, 380, 0),
        "icon-mago": prop(wizard(), 62, 62, 400, 0),
        # Metà sinistra: il mago allibito in primo piano; metà destra: la sfera che guarda.
        "icon-mago-sfera": (
            prop(wizard(amazed=True, arm=False), -212, 20, 750, 0)
            + '<div class="glow" style="left:236px;top:106px;width:300px;height:300px"></div>'
            + prop(crystal_ball(stand=False), 236, 124, 300, -4)
        ),
    }
    for name, body in icons.items():
        render(STYLE.format(fonts=FONTS.as_uri(), title_css=TITLE_CSS, w=512, h=512, body=sky + body), OUT / f"{name}.png", 512, 512)


if __name__ == "__main__":
    feature_graphic()
    icon()
    simple_icons()
