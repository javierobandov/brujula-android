"""Capture actual Compose destinations using their accessibility labels, not screen coordinates."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = Path('visual-lab')
OUT.mkdir(exist_ok=True)


def adb(*args, **kwargs):
    return subprocess.run(['adb', *args], check=True, timeout=45, **kwargs)


def capture(name):
    with (OUT / f'{name}.png').open('wb') as target:
        adb('exec-out', 'screencap', '-p', stdout=target)


def navigate(label):
    adb('shell', 'uiautomator', 'dump', '/sdcard/brujula-ui.xml', stdout=subprocess.DEVNULL)
    xml = adb('exec-out', 'cat', '/sdcard/brujula-ui.xml', capture_output=True).stdout
    root = ET.fromstring(xml)
    candidates = []
    for node in root.iter('node'):
        if label in (node.get('text'), node.get('content-desc')):
            bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
            if len(bounds) == 4 and bounds[3] > bounds[1]:
                candidates.append(bounds)
    if not candidates:
        raise AssertionError(f'Destino inaccesible: {label}')
    # The bottom-most matching label belongs to navigation, not the page heading.
    left, top, right, bottom = max(candidates, key=lambda b: b[3])
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(1)


time.sleep(2)
capture('01-resumen')
for number, label in enumerate(['Movimientos', 'Planificación', 'Cuenta'], 2):
    navigate(label)
    capture(f'{number:02d}-{label.lower()}')

# The revised spacing must survive larger text without losing navigation.
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
adb('shell', 'am', 'force-stop', 'cl.brujula.app')
adb('shell', 'am', 'start', '-n', 'cl.brujula.app/.MainActivity', stdout=subprocess.DEVNULL)
time.sleep(3)
capture('05-resumen-texto-grande')
navigate('Movimientos')
capture('06-movimientos-texto-grande')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
print('VISUAL_NAVIGATION_OK')
