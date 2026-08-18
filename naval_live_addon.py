"""Live mitmproxy addon used by naval_monitor_app.py."""

from __future__ import annotations

import base64
import json
import os
import threading
from datetime import datetime
from pathlib import Path
from typing import Any

from decode_dxx import WireError, decode_frame

GAME_HOST = "prod-game.survivorio.com"
STATE_PATH = Path(os.environ.get("SURVIVOR_NAVAL_STATE", "naval_live.json"))
LOCK = threading.Lock()
STATE: dict[str, Any] = {
    "updated_at": None, "board_number": 0,
    "source": "waiting for 19701/19702 board setup",
    "rows": None, "cols": None, "seed": None, "selected": [], "matrix": None,
}


def get_node(root: Any, *path: str) -> Any:
    current = root
    for key in path:
        if isinstance(current, dict) and current.get("wire") == "message":
            current = current.get("value")
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def scalar(node: Any, *path: str) -> Any:
    value = get_node(node, *path)
    return value.get("value") if isinstance(value, dict) and value.get("wire") in {"varint", "string"} else None


def bytes_value(node: Any, *path: str) -> bytes:
    value = get_node(node, *path)
    if isinstance(value, dict) and value.get("wire") == "bytes":
        return base64.b64decode(value.get("base64", ""))
    return b""


def save_and_print() -> None:
    with LOCK:
        STATE["updated_at"] = datetime.now().isoformat(timespec="seconds")
        STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        STATE_PATH.write_text(json.dumps(STATE, ensure_ascii=False, indent=2), encoding="utf-8")
        print("\n=== Survivor.io naval board ===", flush=True)
        print(f"Board {STATE['board_number']} | {STATE.get('rows')}x{STATE.get('cols')} | selecionadas: {len(STATE.get('selected', []))}", flush=True)
        rows = STATE.get("matrix")
        if rows:
            cols = int(STATE["cols"])
            selected = set(STATE.get("selected", []))
            for row, values in enumerate(rows):
                cells = []
                for col, value in enumerate(values):
                    cell_id = row * cols + col
                    text = str(value) if value else "."
                    cells.append(f"[{text}]" if cell_id in selected else f" {text} ")
                print("".join(cells), flush=True)
        print(f"Estado salvo em: {STATE_PATH}", flush=True)


def handle_setup(message: dict[str, Any]) -> bool:
    config = get_node(message.get("protobuf"), "3")
    rows, cols = scalar(config, "5"), scalar(config, "6")
    seed = bytes_value(config, "7")
    if not isinstance(rows, int) or not isinstance(cols, int) or len(seed) != rows * cols:
        return False
    with LOCK:
        if (STATE.get("rows"), STATE.get("cols"), STATE.get("seed")) == (rows, cols, list(seed)):
            return False
        STATE["board_number"] += 1
        STATE.update({"source": "19701/19702 setup", "rows": rows, "cols": cols,
                      "seed": list(seed), "selected": [],
                      "matrix": [list(seed[r * cols:(r + 1) * cols]) for r in range(rows)]})
    save_and_print()
    return True


def handle_shot(flow: Any) -> None:
    request = decode_frame(bytes(flow.request.raw_content or b""))
    with LOCK:
        STATE["selected"] = list(bytes_value(request.get("protobuf"), "2"))
        STATE["source"] = "19709/19710 selection update"
    save_and_print()


class NavalLiveAddon:
    def response(self, flow: Any) -> None:
        if (getattr(flow, "type", None) != "http" or not getattr(flow, "request", None)
                or flow.request.pretty_host != GAME_HOST or not getattr(flow, "response", None)):
            return
        try:
            message = decode_frame(bytes(flow.response.raw_content or b""))
            if message["message_type"] == 19702:
                handle_setup(message)
            elif message["message_type"] == 19710:
                request = decode_frame(bytes(flow.request.raw_content or b""))
                if request["message_type"] == 19709:
                    # A completed board can carry the next board setup inside
                    # the 19710 response itself. Do not apply the old board's
                    # selected-cell list after a new seed was detected.
                    new_board = handle_setup(message)
                    if not new_board:
                        handle_shot(flow)
        except (WireError, ValueError, KeyError):
            return


addons = [NavalLiveAddon()]
