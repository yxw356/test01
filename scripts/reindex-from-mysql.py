#!/usr/bin/env python3
"""从 MySQL document_vectors 重建 Elasticsearch 索引（应急脚本）。"""
import json
import subprocess
import uuid
import urllib.request

ES = "http://localhost:9200/knowledge_base"
ES_AUTH = "elastic:PaiSmart2025"
EMBED_URL = "http://127.0.0.1:8001/v1/embeddings"
EMBED_KEY = "vllm_api_key_12345"
MYSQL = [
    "docker", "exec", "mysql", "mysql",
    "-uroot", "-pPaiSmart2025", "PaiSmart", "-N", "--default-character-set=utf8mb4",
]


def mysql_query(sql: str) -> str:
    p = subprocess.run(MYSQL + ["-e", sql], capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return p.stdout


def embed(texts: list[str]) -> list[list[float]]:
    body = json.dumps({
        "model": "bge-m3",
        "input": texts,
        "dimension": 1024,
        "encoding_format": "float",
    }).encode()
    req = urllib.request.Request(
        EMBED_URL,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {EMBED_KEY}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.load(resp)
    return [item["embedding"] for item in data["data"]]


def bulk_index(docs: list[dict]) -> None:
    lines = []
    for doc in docs:
        lines.append(json.dumps({"index": {"_index": "knowledge_base", "_id": doc["id"]}}))
        lines.append(json.dumps(doc))
    payload = "\n".join(lines) + "\n"
    req = urllib.request.Request(
        f"{ES}/_bulk",
        data=payload.encode(),
        headers={"Content-Type": "application/x-ndjson"},
        method="POST",
    )
    import base64
    auth = base64.b64encode(ES_AUTH.encode()).decode()
    req.add_header("Authorization", f"Basic {auth}")
    with urllib.request.urlopen(req, timeout=120) as resp:
        result = json.load(resp)
    if result.get("errors"):
        raise RuntimeError(json.dumps(result, ensure_ascii=False)[:2000])


def main() -> None:
    rows = mysql_query(
        "SELECT dv.file_md5, dv.chunk_id, dv.parent_id, dv.text_content, "
        "dv.parent_text_content, fu.user_id, fu.org_tag, fu.is_public "
        "FROM document_vectors dv "
        "JOIN file_upload fu ON fu.file_md5 = dv.file_md5"
    ).strip().splitlines()
    if not rows:
        print("无分块数据")
        return

    batch_texts = []
    batch_meta = []
    total = 0
    for line in rows:
        parts = line.split("\t")
        if len(parts) < 8:
            continue
        file_md5, chunk_id, parent_id, text, parent_text, user_id, org_tag, is_public = parts[:8]
        batch_texts.append(text)
        batch_meta.append({
            "file_md5": file_md5,
            "chunk_id": int(chunk_id),
            "parent_id": parent_id or "",
            "text_content": text,
            "parent_text_content": parent_text or "",
            "user_id": user_id,
            "org_tag": org_tag,
            "is_public": is_public == "\x01" or is_public == "1",
        })
        if len(batch_texts) >= 10:
            vectors = embed(batch_texts)
            docs = []
            for meta, vector in zip(batch_meta, vectors):
                docs.append({
                    "id": str(uuid.uuid4()),
                    "fileMd5": meta["file_md5"],
                    "chunkId": meta["chunk_id"],
                    "parentId": meta["parent_id"],
                    "textContent": meta["text_content"],
                    "parentTextContent": meta["parent_text_content"],
                    "vector": vector,
                    "modelVersion": "bge-m3",
                    "userId": meta["user_id"],
                    "orgTag": meta["org_tag"],
                    "isPublic": meta["is_public"],
                })
            bulk_index(docs)
            total += len(docs)
            batch_texts, batch_meta = [], []
            print(f"indexed {total} chunks...")

    if batch_texts:
        vectors = embed(batch_texts)
        docs = []
        for meta, vector in zip(batch_meta, vectors):
            docs.append({
                "id": str(uuid.uuid4()),
                "fileMd5": meta["file_md5"],
                "chunkId": meta["chunk_id"],
                "parentId": meta["parent_id"],
                "textContent": meta["text_content"],
                "parentTextContent": meta["parent_text_content"],
                "vector": vector,
                "modelVersion": "bge-m3",
                "userId": meta["user_id"],
                "orgTag": meta["org_tag"],
                "isPublic": meta["is_public"],
            })
        bulk_index(docs)
        total += len(docs)

    count = urllib.request.urlopen(
        urllib.request.Request(
            f"{ES}/_count",
            headers={"Authorization": f"Basic {__import__('base64').b64encode(ES_AUTH.encode()).decode()}"},
        )
    )
    print("done, es count:", json.load(count))


if __name__ == "__main__":
    main()
