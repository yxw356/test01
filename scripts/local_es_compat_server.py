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


def tokenize(text: str) -> list[str]:
    raw_terms = re.findall(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]+", text.lower())
    terms: list[str] = []
    for term in raw_terms:
        if len(term) <= 1:
            continue
        terms.append(term)
        if re.fullmatch(r"[\u4e00-\u9fff]+", term) and len(term) > 2:
            terms.extend(term[index:index + 2] for index in range(len(term) - 1))
    return terms


def extract_text_query_terms(request: dict) -> list[str]:
    queries: list[str] = []

    def visit(node: object) -> None:
        if isinstance(node, dict):
            match = node.get("match")
            if isinstance(match, dict) and "textContent" in match:
                value = match["textContent"]
                if isinstance(value, dict):
                    query = value.get("query")
                else:
                    query = value
                if query is not None:
                    queries.append(str(query))
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for item in node:
                visit(item)

    visit(request.get("query", {}))
    terms: list[str] = []
    for query in queries:
        terms.extend(tokenize(query))
    return dedupe(terms)


def extract_filter(request: dict) -> object:
    filters: list[object] = []

    def visit(node: object) -> None:
        if isinstance(node, dict):
            bool_node = node.get("bool")
            if isinstance(bool_node, dict) and "filter" in bool_node:
                filter_value = bool_node["filter"]
                if isinstance(filter_value, list):
                    filters.extend(filter_value)
                else:
                    filters.append(filter_value)
            knn_node = node.get("knn")
            if isinstance(knn_node, dict) and "filter" in knn_node:
                filters.append(knn_node["filter"])
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for item in node:
                visit(item)

    visit(request)
    if not filters:
        return None
    if len(filters) == 1:
        return filters[0]
    return {"bool": {"must": filters}}


def matches_filter(doc: dict, filter_node: object) -> bool:
    if not filter_node:
        return True
    if isinstance(filter_node, list):
        return all(matches_filter(doc, item) for item in filter_node)
    if not isinstance(filter_node, dict):
        return True
    if "term" in filter_node:
        term = filter_node["term"]
        if not isinstance(term, dict):
            return True
        for field, expected in term.items():
            value = expected.get("value") if isinstance(expected, dict) else expected
            return normalize_value(doc.get(field)) == normalize_value(value)
    if "match_all" in filter_node:
        return True
    if "bool" in filter_node:
        bool_node = filter_node["bool"]
        must = bool_node.get("must", [])
        should = bool_node.get("should", [])
        filters = bool_node.get("filter", [])
        if isinstance(must, dict):
            must = [must]
        if isinstance(should, dict):
            should = [should]
        if isinstance(filters, dict):
            filters = [filters]
        if not all(matches_filter(doc, item) for item in must):
            return False
        if not all(matches_filter(doc, item) for item in filters):
            return False
        min_should = int(bool_node.get("minimum_should_match") or (1 if should else 0))
        should_matches = sum(1 for item in should if matches_filter(doc, item))
        return should_matches >= min_should
    return True


def normalize_value(value: object) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value).lower()


def score_doc(doc: dict, query_terms: list[str]) -> float:
    text = " ".join(str(doc.get(field) or "") for field in ("textContent", "parentTextContent", "fileName")).lower()
    if not query_terms:
        return 1.0
    score = 0.0
    for term in query_terms:
        if term in text:
            score += 4.0 if len(term) > 2 else 1.0
    return score


def dedupe(items: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


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
        if request.get("knn") and not request.get("query"):
            self._send(200, {
                "took": 1,
                "timed_out": False,
                "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0},
                "hits": {"total": {"value": 0, "relation": "eq"}, "max_score": None, "hits": []},
            })
            return

        query_terms = extract_text_query_terms(request)
        filter_node = extract_filter(request)
        permitted_docs = [doc for doc in docs if matches_filter(doc, filter_node)]
        scored = [(doc, score_doc(doc, query_terms)) for doc in permitted_docs]
        if query_terms:
            scored = [(doc, score) for doc, score in scored if score > 0]
        ranked_pairs = sorted(scored, key=lambda item: item[1], reverse=True)[:size]
        hits = [
            {
                "_index": INDEX_NAME,
                "_id": str(doc.get("id") or i),
                "_score": score,
                "_source": {k: v for k, v in doc.items() if k != "vector"},
            }
            for i, (doc, score) in enumerate(ranked_pairs)
        ]
        self._send(200, {
            "took": 1,
            "timed_out": False,
            "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0},
            "hits": {
                "total": {"value": len(scored), "relation": "eq"},
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
