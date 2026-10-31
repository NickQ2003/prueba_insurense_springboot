import datetime
import calendar
import json
import os
import time
import urllib.error
import urllib.request
import uuid


BASE_URL = os.environ.get("E2E_BASE_URL", "http://localhost:8080")
API_KEY = os.environ.get("API_KEY", "123456")
WAIT_SECONDS = int(os.environ.get("E2E_WAIT_SECONDS", "240"))
RUN_ID = uuid.uuid4().hex[:10]


def request(method, path, body=None, idempotency_key=None):
    headers = {"x-api-key": API_KEY, "Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key
    http_request = urllib.request.Request(
        BASE_URL + path, data=data, headers=headers, method=method
    )
    try:
        with urllib.request.urlopen(http_request, timeout=30) as response:
            payload = response.read()
            return (
                response.status,
                json.loads(payload) if payload else None,
                response.headers,
            )
    except urllib.error.HTTPError as error:
        raise RuntimeError(
            f"{method} {path} returned {error.code}: {error.read().decode()}"
        ) from error


def create_party(display_name, document_reference):
    _, response, _ = request(
        "POST",
        "/api/v1/parties",
        {
            "party_type": "PERSON",
            "display_name": display_name,
            "document_reference": document_reference,
        },
    )
    return response["id"]


def plus_months(value, months):
    month_index = value.month - 1 + months
    year = value.year + month_index // 12
    month = month_index % 12 + 1
    day = min(value.day, calendar.monthrange(year, month)[1])
    return datetime.date(year, month, day)


def issue_policy(policy_type, holder_id, landlord_id, starts_on, risks, key):
    status, operation, headers = request(
        "POST",
        "/api/v1/policies",
        {
            "policy_type": policy_type,
            "holder_party_id": holder_id,
            "starts_on": starts_on.isoformat(),
            "duration_months": 12,
            "risks": [
                {
                    "property_address": risk[0],
                    "tenant_party_id": risk[1],
                    "landlord_party_id": landlord_id,
                    "monthly_rent": risk[2],
                }
                for risk in risks
            ],
        },
        key,
    )
    assert status == 202, status
    assert headers.get("Location", "").endswith(operation["id"]), headers
    return operation["id"]


def wait_for_success(operation_id):
    deadline = time.monotonic() + WAIT_SECONDS
    while time.monotonic() < deadline:
        _, operation, _ = request("GET", f"/api/v1/operations/{operation_id}")
        if operation["status"] == "SUCCEEDED":
            return
        if operation["status"] in {"REJECTED", "NEEDS_REVIEW"}:
            raise RuntimeError(
                f"operation {operation_id} ended as {operation['status']}"
            )
        time.sleep(2)
    raise TimeoutError(f"operation {operation_id} did not succeed")


def verify_cancelled_policy(policy_id):
    _, policy, _ = request("GET", f"/api/v1/policies/{policy_id}")
    assert policy["status"] == "CANCELLED", policy
    assert all(risk["status"] == "CANCELLED" for risk in policy["risks"]), policy


def main():
    _, health, _ = request("GET", "/health/ready")
    assert health["status"] == "UP", health
    today = datetime.datetime.now(datetime.timezone.utc).date()
    holder_id = create_party("E2E synthetic collective holder", f"E2E-HOLDER-{RUN_ID}")
    landlord_id = create_party("E2E synthetic landlord", f"E2E-LANDLORD-{RUN_ID}")

    issue_id = issue_policy(
        "COLLECTIVE",
        holder_id,
        landlord_id,
        today,
        [
            ("E2E synthetic property one", holder_id, "1000000.00"),
            ("E2E synthetic property two", holder_id, "1200000.00"),
        ],
        f"e2e-{RUN_ID}-issue",
    )
    wait_for_success(issue_id)
    _, policies, _ = request("GET", "/api/v1/policies?limit=100&offset=0")
    policy_id = next(
        policy["id"] for policy in policies["items"] if policy["holder_party_id"] == holder_id
    )
    print("policy issue succeeded")

    add_key = f"e2e-{RUN_ID}-add-risk"
    add_request = {
        "property_address": "E2E synthetic property three",
        "tenant_party_id": holder_id,
        "landlord_party_id": landlord_id,
        "monthly_rent": "2500000.00",
    }
    risk_operation_id = request(
        "POST", f"/api/v1/policies/{policy_id}/risks", add_request, add_key
    )[1]["id"]
    replay_id = request(
        "POST", f"/api/v1/policies/{policy_id}/risks", add_request, add_key
    )[1]["id"]
    assert replay_id == risk_operation_id
    wait_for_success(risk_operation_id)
    _, risks, _ = request("GET", f"/api/v1/policies/{policy_id}/risks")
    assert len(risks) == 3, risks
    _, spanish_risks, _ = request("GET", f"/polizas/{policy_id}/riesgos")
    assert len(spanish_risks) == 3, spanish_risks
    added_risk = next(risk for risk in risks if risk["risk_number"] == "R3")
    print("risk addition and idempotent replay succeeded")

    cancel_risk_id = request(
        "POST",
        f"/api/v1/policies/{policy_id}/risks/{added_risk['id']}/cancellations",
        {},
        f"e2e-{RUN_ID}-cancel-risk",
    )[1]["id"]
    wait_for_success(cancel_risk_id)
    _, risks, _ = request("GET", f"/api/v1/policies/{policy_id}/risks")
    assert next(risk for risk in risks if risk["id"] == added_risk["id"])["status"] == "CANCELLED"
    print("risk cancellation succeeded")

    waiting_holder_id = create_party(
        "E2E synthetic waiting-renewal holder", f"E2E-WAITING-HOLDER-{RUN_ID}"
    )
    waiting_start = today - datetime.timedelta(days=400)
    waiting_renewal_starts_on = plus_months(waiting_start, 12)
    waiting_issue_id = issue_policy(
        "INDIVIDUAL",
        waiting_holder_id,
        landlord_id,
        waiting_start,
        [("E2E synthetic waiting-renewal property", waiting_holder_id, "800000.00")],
        f"e2e-{RUN_ID}-waiting-issue",
    )
    wait_for_success(waiting_issue_id)
    _, policies, _ = request("GET", "/api/v1/policies?limit=100&offset=0")
    waiting_policy_id = next(
        policy["id"]
        for policy in policies["items"]
        if policy["holder_party_id"] == waiting_holder_id
    )
    waiting_renewal_id = request(
        "POST",
        f"/api/v1/policies/{waiting_policy_id}/renewals",
        {},
        f"e2e-{RUN_ID}-waiting-renewal",
    )[1]["id"]
    _, waiting_operation, _ = request(
        "GET", f"/api/v1/operations/{waiting_renewal_id}"
    )
    assert waiting_operation["status"] == "WAITING_INPUT", waiting_operation

    _, ipc_rate, _ = request(
        "POST",
        "/api/v1/ipc-rates",
        {
            "reference_period": f"E2E-{RUN_ID}",
            "effective_on": (waiting_renewal_starts_on - datetime.timedelta(days=1)).isoformat(),
            "rate": "0.08000",
            "source": "E2E synthetic rate",
            "is_fictional": True,
        },
    )
    resumed = request(
        "POST",
        f"/api/v1/operations/{waiting_renewal_id}/resume",
        {"ipc_rate_id": ipc_rate["id"]},
    )[1]
    assert resumed["status"] == "PENDING", resumed
    wait_for_success(waiting_renewal_id)
    _, waiting_periods, _ = request(
        "GET", f"/api/v1/policies/{waiting_policy_id}/periods"
    )
    assert [period["status"] for period in waiting_periods] == ["EXPIRED", "CURRENT"]
    print("IPC wait, rate creation, and renewal resume succeeded")

    renewal_id = request(
        "POST",
        f"/api/v1/policies/{policy_id}/renewals",
        {"ipc_rate_id": ipc_rate["id"]},
        f"e2e-{RUN_ID}-renewal",
    )[1]["id"]
    wait_for_success(renewal_id)
    _, periods, _ = request("GET", f"/api/v1/policies/{policy_id}/periods")
    assert [period["status"] for period in periods] == ["EXPIRED", "CURRENT"], periods
    assert periods[-1]["ipc_rate_snapshot"] == "0.08000", periods[-1]
    print("IPC renewal snapshot succeeded")

    for target_policy_id, key in (
        (waiting_policy_id, "waiting-policy"),
        (policy_id, "collective-policy"),
    ):
        cancellation_id = request(
            "POST",
            f"/api/v1/policies/{target_policy_id}/cancellations",
            {},
            f"e2e-{RUN_ID}-cancel-{key}",
        )[1]["id"]
        wait_for_success(cancellation_id)
        verify_cancelled_policy(target_policy_id)
    print("policy cancellation and risk state transitions succeeded")

    status, _, _ = request(
        "POST",
        "/core-mock/evento",
        {"evento": "ACTUALIZACION", "polizaId": 555},
    )
    assert status == 204, status
    print("gateway health and CORE compatibility route succeeded")


if __name__ == "__main__":
    main()
