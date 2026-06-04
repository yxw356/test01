#!/usr/bin/env python3
"""Local load test for the knowledge base app.

The script intentionally uses only the Python standard library so it can run on
the project machine without installing locust/k6/websocket-client.
"""

from __future__ import annotations

import argparse
import base64
import concurrent.futures
import dataclasses
import hashlib
import json
import os
import random
import socket
import ssl
import statistics
import struct
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http import HTTPStatus
from typing import Any


@dataclasses.dataclass(frozen=True)
class Sample:
    scenario: str
    ok: bool
    duration_ms: float
    error: str | None


class HttpClient:
    def __init__(self, base_url: str, timeout: float):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.token: str | None = None
        self.chat_message = "请用一句话说明知识库系统当前是否可用。"

    def request(
        self,
        method: str,
        path: str,
        body: bytes | None = None,
        headers: dict[str, str] | None = None,
    ) -> tuple[int, dict[str, Any] | str]:
        request_headers = dict(headers or {})
        if self.token:
            request_headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(
            f"{self.base_url}{path}",
            data=body,
            headers=request_headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read()
                return resp.status, decode_response(raw)
        except urllib.error.HTTPError as exc:
            raw = exc.read()
            return exc.code, decode_response(raw)

    def login(self, username: str, password: str) -> None:
        body = json.dumps({"username": username, "password": password}).encode()
        status, payload = self.request(
            "POST",
            "/api/v1/users/login",
            body,
            {"Content-Type": "application/json"},
        )
        if status != HTTPStatus.OK or not isinstance(payload, dict):
            raise RuntimeError(f"login failed status={status} payload={payload}")
        self.token = payload["data"]["token"]


def decode_response(raw: bytes) -> dict[str, Any] | str:
    if not raw:
        return {}
    text = raw.decode("utf-8", errors="replace")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def timed(scenario: str, fn) -> Sample:
    started = time.perf_counter()
    try:
        fn()
        return Sample(scenario, True, elapsed_ms(started), None)
    except Exception as exc:  # noqa: BLE001 - load tests should record all failures
        return Sample(scenario, False, elapsed_ms(started), str(exc))


def elapsed_ms(started: float) -> float:
    return round((time.perf_counter() - started) * 1000, 2)


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((pct / 100) * (len(ordered) - 1))))
    return round(ordered[index], 2)


def summarize_results(results: list[Sample]) -> dict[str, Any]:
    by_scenario: dict[str, list[Sample]] = {}
    for result in results:
        by_scenario.setdefault(result.scenario, []).append(result)

    return {
        "overall": summarize_group(results),
        "by_scenario": {
            scenario: summarize_group(samples)
            for scenario, samples in sorted(by_scenario.items())
        },
    }


def summarize_group(samples: list[Sample]) -> dict[str, Any]:
    durations = [sample.duration_ms for sample in samples]
    errors = [sample for sample in samples if not sample.ok]
    error_counts: dict[str, int] = {}
    for sample in errors:
        message = sample.error or "unknown error"
        error_counts[message] = error_counts.get(message, 0) + 1
    return {
        "total": len(samples),
        "ok": len(samples) - len(errors),
        "errors": len(errors),
        "error_rate": round(len(errors) / len(samples), 4) if samples else 0,
        "avg_ms": round(statistics.mean(durations), 2) if durations else 0.0,
        "p50_ms": percentile(durations, 50),
        "p95_ms": percentile(durations, 95),
        "p99_ms": percentile(durations, 99),
        "max_ms": round(max(durations), 2) if durations else 0.0,
        "top_errors": [
            {"message": message, "count": count}
            for message, count in sorted(error_counts.items(), key=lambda item: item[1], reverse=True)[:5]
        ],
    }


def weighted_choice(choices: list[tuple[str, int]]) -> str:
    positive = [(name, weight) for name, weight in choices if weight > 0]
    if not positive:
        raise ValueError("at least one positive weight is required")
    total = sum(weight for _, weight in positive)
    pick = random.uniform(0, total)
    upto = 0.0
    for name, weight in positive:
        upto += weight
        if pick <= upto:
            return name
    return positive[-1][0]


def scenario_list(client: HttpClient) -> None:
    status, payload = client.request("GET", "/api/v1/documents/accessible")
    assert_ok(status, payload, "knowledge list")


def scenario_preflight(client: HttpClient) -> None:
    status, payload = client.request("GET", "/api/v1/upload/preflight")
    assert_ok(status, payload, "upload preflight")


