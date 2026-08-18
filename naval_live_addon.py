"""PCAPdroid-mitm addon for Survivor.io naval-board monitoring.

Copy this file to the PCAPdroid-mitm user addon directory and enable it.
It sends compact JSON state updates to the monitor app at 127.0.0.1:8086.
"""

from __future__ import annotations

import base64
import json
import socket
import struct
import time


GAME_HOST = "prod-game.survivorio.com"
DESTINATION = ("127.0.0.1", 8086)
STATE = {"board_number": 0, "rows": 0, "cols": 0, "matrix": [], "selected": []}


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while offset < len(data) and shift < 70:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, offset
        shift += 7
    raise ValueError("invalid varint")


def parse_message(data: bytes) -> dict[int, list[object]]:
    result: dict[int, list[object]] = {}
    offset = 0
    while offset < len(data):
        tag, offset = read_varint(data, offset)
        field = tag >> 3
        wire = tag & 7
        if wire == 0:
            value, offset = read_varint(data, offset)
        elif wire == 2:
            size, offset = read_varint(data, offset)
            value = data[offset:offset + size]
            offset += size
        elif wire == 1:
            value = data[offset:offset + 8]
            offset += 8
        elif wire == 5:
            value = data[offset:offset + 4]
            offset += 4
        else:
            raise ValueError("unsupported protobuf wire type")
        result.setdefault(field, []).append(value)
    return result


def first(fields: dict[int, list[object]], number: int, default=None):
    return fields.get(number, [default])[0]


def decode_frames(data: bytes):
    for offset in range(max(0, len(data) - 5)):
        if offset + 6 > len(data):
            break
        message_type, payload_size = struct.unpack_from("<HI", data, offset)
        if not 10000 <= message_type <= 60000 or not 0 < payload_size <= 2_000_000:
            continue
        end = offset + 6 + payload_size
        if end > len(data):
            continue
        yield message_type, data[offset + 6:end]


def send_state(message_type: int):
    payload = dict(STATE)
    payload["type"] = message_type
    payload["updated_at"] = int(time.time())
    try:
        socket.socket(socket.AF_INET, socket.SOCK_DGRAM).sendto(
            json.dumps(payload, separators=(",", ":")).encode("utf-8"), DESTINATION
        )
    except OSError:
        pass


def handle_frame(message_type: int, payload: bytes):
    fields = parse_message(payload)
    if message_type == 19702:
        if update_board_from_config(first(fields, 3, b"")):
            send_state(message_type)
    elif message_type == 19709:
        selected = bytes(first(fields, 2, b""))
        STATE["selected"] = list(selected)
        send_state(message_type)
    elif message_type == 19710:
        response_bytes = first(fields, 3, b"")
        # When a board is completed, the next board setup can be embedded
        # directly in the 19710 response instead of arriving as 19702.
        if update_board_from_config(response_bytes):
            send_state(message_type)
        response = parse_message(response_bytes)
        selected = bytes(first(response, 11, b""))
        if selected:
            STATE["selected"] = list(selected)
        send_state(message_type)


def update_board_from_config(config_bytes: bytes) -> bool:
    try:
        config = parse_message(config_bytes)
        rows = int(first(config, 5, 0))
        cols = int(first(config, 6, 0))
        seed = bytes(first(config, 7, b""))
    except (TypeError, ValueError):
        return False
    if rows <= 0 or cols <= 0 or len(seed) != rows * cols:
        return False
    if STATE["rows"] == rows and STATE["cols"] == cols and STATE["matrix"] == list(seed):
        return False
    STATE.update({
        "board_number": STATE["board_number"] + 1,
        "rows": rows,
        "cols": cols,
        "matrix": list(seed),
        "selected": [],
    })
    return True


class SurvivorNavalAddon:
    def request(self, flow):
        if flow.request.pretty_host != GAME_HOST:
            return
        for message_type, payload in decode_frames(bytes(flow.request.content or b"")):
            handle_frame(message_type, payload)

    def response(self, flow):
        if flow.request.pretty_host != GAME_HOST:
            return
        for message_type, payload in decode_frames(bytes(flow.response.content or b"")):
            handle_frame(message_type, payload)


addons = [SurvivorNavalAddon()]
