from tools.simulate_system import run_simulation


def test_end_to_end_simulation():
    result = run_simulation()
    assert result["mode"] == "SIMULADO"
    assert result["captured"] == 4
    assert result["deduplicated"] == 3
    assert result["reconciled"] == 2
    assert result["month_total"] == 47_440
    assert result["chatgpt_package"]["privacy"].startswith("datos ficticios")
    assert result["local_assistant_preview"]["requires_user_confirmation"] is True
