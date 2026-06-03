#!/usr/bin/env python3
"""Tiny Elasticsearch-compatible HTTP service for local UI/RAG smoke tests."""

from __future__ import annotations

import json
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse


HOST = "127.0.0.1"
PORT = 9200
INDEX_NAME = "knowledge_base"
STORE_PATH = Path(os.environ.get("LOCAL_ES_STORE", "/tmp/test01-local-es-docs.json"))


def load_docs() -> dict[str, dict]:
    try:
        return json.loads(STORE_PATH.read_text("utf-8"))
    except Exception:
        return {}


def save_docs(docs: dict[str, dict]) -> None:
    STORE_PATH.write_text(json.dumps(docs, ensure_ascii=False), "utf-8")


def ok_response(payload: dict) -> bytes:
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    server_version = "LocalElasticsearch/8.10"

    def log_message(self, fmt: str, *args: object) -> None:
        return

    def _send(self, status: int, payload: dict | None = None, method: str | None = None) -> None:
        body = b"" if method == "HEAD" else ok_response(payload or {})
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-Elastic-Product", "Elasticsearch")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _read_json(self) -> dict:
        raw = self._read_body()
        try:
            return json.loads(raw)
        except Exception:
            return {}

    def _read_body(self) -> bytes:
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks: list[bytes] = []
            while True:
                size_line = self.rfile.readline().split(b";", 1)[0].strip()
                if not size_line:
                    break
                size = int(size_line, 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()
            return b"".join(chunks)

        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return b""
        return self.rfile.read(length)

    def do_HEAD(self) -> None:
        path = urlparse(self.path).path.strip("/")
        self._send(200 if path in ("", INDEX_NAME) else 404, method="HEAD")

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/":
            self._send(200, {
                "name": "local-dev-es",
                "cluster_name": "local-dev",
                "version": {"number": "8.10.4"},
                "tagline": "You Know, for Search",
            })
            return
        if path == "/_cluster/health":
            self._send(200, {
                "cluster_name": "local-dev",
                "status": "green",
                "timed_out": False,
                "number_of_nodes": 1,
                "number_of_data_nodes": 1,
                "active_shards": 1,
                "active_primary_shards": 1,
                "relocating_shards": 0,
                "initializing_shards": 0,
                "unassigned_shards": 0,
                "delayed_unassigned_shards": 0,
                "number_of_pending_tasks": 0,
                "number_of_in_flight_fetch": 0,
                "task_max_waiting_in_queue_millis": 0,
                "active_shards_percent_as_number": 100.0,
            })
            return
        if path.endswith("/_mapping"):
            self._send(200, {
                INDEX_NAME: {
                    "mappings": {
                        "properties": {
                            "vector": {"type": "dense_vector", "dims": 1024},
                            "knowledgeScope": {"type": "keyword"},
                            "departmentId": {"type": "keyword"},
                            "categoryId": {"type": "long"},
                            "categoryName": {"type": "keyword"},
                        }
                    }
                }
            })
            return
        if path.endswith("/_count"):
            self._send(200, {"count": len(load_docs()), "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0}})
            return
        self._send(404, {"error": {"reason": "not found"}})

    def do_PUT(self) -> None:
        path = urlparse(self.path).path
        if path.endswith("/_bulk") or path == "/_bulk":
            self._bulk()
            return
        self._send(200, {"acknowledged": True, "index": path.strip("/")})

    def do_DELETE(self) -> None:
        path = urlparse(self.path).path.strip("/")
        if path == INDEX_NAME:
            save_docs({})
            self._send(200, {"acknowledged": True})
            return
        self._send(200, {"acknowledged": True})

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        if path.endswith("/_bulk") or path == "/_bulk":
            self._bulk()
            return
        if path.endswith("/_search"):
            self._search()
            return
        if path.endswith("/_delete_by_query"):
            self._delete_by_query()
            return
        if path.endswith("/_count"):
            self._send(200, {"count": len(load_docs()), "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0}})
            return
        self._send(200, {"acknowledged": True})

    def _bulk(self) -> None:
        raw = self._read_body().decode("utf-8", errors="ignore")
        Path("/tmp/test01-local-es-last-bulk.ndjson").write_text(raw, "utf-8")
        docs = load_docs()
        items = []
        lines = [line for line in raw.splitlines() if line.strip()]
        for index in range(0, len(lines), 2):
            try:
                action = json.loads(lines[index])
                source = json.loads(lines[index + 1]) if index + 1 < len(lines) else {}
            except Exception:
                continue
            meta = action.get("index") or action.get("create") or {}
            doc_id = str(meta.get("_id") or source.get("id") or f"doc-{int(time.time() * 1000)}")
            source.setdefault("id", doc_id)
            docs[doc_id] = source
            items.append({"index": {"_index": meta.get("_index") or INDEX_NAME, "_id": doc_id, "status": 201}})
        save_docs(docs)
        self._send(200, {"took": 1, "errors": False, "items": items})

    def _search(self) -> None:
        request = self._read_json()
        size = int(request.get("size") or 10)
        docs = list(load_docs().values())
        query_text = json.dumps(request.get("query", {}), ensure_ascii=False)
        terms = [t.lower() for t in re.findall(r"[\w\u4e00-\u9fff]+", query_text) if len(t) > 1]

        def score(doc: dict) -> float:
            text = json.dumps(doc, ensure_ascii=False).lower()
            matches = sum(1 for term in terms if term in text)
            return 1.0 + matches

        ranked = sorted(docs, key=score, reverse=True)[:size]
        hits = [
            {
                "_index": INDEX_NAME,
                "_id": str(doc.get("id") or i),
                "_score": score(doc),
                "_source": {k: v for k, v in doc.items() if k != "vector"},
            }
            for i, doc in enumerate(ranked)
        ]
        self._send(200, {
            "took": 1,
            "timed_out": False,
            "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0},
            "hits": {
                "total": {"value": len(docs), "relation": "eq"},
                "max_score": hits[0]["_score"] if hits else None,
                "hits": hits,
            },
        })

    def _delete_by_query(self) -> None:
        request = self._read_json()
        payload = json.dumps(request, ensure_ascii=False)
        match = re.search(r'"fileMd5"\\s*:\\s*\\{[^{}]*"value"\\s*:\\s*"([^"]+)"', payload)
        docs = load_docs()
        deleted = 0
        if match:
            file_md5 = match.group(1)
            keep = {key: doc for key, doc in docs.items() if doc.get("fileMd5") != file_md5}
            deleted = len(docs) - len(keep)
            save_docs(keep)
        self._send(200, {"deleted": deleted, "failures": []})


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
