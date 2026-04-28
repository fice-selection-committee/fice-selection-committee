# STEP-03 — MinIO Download Adapter (Python)

**Status**: ⏳ TODO
**Depends on**: STEP-01
**Blocks**: STEP-08

## Goal

Implement an async MinIO/S3 download adapter that streams objects to disk-backed temp files (so we don't hold a 50MB scan in RAM), handles missing-object and transient-error cases distinctly, and matches the Java side's `S3Properties` env-var naming for operational consistency.

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/storage/
├── __init__.py
├── minio_client.py         async wrapper around minio-py
├── exceptions.py           ObjectNotFoundError, StorageTransientError, StorageError (base)
└── models.py               DownloadedObject(path, size, content_type, etag)
```

```
tests/storage/
├── __init__.py
├── conftest.py             testcontainers MinIO fixture (session-scoped)
└── test_minio_client.py
```

## Implementation Outline

1. **`exceptions.py`** — three classes; `StorageTransientError` extends `StorageError`; `ObjectNotFoundError` is terminal (do not retry).
2. **`minio_client.py`** — `MinioStorageClient`:
   - Constructor: takes `endpoint`, `access_key`, `secret_key`, `bucket`, `secure: bool`.
   - `async def download(key: str) -> DownloadedObject` — uses `minio.Minio.fget_object` (sync) wrapped in `asyncio.to_thread`. Maps `S3Error.code in {"NoSuchKey","NoSuchBucket"}` → `ObjectNotFoundError`. Maps connection errors / 5xx → `StorageTransientError`.
   - Streaming via `fget_object` writes directly to disk (no buffer load).
   - Returns a `DownloadedObject(NamedTemporaryFile path, size, etag, content_type)`.
   - `__aenter__/__aexit__` — no resource pooling needed for minio-py; just clean tempfiles on close.
3. Env-var naming mirrors Java: `CV_MINIO_ENDPOINT`, `CV_MINIO_ACCESS_KEY` (=`MINIO_ROOT_USER`), `CV_MINIO_SECRET_KEY`, `CV_MINIO_BUCKET`, `CV_MINIO_SECURE` (default `false` for local).
4. Internal vs external endpoint: CV-Service is **always** running inside the docker network → uses `CV_MINIO_ENDPOINT_INTERNAL` (default `http://minio:9000`). No external endpoint needed.

## Tests (Acceptance Gates)

Use `testcontainers-python` to spin up a real MinIO container per test session.

- [ ] **Happy path**: Upload `tests/fixtures/passport_clean.png` to MinIO via the test fixture's admin client. Call `client.download(key)`. Assert: returned `path` exists, `size` matches uploaded bytes, sha256 of downloaded file matches sha256 of original.
- [ ] **Missing object**: Call `client.download("does/not/exist.png")` → assert raises `ObjectNotFoundError`.
- [ ] **Bucket missing**: Use a non-existent bucket → assert raises `ObjectNotFoundError`.
- [ ] **Transient error**: Patch the underlying SDK to raise `urllib3.exceptions.MaxRetryError` → assert wrapped as `StorageTransientError`.
- [ ] **Streaming RAM bound**: Generate a 10MB file in-memory, upload, then download with `tracemalloc` snapshot before/after. Assert peak allocated bytes during `download()` stays below 2 MB (proves streaming, not buffering).
- [ ] **Concurrent downloads**: Run 10 `download()` calls via `asyncio.gather` → all succeed; no race on tempfile names.

## Definition of Done

- [ ] All files created
- [ ] All 6 tests pass
- [ ] `ruff` + `mypy --strict` clean
- [ ] `progress/README.md` STEP-03 row marked ✅

## Notes

- **Do NOT use `boto3`** — bigger dep, slower startup, and minio-py is the project's de facto pattern.
- **Do NOT add presigned-URL support** — that's a Java-side concern (documents-service). CV uses direct (server-side) download with service credentials.