def scenario_upload(client: HttpClient, user_index: int) -> None:
    now = int(time.time() * 1000)
    text = (
        f"# load test {user_index}-{now}\n\n"
        "这是本地压测上传的小文件，用于验证 20 人同时访问时上传链路是否稳定。\n"
    )
    content = text.encode("utf-8")
    file_md5 = hashlib.md5(content).hexdigest()
    file_name = f"loadtest-{user_index}-{now}.md"
    multipart_body, boundary = build_multipart(
        {
            "fileMd5": file_md5,
            "chunkIndex": "0",
            "totalSize": str(len(content)),
            "fileName": file_name,
            "totalChunks": "1",
            "knowledgeScope": "PUBLIC",
            "departmentId": "DEFAULT",
            "orgTag": "DEFAULT",
            "isPublic": "true",
        },
        "file",
        file_name,
        content,
        "text/markdown",
    )
    status, payload = client.request(
        "POST",
        "/api/v1/upload/chunk",
        multipart_body,
        {"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    assert_ok(status, payload, "upload chunk")

    merge_body = json.dumps({"fileMd5": file_md5, "fileName": file_name}).encode()
    status, payload = client.request(
        "POST",
        "/api/v1/upload/merge",
        merge_body,
        {"Content-Type": "application/json"},
    )
    assert_ok(status, payload, "upload merge")


def scenario_chat(client: HttpClient, base_url: str, timeout: float, mode: str) -> None:
    if not client.token:
        raise RuntimeError("missing token")
    ws_url = to_ws_url(base_url, f"/chat/{urllib.parse.quote(client.token, safe='')}")
    with WebSocketClient(ws_url, timeout=timeout) as ws:
        ws.send_text(client.chat_message)
        deadline = time.time() + timeout
        seen_payload = False
        while time.time() < deadline:
            frame = ws.recv_text()
            if frame is None:
                continue
            seen_payload = True
            try:
                payload = json.loads(frame)
            except json.JSONDecodeError:
                payload = {}
            if is_chat_satisfied(payload, mode):
                return
            if payload.get("error"):
                raise RuntimeError(payload["error"])
        if not seen_payload:
            raise RuntimeError("chat websocket produced no payload")
        raise RuntimeError("chat websocket timed out before completion")


def is_chat_satisfied(payload: dict[str, Any], mode: str) -> bool:
    if mode == "first-byte":
        return bool(payload.get("chunk")) or payload.get("type") == "completion"
    return payload.get("type") == "completion" and payload.get("status") == "finished"


def assert_ok(status: int, payload: dict[str, Any] | str, label: str) -> None:
    if status < 200 or status >= 300:
        raise RuntimeError(f"{label} failed status={status} payload={payload}")
    if isinstance(payload, dict) and payload.get("code", 200) >= 400:
        raise RuntimeError(f"{label} failed payload={payload}")


def build_multipart(
    fields: dict[str, str],
    file_field: str,
    file_name: str,
    file_content: bytes,
    content_type: str,
) -> tuple[bytes, str]:
    boundary = "----codexloadtest" + hashlib.md5(os.urandom(16)).hexdigest()
    parts: list[bytes] = []
    for name, value in fields.items():
        parts.extend(
            [
                f"--{boundary}\r\n".encode(),
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode(),
                value.encode(),
                b"\r\n",
            ]
        )
    parts.extend(
        [
            f"--{boundary}\r\n".encode(),
            (
                f'Content-Disposition: form-data; name="{file_field}"; '
                f'filename="{file_name}"\r\n'
            ).encode(),
            f"Content-Type: {content_type}\r\n\r\n".encode(),
            file_content,
            b"\r\n",
            f"--{boundary}--\r\n".encode(),
        ]
    )
    return b"".join(parts), boundary


class WebSocketClient:
    def __init__(self, url: str, timeout: float):
        self.url = url
        self.timeout = timeout
        self.sock: socket.socket | ssl.SSLSocket | None = None

    def __enter__(self) -> "WebSocketClient":
        parsed = urllib.parse.urlparse(self.url)
        host = parsed.hostname or "127.0.0.1"
        port = parsed.port or (443 if parsed.scheme == "wss" else 80)
        path = parsed.path or "/"
        if parsed.query:
            path += f"?{parsed.query}"
        raw_sock = socket.create_connection((host, port), timeout=self.timeout)
        if parsed.scheme == "wss":
            self.sock = ssl.create_default_context().wrap_socket(raw_sock, server_hostname=host)
        else:
            self.sock = raw_sock
        self.sock.settimeout(self.timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        self.sock.sendall(request.encode())
        response = self.sock.recv(4096)
        if b" 101 " not in response.split(b"\r\n", 1)[0]:
            raise RuntimeError(f"websocket handshake failed: {response[:200]!r}")
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        if self.sock:
            try:
                self.sock.close()
            finally:
                self.sock = None

    def send_text(self, text: str) -> None:
        payload = text.encode("utf-8")
        self._send_frame(0x1, payload)

    def recv_text(self) -> str | None:
        if not self.sock:
            raise RuntimeError("websocket not connected")
        header = self._recv_exact(2)
        if not header:
            return None
        first, second = header
        opcode = first & 0x0F
        length = second & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._recv_exact(8))[0]
        masked = second & 0x80
        mask = self._recv_exact(4) if masked else b""
        payload = self._recv_exact(length) if length else b""
        if masked:
            payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
        if opcode == 0x8:
            return None
        if opcode == 0x9:
            self._send_frame(0xA, payload)
            return None
        if opcode != 0x1:
            return None
        return payload.decode("utf-8", errors="replace")

    def _send_frame(self, opcode: int, payload: bytes) -> None:
        if not self.sock:
            raise RuntimeError("websocket not connected")
        mask = os.urandom(4)
        first = 0x80 | opcode
        length = len(payload)
        if length < 126:
            header = struct.pack("!BB", first, 0x80 | length)
        elif length < 65536:
            header = struct.pack("!BBH", first, 0x80 | 126, length)
        else:
            header = struct.pack("!BBQ", first, 0x80 | 127, length)
        masked_payload = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
        self.sock.sendall(header + mask + masked_payload)

    def _recv_exact(self, size: int) -> bytes:
        if not self.sock:
            raise RuntimeError("websocket not connected")
        chunks = bytearray()
        while len(chunks) < size:
            chunk = self.sock.recv(size - len(chunks))
            if not chunk:
                raise RuntimeError("websocket closed")
            chunks.extend(chunk)
        return bytes(chunks)


def to_ws_url(base_url: str, path: str) -> str:
    parsed = urllib.parse.urlparse(base_url)
    scheme = "wss" if parsed.scheme == "https" else "ws"
    netloc = parsed.netloc
    return urllib.parse.urlunparse((scheme, netloc, path, "", "", ""))


def virtual_user(user_index: int, args: argparse.Namespace, stop_at: float, sink: list[Sample], lock: threading.Lock) -> None:
    client = HttpClient(args.base_url, args.timeout)
    client.chat_message = args.chat_message
    sample = timed("login", lambda: client.login(args.username, args.password))
    append_sample(sink, lock, sample)
    if not sample.ok:
        return

    choices = [
        ("list", args.list_weight),
        ("preflight", args.preflight_weight),
        ("upload", args.upload_weight),
        ("chat", args.chat_weight),
    ]
    while time.time() < stop_at:
        scenario = weighted_choice(choices)
        if scenario == "list":
            sample = timed("list", lambda: scenario_list(client))
        elif scenario == "preflight":
            sample = timed("preflight", lambda: scenario_preflight(client))
        elif scenario == "upload":
            sample = timed("upload", lambda: scenario_upload(client, user_index))
        else:
            sample = timed("chat", lambda: scenario_chat(client, args.base_url, args.chat_timeout, args.chat_mode))
        append_sample(sink, lock, sample)
        if args.think_time > 0:
            time.sleep(random.uniform(0, args.think_time))


def append_sample(sink: list[Sample], lock: threading.Lock, sample: Sample) -> None:
    with lock:
        sink.append(sample)


def run(args: argparse.Namespace) -> dict[str, Any]:
    stop_at = time.time() + args.duration
    results: list[Sample] = []
    lock = threading.Lock()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.users) as executor:
        futures = []
        for user_index in range(args.users):
            if args.ramp_seconds > 0:
                time.sleep(args.ramp_seconds / args.users)
            futures.append(executor.submit(virtual_user, user_index, args, stop_at, results, lock))
        for future in concurrent.futures.as_completed(futures):
            future.result()
    return summarize_results(results)


def print_summary(summary: dict[str, Any]) -> None:
    print(json.dumps(summary, ensure_ascii=False, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a local mixed load test against the knowledge base app.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--users", type=int, default=20)
    parser.add_argument("--duration", type=int, default=60, help="test duration in seconds")
    parser.add_argument("--ramp-seconds", type=float, default=10.0)
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--chat-timeout", type=float, default=60.0)
    parser.add_argument("--chat-mode", choices=["first-byte", "completion"], default="first-byte")
    parser.add_argument("--chat-message", default="请用一句话说明知识库系统当前是否可用。")
    parser.add_argument("--think-time", type=float, default=1.0, help="max random pause between actions per user")
    parser.add_argument("--list-weight", type=int, default=55)
    parser.add_argument("--preflight-weight", type=int, default=15)
    parser.add_argument("--upload-weight", type=int, default=15)
    parser.add_argument("--chat-weight", type=int, default=15)
    return parser.parse_args()


if __name__ == "__main__":
    print_summary(run(parse_args()))
