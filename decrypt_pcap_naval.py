"""Extrai e decodifica quadros Dxx do evento Naval a partir de um arquivo .pcap do PCAPdroid usando o sslkeylogfile.txt."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any

from decode_dxx import decode_frame

TSHARK = r"C:\Program Files\Wireshark\tshark.exe"


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
    val = get_node(node, *path)
    return val.get("value") if isinstance(val, dict) and val.get("wire") in {"varint", "string"} else None


def bytes_value(node: Any, *path: str) -> bytes:
    import base64
    val = get_node(node, *path)
    return base64.b64decode(val.get("base64", "")) if isinstance(val, dict) and val.get("wire") == "bytes" else b""


def extract_decrypted_hex(pcap_path: Path, keylog_path: Path) -> list[bytes]:
    cmd = [
        TSHARK,
        "-r", str(pcap_path),
        "-o", f"tls.keylog_file:{keylog_path}",
        "-Y", "tls.app_data",
        "-T", "fields",
        "-e", "tls.app_data"
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    payloads = []
    for line in result.stdout.splitlines():
        hex_str = line.strip().replace(":", "")
        if hex_str:
            try:
                payloads.append(bytes.fromhex(hex_str))
            except ValueError:
                pass
    return payloads


def parse_dxx_stream(payloads: list[bytes]) -> list[dict[str, Any]]:
    full_stream = b"".join(payloads)
    frames = []
    pos = 0
    while pos + 6 <= len(full_stream):
        msg_type = int.from_bytes(full_stream[pos:pos + 2], "little")
        payload_size = int.from_bytes(full_stream[pos + 2:pos + 6], "little")

        if 10000 <= msg_type <= 40000 and 0 < payload_size <= 500000:
            end = pos + 6 + payload_size
            if end <= len(full_stream):
                raw_frame = full_stream[pos:end]
                try:
                    decoded = decode_frame(raw_frame)
                    frames.append(decoded)
                    pos = end
                    continue
                except Exception:
                    pass
        pos += 1
    return frames


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pcap", type=Path, help="Caminho para o arquivo .pcap gravado pelo PCAPdroid")
    parser.add_argument("--keylog", type=Path, default=Path("sslkeylogfile.txt"), help="Caminho para o sslkeylogfile.txt")
    args = parser.parse_args()

    pcap_path = args.pcap.resolve()
    keylog_path = args.keylog.resolve()

    if not pcap_path.is_file():
        print(f"[ERRO] Arquivo pcap não encontrado: {pcap_path}")
        return 1

    if not keylog_path.is_file():
        print(f"[ERRO] Arquivo sslkeylogfile não encontrado: {keylog_path}")
        return 1

    print(f"Lendo PCAP  : {pcap_path.name}")
    print(f"Chaves TLS  : {keylog_path.name}")

    payloads = extract_decrypted_hex(pcap_path, keylog_path)
    print(f"Blocos TLS  : {len(payloads)} encontrados")

    frames = parse_dxx_stream(payloads)
    print(f"Quadros Dxx : {len(frames)} decodificados com sucesso!\n")

    naval_events = []
    type_counts: dict[int, int] = {}

    for frame in frames:
        msg_type = frame["message_type"]
        type_counts[msg_type] = type_counts.get(msg_type, 0) + 1

        if msg_type == 19702:
            config = get_node(frame.get("protobuf"), "3")
            rows = scalar(config, "5")
            cols = scalar(config, "6")
            seed = bytes_value(config, "7")
            if rows and cols and seed:
                naval_events.append({
                    "opcode": 19702,
                    "event": "SETUP_TABULEIRO",
                    "rows": rows,
                    "cols": cols,
                    "seed": list(seed),
                    "matrix": [list(seed[r * cols:(r + 1) * cols]) for r in range(rows)]
                })

        elif msg_type == 19710:
            protobuf = frame.get("protobuf")
            selected = list(bytes_value(protobuf, "2"))
            naval_events.append({
                "opcode": 19710,
                "event": "SELECAO_TIROS",
                "selected": selected
            })

    report = {
        "pcap": str(pcap_path),
        "total_frames": len(frames),
        "opcodes": type_counts,
        "naval_events": naval_events
    }

    output_path = pcap_path.with_suffix(".naval_decoded.json")
    output_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Opcodes encontrados: {type_counts}")
    print(f"Eventos Navais     : {len(naval_events)}")
    print(f"Resultado salvo em : {output_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
