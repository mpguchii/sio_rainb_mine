"""Decode Survivor.io Dxx frames and schema-less protobuf payloads to JSON.

Dxx HTTP bodies observed in the Windows Google Play Games client use:

    uint16_le message_type
    uint32_le protobuf_payload_size
    bytes       protobuf_payload

The protobuf decoder intentionally keeps numeric field identifiers because the
game's .proto descriptors have not yet been recovered.
"""

from __future__ import annotations

import argparse
import base64
import json
import struct
from collections import defaultdict
from pathlib import Path
from typing import Any

from mitmproxy import io


GAME_HOST = "prod-game.survivorio.com"


class WireError(ValueError):
    pass


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    for _ in range(10):
        if offset >= len(data):
            raise WireError("truncated varint")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, offset
        shift += 7
    raise WireError("varint exceeds 10 bytes")


def printable_utf8(data: bytes) -> str | None:
    if not data:
        return ""
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        return None
    printable = sum(character.isprintable() or character in "\r\n\t" for character in text)
    return text if printable / max(len(text), 1) >= 0.9 else None


def add_field(result: dict[str, Any], field_number: int, value: Any) -> None:
    key = str(field_number)
    if key not in result:
        result[key] = value
    elif isinstance(result[key], list):
        result[key].append(value)
    else:
        result[key] = [result[key], value]


def decode_length_delimited(data: bytes, depth: int) -> Any:
    text = printable_utf8(data)
    if text is not None and text:
        return {"wire": "string", "value": text}
    if not data:
        return {"wire": "bytes", "size": 0, "base64": ""}
    if depth < 12:
        try:
            nested = decode_message(data, depth + 1)
            if nested:
                return {"wire": "message", "value": nested}
        except WireError:
            pass
    return {
        "wire": "bytes",
        "size": len(data),
        "base64": base64.b64encode(data).decode("ascii"),
    }


def decode_message(data: bytes, depth: int = 0) -> dict[str, Any]:
    result: dict[str, Any] = {}
    offset = 0
    while offset < len(data):
        tag, offset = read_varint(data, offset)
        field_number = tag >> 3
        wire_type = tag & 7
        if field_number <= 0 or field_number > 536_870_911:
            raise WireError(f"invalid field number {field_number}")

        if wire_type == 0:
            value, offset = read_varint(data, offset)
            decoded: Any = {"wire": "varint", "value": value}
        elif wire_type == 1:
            if offset + 8 > len(data):
                raise WireError("truncated fixed64")
            raw = data[offset : offset + 8]
            offset += 8
            decoded = {
                "wire": "fixed64",
                "unsigned": int.from_bytes(raw, "little"),
                "double": struct.unpack("<d", raw)[0],
            }
        elif wire_type == 2:
            size, offset = read_varint(data, offset)
            end = offset + size
            if end > len(data):
                raise WireError("truncated length-delimited field")
            decoded = decode_length_delimited(data[offset:end], depth)
            offset = end
        elif wire_type == 5:
            if offset + 4 > len(data):
                raise WireError("truncated fixed32")
            raw = data[offset : offset + 4]
            offset += 4
            decoded = {
                "wire": "fixed32",
                "unsigned": int.from_bytes(raw, "little"),
                "float": struct.unpack("<f", raw)[0],
            }
        else:
            raise WireError(f"unsupported wire type {wire_type}")
        add_field(result, field_number, decoded)
    return result


def decode_frame(data: bytes) -> dict[str, Any]:
    if len(data) < 6:
        raise WireError(f"Dxx frame is only {len(data)} bytes")
    message_type = int.from_bytes(data[0:2], "little")
    declared_size = int.from_bytes(data[2:6], "little")
    payload = data[6:]
    if declared_size != len(payload):
        raise WireError(
            f"Dxx length mismatch: declared {declared_size}, actual {len(payload)}"
        )
    return {
        "message_type": message_type,
        "payload_size": declared_size,
        "protobuf": decode_message(payload) if payload else {},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    capture = args.capture.resolve()
    output = (args.output or capture.with_suffix(".dxx.json")).resolve()

    messages = []
    type_stats = defaultdict(lambda: {"requests": 0, "responses": 0})
    with capture.open("rb") as handle:
        for flow_sequence, flow in enumerate(io.FlowReader(handle).stream()):
            if (
                getattr(flow, "type", None) != "http"
                or not getattr(flow, "request", None)
                or flow.request.pretty_host != GAME_HOST
            ):
                continue
            row: dict[str, Any] = {
                "flow_sequence": flow_sequence,
                "dxx_request_header": flow.request.headers.get("DxxREQ", ""),
                "dxx_type_header": flow.request.headers.get("DxxType", ""),
            }
            try:
                row["request"] = decode_frame(bytes(flow.request.raw_content or b""))
                type_stats[str(row["request"]["message_type"])]["requests"] += 1
            except WireError as error:
                row["request_error"] = str(error)
            if getattr(flow, "response", None):
                try:
                    row["response"] = decode_frame(bytes(flow.response.raw_content or b""))
                    type_stats[str(row["response"]["message_type"])]["responses"] += 1
                except WireError as error:
                    row["response_error"] = str(error)
            messages.append(row)

    report = {
        "capture": str(capture),
        "format": "uint16_le message_type + uint32_le payload_size + protobuf",
        "type_stats": dict(sorted(type_stats.items(), key=lambda item: int(item[0]))),
        "messages": messages,
    }
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    request_errors = sum("request_error" in message for message in messages)
    response_errors = sum("response_error" in message for message in messages)
    print(f"Dxx exchanges : {len(messages)}")
    print(f"Decode errors : request={request_errors}, response={response_errors}")
    print(f"Output        : {output}")
    print("Message types :")
    for message_type, counts in report["type_stats"].items():
        print(f"- {message_type}: {counts}")
    return 0 if not request_errors and not response_errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
