"""M6 路由正式入口与错误映射集成测试。"""

import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import AsyncMock

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from landppt.m6 import router as routerModule
from landppt.m6.schemas import MAX_INT64
from tests.m6.conftest import makeEvent, makeRequest


@pytest.fixture
def client() -> TestClient:
  """创建仅挂载 M6 路由的真实 HTTP 测试客户端。"""
  app = FastAPI()
  app.include_router(routerModule.router)
  return TestClient(app)


def test_valid_request_returns_200(client: TestClient, caplog: pytest.LogCaptureFixture) -> None:
  caplog.set_level("INFO", logger="landppt.m6.router")
  response = client.post("/api/internal/ai/build-profile", json=makeRequest([]))

  assert response.status_code == 200
  assert response.json()["status"] == "NO_CHANGE"
  assert "12345678-1234-5678-1234-567812345678" in caplog.text
  assert "m6-profile-v1.0.0" in caplog.text
  assert "events" not in caplog.text


def test_formal_post_uses_named_typed_handler_and_omits_none_fields() -> None:
  route = next(
    route
    for route in routerModule.router.routes
    if route.path == "/api/internal/ai/build-profile"
  )

  assert route.endpoint.__name__ == "buildProfileRoute"
  assert route.endpoint.__annotations__["return"] == routerModule.BuildProfileResponse
  assert route.response_model_exclude_none is True


def test_invalid_contract_version_returns_400(client: TestClient) -> None:
  response = client.post(
    "/api/internal/ai/build-profile",
    json=makeRequest([], contractVersion="2.0"),
  )

  assert response.status_code == 400
  assert response.json() == {"detail": "画像请求不符合v1.0契约"}


@pytest.mark.parametrize("path", ["/api/review/calculate-all", "/api/events/process"])
def test_legacy_post_routes_return_404(client: TestClient, path: str) -> None:
  assert client.post(path).status_code == 404


def test_two_existing_get_routes_are_preserved() -> None:
  getPaths = {
    route.path
    for route in routerModule.router.routes
    if "GET" in getattr(route, "methods", set())
  }

  assert getPaths == {
    "/api/user-profile/{userId}/knowledge-status",
    "/api/user-profile/{userId}/timeline",
  }


def test_engine_unknown_exception_returns_503(
  client: TestClient,
  monkeypatch: pytest.MonkeyPatch,
) -> None:
  engine = AsyncMock(side_effect=RuntimeError("内部细节不得泄漏"))
  monkeypatch.setattr(routerModule, "buildProfile", engine)

  response = client.post("/api/internal/ai/build-profile", json=makeRequest([]))

  assert response.status_code == 503
  assert response.json() == {"detail": "画像引擎暂时不可用"}


def test_profile_version_overflow_returns_400(client: TestClient) -> None:
  event = makeEvent(occurredAt=datetime.now(timezone.utc).isoformat())
  response = client.post(
    "/api/internal/ai/build-profile",
    json=makeRequest([event], baseProfileVersion=MAX_INT64),
  )

  assert response.status_code == 400
  assert response.json() == {"detail": "画像版本已达到上限，无法继续推进"}


def test_post_router_import_does_not_load_database_driver() -> None:
  projectRoot = Path(__file__).parents[2]
  environment = {**os.environ, "PYTHONPATH": str(projectRoot / "src")}
  command = (
    "import sys; import landppt.m6.router; "
    "assert 'landppt.m6.db' not in sys.modules; assert 'pymysql' not in sys.modules"
  )

  result = subprocess.run(
    [sys.executable, "-c", command],
    cwd=projectRoot,
    env=environment,
    capture_output=True,
    text=True,
    check=False,
  )

  assert result.returncode == 0, result.stderr
