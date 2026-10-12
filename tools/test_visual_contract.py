from pathlib import Path


SOURCE = Path(__file__).parents[1] / "android/app/src/main/java/cl/brujula/app/MainActivity.kt"


def test_visual_contract() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    required_labels = (
        'Text("BRÚJULA"',
        'Text("Actividad reciente"',
        '1 to "Movimientos"',
        'Text("Planificación mensual',
        'Text("Cuenta"',
        '"Consultar con ChatGPT"',
    )
    missing = [label for label in required_labels if label not in source]
    assert not missing, f"Faltan elementos visuales: {missing}"
    assert source.count("NavigationBarItem(") == 1
    assert source.count("ActionRow(") >= 12
    assert "Herramientas avanzadas" in source


if __name__ == "__main__":
    test_visual_contract()
    print("VISUAL_CONTRACT_OK")
