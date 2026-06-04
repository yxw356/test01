#!/usr/bin/env python3
"""Small OpenAI-compatible embedding server for local development."""

from __future__ import annotations

import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


HOST = "127.0.0.1"
PORT = 8001
DEFAULT_DIMENSION = 1024
MODEL_NAME = "bge-m3"


def make_embedding(text: str, dimension: int) -> list[float]:
    seed = hashlib.sha256(text.encode("utf-8")).digest()
    values: list[float] = []
    counter = 0
    while len(values) < dimension:
        block = hashlib.sha256(seed + counter.to_bytes(4, "big")).digest()
        for byte in block:
            values.append((byte / 127.5) - 1.0)
            if len(values) >= dimension:
                break
        counter += 1

    norm = sum(v * v for v in values) ** 0.5 or 1.0
    return [round(v / norm, 8) for v in values]


class Handler(BaseHTTPRequestHandler):
    server_version = "LocalEmbedding/1.0"

    def log_message(self, fmt: str, *args: object) -> None:
        return

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path.rstrip("/") == "/v1/models":
            self._json(200, {"object": "list", "data": [{"id": MODEL_NAME, "object": "model"}]})
            return
        self._json(404, {"error": {"message": "not found"}})

    def do_POST(self) -> None:
        if self.path.rstrip("/") != "/v1/embeddings":
            self._json(404, {"error": {"message": "not found"}})
            return

        length = int(self.headers.get("Content-Length", "0"))
        request = json.loads(self.rfile.read(length) or b"{}")
        dimension = int(request.get("dimension") or DEFAULT_DIMENSION)
        input_value = request.get("input", [])
        inputs = input_value if isinstance(input_value, list) else [input_value]
        data = [
            {
                "object": "embedding",
                "index": index,
                "embedding": make_embedding(str(text), dimension),
            }
            for index, text in enumerate(inputs)
        ]
        self._json(200, {"object": "list", "model": request.get("model") or MODEL_NAME, "data": data})


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
