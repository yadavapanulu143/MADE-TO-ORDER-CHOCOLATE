#!/usr/bin/env python3
"""End-to-end customer journey against a running chocolate stack.

Drives the whole flow through the API gateway (never a service port directly):
register -> verify email + mobile -> sign in -> address -> cart -> quote -> place
order -> payment -> verified capture -> production -> delivery -> notification ->
invoice -> admin reports and exports. Every stage asserts the response the platform
is meant to produce, so a regression in any service shows up as a failed step.

The stack must be up and healthy:

    docker compose -f docker-compose.yml -f docker-compose.app.yml up -d

Two OTP sources are read. The email code comes from Mailpit. The mobile code comes
from the identity service log, which requires ``CHOCO_LOG_OTP=true`` because there is
no SMS provider in development. Start the identity container with that flag:

    CHOCO_LOG_OTP=true docker compose -f docker-compose.yml -f docker-compose.app.yml \\
        up -d identity-service

Razorpay credentials are not configured in the sandbox, so a real capture cannot
happen here. The payment service refuses to create a provider order, which this script
asserts as fail-closed behaviour. The order -> production saga is then driven by
publishing the *same* verified ``payment.captured`` event the payment service publishes
after it has confirmed a capture with the provider; the capture path itself is covered
by ``PaymentJourneyIT`` with real HMAC signatures. Pass ``--skip-simulated-capture`` to
stop before that step.

The seller-driven stages need an administrator. Provide:

    E2E_ADMIN_EMAIL, E2E_ADMIN_PASSWORD

On a fresh stack, bootstrap one first (the bootstrap only ever creates, so clear these
afterwards):

    CHOCO_ADMIN_BOOTSTRAP_EMAIL=e2e-admin@choco.test \\
    CHOCO_ADMIN_BOOTSTRAP_PASSWORD='E2e!AdminPass123' \\
    CHOCO_LOG_OTP=true \\
    docker compose -f docker-compose.yml -f docker-compose.app.yml up -d identity-service

If the credentials are absent the seller section is reported as SKIPPED rather than
silently passing. Exit code is 0 only when every run step passed.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

GATEWAY = os.environ.get("E2E_GATEWAY", "http://localhost:8080")
MAILPIT = os.environ.get("E2E_MAILPIT", "http://localhost:8025")
RABBIT_API = os.environ.get("E2E_RABBIT_API", "http://localhost:15672")
RABBIT_USER = os.environ.get("E2E_RABBIT_USER", "choco")
RABBIT_PASSWORD = os.environ.get("E2E_RABBIT_PASSWORD", "choco_rabbit_pw")
IDENTITY_CONTAINER = os.environ.get("E2E_IDENTITY_CONTAINER", "choco-identity")
ADMIN_EMAIL = os.environ.get("E2E_ADMIN_EMAIL", "")
ADMIN_PASSWORD = os.environ.get("E2E_ADMIN_PASSWORD", "")

PASSWORD = "Choco!Pass123"
PASSED: list[str] = []
FAILED: list[str] = []
SKIPPED: list[str] = []


class StepError(RuntimeError):
    """A step produced a response the platform is not meant to produce."""


def check(condition: bool, message: str) -> None:
    if not condition:
        raise StepError(message)


def http(method: str, path: str, token: str | None = None, body=None,
         headers: dict | None = None) -> tuple[int, object, dict]:
    """One request against the gateway. Returns (status, parsed body, headers)."""
    data = None
    request_headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(GATEWAY + path, data=data, method=method,
                                     headers=request_headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, _parse(response.read(), response.headers), dict(response.headers)
    except urllib.error.HTTPError as error:
        return error.code, _parse(error.read(), error.headers), dict(error.headers)


def _header(headers: dict, name: str) -> str:
    """Case-insensitive header lookup; urllib normalises the case inconsistently."""
    wanted = name.lower()
    for key, value in headers.items():
        if key.lower() == wanted:
            return value
    return ""


def _parse(raw: bytes, headers) -> object:
    content_type = _header(headers, "Content-Type")
    if "json" in content_type:
        try:
            return json.loads(raw.decode())
        except json.JSONDecodeError:
            return raw.decode(errors="replace")
    if raw:
        try:
            return raw.decode()
        except UnicodeDecodeError:
            return raw
    return {}


def wait_for_gateway(timeout: int = 90) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            status, _, _ = http("GET", "/actuator/health")
            if status == 200:
                return
        except Exception:  # noqa: BLE001 - the gateway may not be listening yet
            pass
        time.sleep(2)
    raise SystemExit(f"The gateway did not become healthy at {GATEWAY}")


def mailpit_codes(email: str) -> list[str]:
    """Every 6-digit code Mailpit has received for this address."""
    query = urllib.parse.quote(f"to:{email}")
    with urllib.request.urlopen(f"{MAILPIT}/api/v1/search?query={query}", timeout=15) as response:
        listing = json.load(response)
    codes: list[str] = []
    for message in listing.get("messages", []):
        with urllib.request.urlopen(f"{MAILPIT}/api/v1/message/{message['ID']}", timeout=15) as body:
            detail = json.load(body)
        text = " ".join(str(detail.get(field) or "") for field in ("Subject", "Text", "HTML"))
        codes.extend(re.findall(r"\b(\d{6})\b", text))
    return codes


def log_code(destination_tail: str, channel: str) -> str | None:
    """The newest verification code the identity service logged for a destination."""
    try:
        completed = subprocess.run(["docker", "logs", IDENTITY_CONTAINER],
                                   capture_output=True, text=True, timeout=30)
        logs = completed.stdout + completed.stderr
    except (OSError, subprocess.SubprocessError):
        return None
    pattern = re.compile(rf"Verification code for \*+{re.escape(destination_tail)} "
                         rf"\({channel}\) is (\d{{6}})")
    matches = pattern.findall(logs)
    return matches[-1] if matches else None


def publish_event(routing_key: str, payload: dict, producer: str) -> bool:
    """Publish one event to the topic exchange exactly as the outbox relay would."""
    envelope = {
        "eventId": str(uuid.uuid4()),
        "eventType": routing_key,
        "schemaVersion": 1,
        "aggregateId": payload.get("orderId") or payload.get("customerId") or "",
        "occurredAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "correlationId": str(uuid.uuid4()),
        "producer": producer,
        "payload": payload,
    }
    body = json.dumps({
        "properties": {"content_type": "application/json",
                       "headers": {"eventType": routing_key}},
        "routing_key": routing_key,
        "payload": json.dumps(envelope),
        "payload_encoding": "string",
    }).encode()
    token = base64.b64encode(f"{RABBIT_USER}:{RABBIT_PASSWORD}".encode()).decode()
    request = urllib.request.Request(
        f"{RABBIT_API}/api/exchanges/%2F/choco.events/publish",
        data=body, method="POST",
        headers={"Content-Type": "application/json", "Authorization": f"Basic {token}"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response).get("routed") is True


class Runner:
    """Records each step's outcome so one failure does not hide the rest."""

    def run(self, name: str, function) -> None:
        try:
            function()
            PASSED.append(name)
            print(f"  PASS  {name}")
        except StepError as error:
            FAILED.append(f"{name}: {error}")
            print(f"  FAIL  {name}: {error}")
        except Exception as error:  # noqa: BLE001 - a step must not kill the run
            FAILED.append(f"{name}: unexpected {type(error).__name__}: {error}")
            print(f"  FAIL  {name}: unexpected {type(error).__name__}: {error}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the end-to-end chocolate journey.")
    parser.add_argument("--skip-simulated-capture", action="store_true",
                        help="stop after asserting the payment step fails closed")
    parser.add_argument("--skip-seller-stages", action="store_true",
                        help="stop after the customer journey and the saga hand-over")
    arguments = parser.parse_args()

    print(f"End-to-end journey against {GATEWAY}\n")
    wait_for_gateway()
    print("  PASS  the gateway is healthy\n")
    PASSED.append("the gateway is healthy")

    runner = Runner()
    stamp = int(time.time())
    email = f"e2e-{stamp}@choco.test"
    mobile = f"9{str(stamp)[-9:]}"
    state: dict = {"customer_id": "", "token": "", "address_id": "", "variant_id": "",
                   "order_id": "", "order_total": 0, "jobs": [], "admin_token": ""}

    def admin_login():
        """Sign the seller in once. Needed both to seed a fresh stack and for the seller stages."""
        status, body, _ = http("POST", "/api/v1/auth/admin/login",
                               body={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
        check(status == 200, f"admin sign-in -> {status}: {body}")
        state["admin_token"] = body["tokens"]["accessToken"]

    def require_admin():
        check(bool(ADMIN_EMAIL and ADMIN_PASSWORD),
              "this step needs a seller account: set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD")
        if not state["admin_token"]:
            admin_login()

    # ------------------------------------------------------------------ customer --

    def register():
        status, body, _ = http("POST", "/api/v1/auth/register", body={
            "fullName": "E2E Customer", "email": email, "mobile": mobile, "password": PASSWORD})
        check(status == 202, f"expected 202, got {status}: {body}")
        check(body["email"] == email, "the registration did not echo the email")
        state["customer_id"] = body["customerId"]

    def duplicate_registration():
        status, _, _ = http("POST", "/api/v1/auth/register", body={
            "fullName": "E2E Customer", "email": email, "mobile": mobile, "password": PASSWORD})
        check(status in (409, 422), f"expected a conflict, got {status}")

    def email_code_verifies():
        time.sleep(3)
        code = next(iter(mailpit_codes(email)), None)
        check(code is not None, "no verification email reached Mailpit")
        status, body, _ = http(
            "POST", f"/api/v1/auth/customers/{state['customer_id']}/verifications?channel=EMAIL",
            body={"code": code})
        check(status == 204, f"expected 204, got {status}: {body}")

    def a_wrong_code_is_refused():
        status, _, _ = http(
            "POST", f"/api/v1/auth/customers/{state['customer_id']}/verifications?channel=EMAIL",
            body={"code": "000000"})
        check(status in (400, 409, 422, 429), f"expected a refusal, got {status}")

    def mobile_code_verifies():
        code = log_code(mobile[-4:], "sms")
        if code is None:
            http("POST", f"/api/v1/auth/customers/{state['customer_id']}/verifications/resend",
                 body={"channel": "MOBILE"})
            time.sleep(4)
            code = log_code(mobile[-4:], "sms")
        check(code is not None,
              "no mobile code in the identity log; start the container with CHOCO_LOG_OTP=true")
        status, body, _ = http(
            "POST", f"/api/v1/auth/customers/{state['customer_id']}/verifications?channel=MOBILE",
            body={"code": code})
        check(status == 204, f"expected 204, got {status}: {body}")

    def sign_in():
        status, body, _ = http("POST", "/api/v1/auth/login",
                               body={"identifier": email, "password": PASSWORD})
        check(status == 200, f"expected 200, got {status}: {body}")
        state["token"] = body["tokens"]["accessToken"]
        status, profile, _ = http("GET", "/api/v1/customers/me", token=state["token"])
        check(status == 200, f"expected 200, got {status}")
        check(profile["fullyVerified"] is True, "the customer is not fully verified")

    def wrong_password_refused():
        status, _, _ = http("POST", "/api/v1/auth/login",
                            body={"identifier": email, "password": "NotThePassword1!"})
        check(status == 401, f"expected 401, got {status}")

    def add_address():
        status, body, _ = http("POST", "/api/v1/customers/me/addresses", token=state["token"], body={
            "recipientName": "E2E Customer", "mobile": mobile, "line1": "12 Cocoa Lane",
            "line2": "Indiranagar", "city": "Bengaluru", "state": "Karnataka",
            "pincode": "560038", "deliveryInstructions": "Ring the bell"})
        check(status == 201, f"expected 201, got {status}: {body}")
        state["address_id"] = body["id"]

    def catalogue():
        status, listing, _ = http("GET", "/api/v1/products?size=5")
        check(status == 200, f"expected 200, got {status}")
        if not listing["content"]:
            # A fresh volume has no catalogue. Seed one through the seller API so the journey runs
            # end to end on a clean stack (which is what CI starts from) instead of assuming a
            # product was created by hand first.
            seed_catalogue()
            status, listing, _ = http("GET", "/api/v1/products?size=5")
            check(status == 200, f"expected 200, got {status}")
            check(listing["content"], "the catalogue is still empty after seeding")
        slug = listing["content"][0]["slug"]
        status, product, _ = http("GET", f"/api/v1/products/{slug}")
        check(status == 200, f"expected 200, got {status}")
        active = [v for v in product["variants"] if v["active"]]
        check(active, "no active variant to buy")
        state["variant_id"] = active[0]["id"]

    def seed_catalogue():
        require_admin()
        # The name carries the run stamp: the catalogue is only seeded when it is empty, but a
        # previous failed run can leave an unpublished draft behind, and the product name has to be
        # unique. A stamped name never collides, so a rerun against a warm volume still works.
        status, product, _ = http("POST", "/api/v1/admin/catalog/products",
                                  token=state["admin_token"],
                                  body={"name": f"Single-Origin Dark Bar {stamp}",
                                        "shortDescription": "70% cocoa",
                                        "description": "Rich and dark, made to order",
                                        "preparationInfo": "Made fresh after payment",
                                        "deliveryInfo": "Delivered by the seller"})
        check(status == 201, f"seeding a product -> {status}: {product}")
        product_id = product["id"]
        status, _, _ = http("POST", f"/api/v1/admin/catalog/products/{product_id}/variants",
                            token=state["admin_token"],
                            body={"label": "200 g", "sku": f"E2E-{uuid.uuid4().hex[:8]}",
                                  "price": "450.00", "minOrderQuantity": 1, "maxOrderQuantity": 10})
        check(status == 200, f"seeding a variant -> {status}")
        status, _, _ = http("POST", f"/api/v1/admin/catalog/products/{product_id}/publish",
                            token=state["admin_token"])
        check(status == 200, f"publishing the seeded product -> {status}")

    def quote():
        # A fresh stack covers no PIN codes, so the address added above would not be serviceable.
        # Cover it through the seller API before quoting, the way the seller configures delivery —
        # but only when it is not covered already, so a pre-seeded stack needs no seller account.
        status, probe, _ = http("GET", "/api/v1/serviceability/560038")
        check(status == 200, f"probing the PIN -> {status}: {probe}")
        if not probe.get("serviceable"):
            require_admin()
            status, _, _ = http("POST", "/api/v1/admin/fulfilment/serviceable-areas",
                                token=state["admin_token"],
                                body={"pincodePrefix": "560038", "areaName": "Indiranagar",
                                      "deliveryChargeMinorUnits": 4900, "active": True})
            check(status in (200, 201, 409), f"covering the PIN -> {status}")
        status, body, _ = http("POST", "/api/v1/carts/me/lines", token=state["token"],
                               body={"variantId": state["variant_id"], "quantity": 2})
        check(status == 200, f"expected 200, got {status}: {body}")
        status, quote_body, _ = http(
            "GET", f"/api/v1/checkout/quote?addressId={state['address_id']}", token=state["token"])
        check(status == 200, f"expected 200, got {status}: {quote_body}")
        check(quote_body["serviceable"] is True, "the delivery PIN is not serviceable")
        state["order_total"] = quote_body["totalMinorUnits"]
        check(state["order_total"] > 0, "the quote total is not positive")

    def unserviceable_pin():
        status, body, _ = http("GET", "/api/v1/serviceability/999999")
        check(status == 200, f"expected a reasoned 200, got {status}: {body}")
        check(body.get("serviceable") is False, "an uncovered PIN reported as serviceable")

    def place_order():
        status, body, _ = http("POST", "/api/v1/checkout", token=state["token"],
                               body={"addressId": state["address_id"]},
                               headers={"Idempotency-Key": str(uuid.uuid4())})
        check(status == 201, f"expected 201, got {status}: {body}")
        check(body["status"] == "PAYMENT_PENDING", f"expected PAYMENT_PENDING, got {body['status']}")
        check(body["totalMinorUnits"] == state["order_total"],
              "the order total is not the quoted total")
        state["order_id"] = body["id"]

    def another_customers_order_is_hidden():
        status, _, _ = http("GET", f"/api/v1/orders/me/{uuid.uuid4()}", token=state["token"])
        check(status in (403, 404), f"expected 403 or 404, got {status}")

    def unauthenticated_order_read():
        status, _, _ = http("GET", f"/api/v1/orders/me/{state['order_id']}")
        check(status == 401, f"expected 401, got {status}")

    def payment_fails_closed():
        status, body, _ = http("POST", f"/api/v1/orders/me/{state['order_id']}/payment",
                               token=state["token"],
                               headers={"Idempotency-Key": str(uuid.uuid4())})
        check(status in (502, 503), f"expected 502/503, got {status}: {body}")
        _, order, _ = http("GET", f"/api/v1/orders/me/{state['order_id']}", token=state["token"])
        check(order["paymentStatus"] == "PENDING", "an unconfigured provider moved the payment")

    for name, function in (
            ("register a customer through the gateway", register),
            ("a duplicate registration is refused", duplicate_registration),
            ("the email code arrives and verifies the address", email_code_verifies),
            ("a wrong verification code is refused", a_wrong_code_is_refused),
            ("the mobile code arrives in the dev log and verifies the number", mobile_code_verifies),
            ("sign in and read the profile", sign_in),
            ("a wrong password is refused", wrong_password_refused),
            ("add a delivery address", add_address),
            ("read the public catalogue and pick a published variant", catalogue),
            ("the PIN is serviceable and the quote prices the cart", quote),
            ("an uncovered PIN is answered with a reason, not an error", unserviceable_pin),
            ("place the order with an idempotency key", place_order),
            ("another customer's order is not readable", another_customers_order_is_hidden),
            ("an unauthenticated caller cannot read the order", unauthenticated_order_read),
            ("starting a payment fails closed with no provider credentials", payment_fails_closed)):
        runner.run(name, function)

    if arguments.skip_simulated_capture:
        return _report()

    print("\n  -- the verified capture (sandbox simulation) --")

    def captured_confirms():
        routed = publish_event("payment.captured", {
            "orderId": state["order_id"],
            "providerPaymentId": f"pay_E2E{uuid.uuid4().hex[:10]}",
            "providerOrderId": f"order_E2E{uuid.uuid4().hex[:10]}",
            "amountMinorUnits": state["order_total"],
            "currency": "INR"}, producer="e2e-journey")
        check(routed, "the capture event was not routed to any queue")
        deadline = time.time() + 30
        while time.time() < deadline:
            _, order, _ = http("GET", f"/api/v1/orders/me/{state['order_id']}", token=state["token"])
            if order["status"] == "CONFIRMED":
                check(order["paymentStatus"] == "PAID", "confirmed without a paid payment")
                return
            time.sleep(2)
        raise StepError("the order did not become CONFIRMED from the capture event")

    def wrong_amount_refused():
        routed = publish_event("payment.captured", {
            "orderId": state["order_id"],
            "providerPaymentId": f"pay_WRONG{uuid.uuid4().hex[:8]}",
            "providerOrderId": f"order_WRONG{uuid.uuid4().hex[:8]}",
            "amountMinorUnits": 1,
            "currency": "INR"}, producer="e2e-journey")
        check(routed, "the wrong-amount event was not routed")
        time.sleep(4)
        _, order, _ = http("GET", f"/api/v1/orders/me/{state['order_id']}", token=state["token"])
        check(order["totalMinorUnits"] == state["order_total"], "the order total changed")
        check(order["paymentStatus"] == "PAID", "a wrong-amount capture changed the payment")

    def production_job_opened():
        deadline = time.time() + 30
        while time.time() < deadline:
            _, body, _ = http("GET", f"/api/v1/fulfilment/me/orders/{state['order_id']}/preparation",
                              token=state["token"])
            if body:
                state["jobs"].extend(body)
                check(body[0]["status"] == "RECEIVED",
                      f"unexpected job status {body[0]['status']}")
                return
            time.sleep(2)
        raise StepError("no production job was opened from order.confirmed")

    runner.run("a verified payment.captured confirms the order", captured_confirms)
    runner.run("a capture for the wrong amount is refused", wrong_amount_refused)
    runner.run("the fulfilment service opened a job at the frozen address", production_job_opened)

    if arguments.skip_seller_stages:
        return _report()

    print("\n  -- the seller's stages --")
    if not (ADMIN_EMAIL and ADMIN_PASSWORD):
        SKIPPED.append("seller stages: set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD")
        print("  SKIP  seller stages: set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD")
        return _report()

    def customer_refused_at_kitchen():
        status, _, _ = http("GET", "/api/v1/admin/fulfilment/production/jobs",
                            token=state["token"])
        check(status in (401, 403), f"expected 401/403, got {status}")

    def anonymous_refused_at_kitchen():
        status, _, _ = http("GET", "/api/v1/admin/fulfilment/production/jobs")
        check(status == 401, f"expected 401, got {status}")

    def forged_admin_token_refused():
        status, _, _ = http("GET", "/api/v1/admin/fulfilment/production/jobs",
                            token="not.a.jwt")
        check(status == 401, f"expected 401, got {status}")

    def production():
        job_id = state["jobs"][0]["id"]
        for action, expected in (("ACKNOWLEDGE", "AWAITING_PREPARATION"),
                                 ("START", "PREPARATION_STARTED"),
                                 ("COMPLETE", "PREPARATION_COMPLETED"),
                                 ("PACKAGING", "PACKAGING"),
                                 ("READY_FOR_DISPATCH", "READY_FOR_DISPATCH")):
            status, body, _ = http(
                "POST", f"/api/v1/admin/fulfilment/production/jobs/{job_id}/progress",
                token=state["admin_token"], body={"action": action})
            check(status == 200, f"{action} -> {status}: {body}")
            check(body["status"] == expected,
                  f"{action} produced {body['status']}, expected {expected}")

    def illegal_production_jump():
        job_id = state["jobs"][0]["id"]
        status, _, _ = http("POST",
                            f"/api/v1/admin/fulfilment/production/jobs/{job_id}/progress",
                            token=state["admin_token"], body={"action": "START"})
        check(status in (409, 422), f"expected a refusal, got {status}")

    def delivery_opened_at_frozen_address():
        status, body, _ = http("GET", f"/api/v1/fulfilment/me/orders/{state['order_id']}/delivery",
                               token=state["token"])
        check(status == 200, f"expected 200, got {status}: {body}")
        check(body["status"] == "PENDING", f"expected PENDING, got {body['status']}")
        address = body.get("address") or body
        check(address.get("pincode") == "560038", "the delivery address is not the frozen one")

    def delivery():
        status, body, _ = http(
            "POST", f"/api/v1/admin/fulfilment/deliveries/orders/{state['order_id']}/assign",
            token=state["admin_token"],
            body={"deliveryPersonName": "Ravi Kumar", "deliveryPersonPhone": "9800000001"})
        check(status == 200, f"assign -> {status}: {body}")
        check(body["status"] == "ASSIGNED", f"expected ASSIGNED, got {body['status']}")
        for action, expected in (("OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY"),
                                 ("DELIVERED", "DELIVERED")):
            status, body, _ = http(
                "POST", f"/api/v1/admin/fulfilment/deliveries/orders/{state['order_id']}/progress",
                token=state["admin_token"],
                body={"action": action, "proofReference": "POD-E2E-1"})
            check(status == 200, f"{action} -> {status}: {body}")
            check(body["status"] == expected, f"{action} produced {body['status']}")

    def customer_sees_delivery():
        status, body, _ = http("GET", f"/api/v1/fulfilment/me/orders/{state['order_id']}/delivery",
                               token=state["token"])
        check(status == 200, f"expected 200, got {status}")
        check(body["status"] == "DELIVERED", f"expected DELIVERED, got {body['status']}")

    def journey_notifications_sent():
        wanted = {"ORDER_PLACED", "ORDER_CONFIRMED", "INVOICE_ISSUED", "PRODUCTION_STARTED",
                  "PRODUCTION_COMPLETED", "READY_FOR_DISPATCH", "DELIVERY_ASSIGNED",
                  "OUT_FOR_DELIVERY", "DELIVERY_DELIVERED"}
        seen: set[str] = set()
        deadline = time.time() + 45
        while time.time() < deadline and not wanted.issubset(seen):
            status, listing, _ = http("GET", "/api/v1/admin/notifications?size=100",
                                      token=state["admin_token"])
            check(status == 200, f"notification log -> {status}: {listing}")
            content = listing.get("content", listing if isinstance(listing, list) else [])
            seen = {row["type"] for row in content
                    if row.get("referenceId") == state["order_id"]}
            if not wanted.issubset(seen):
                time.sleep(3)
        check(not (wanted - seen), f"no notification was sent for {sorted(wanted - seen)}")

    def invoice():
        status, listing, _ = http("GET", "/api/v1/invoices/me", token=state["token"])
        check(status == 200, f"expected 200, got {status}: {listing}")
        mine = [row for row in listing.get("content", []) if row["orderId"] == state["order_id"]]
        check(mine, "no invoice was issued for the order")
        status, _, headers = http("GET", f"/api/v1/invoices/me/{mine[0]['id']}",
                                  token=state["token"])
        check(status == 200, f"invoice download -> {status}")
        check("no-store" in _header(headers, "Cache-Control").lower(),
              "the invoice download is cacheable")

    def invoice_isolation():
        status, _, _ = http("GET", f"/api/v1/invoices/me/{uuid.uuid4()}", token=state["token"])
        check(status in (403, 404), f"expected 403 or 404, got {status}")

    def dashboard_and_reports():
        status, dashboard, _ = http("GET", "/api/v1/dashboard", token=state["admin_token"])
        check(status == 200, f"dashboard -> {status}: {dashboard}")
        status, report, _ = http("GET", "/api/v1/reports/SALES", token=state["admin_token"])
        check(status == 200, f"SALES report -> {status}: {report}")

    def customer_export_masks():
        status, csv, _ = http("GET", "/api/v1/exports/CUSTOMERS?format=CSV",
                              token=state["admin_token"])
        check(status == 200, f"export -> {status}")
        text = csv if isinstance(csv, str) else str(csv)
        check(email not in text, "the raw email appears in the export")
        check(mobile not in text, "the raw mobile appears in the export")
        status, _, _ = http("GET", "/api/v1/exports/CUSTOMERS?format=CSV", token=state["token"])
        check(status in (401, 403), f"a customer reached the export: {status}")

    for name, function in (
            ("an administrator signs in", admin_login),
            ("a customer token cannot reach the seller's kitchen", customer_refused_at_kitchen),
            ("an unauthenticated caller cannot reach the seller's kitchen",
             anonymous_refused_at_kitchen),
            ("a forged admin token is refused at the edge", forged_admin_token_refused),
            ("the seller walks preparation from received to ready for dispatch", production),
            ("an illegal production jump is refused", illegal_production_jump),
            ("ready for dispatch opened one delivery at the frozen address",
             delivery_opened_at_frozen_address),
            ("the seller assigns a delivery person and completes the delivery", delivery),
            ("the customer sees the delivery as delivered", customer_sees_delivery),
            ("the journey notifications were all sent", journey_notifications_sent),
            ("an invoice was issued and the customer can read it", invoice),
            ("a customer cannot download another customer's invoice", invoice_isolation),
            ("the seller's dashboard and reports answer", dashboard_and_reports),
            ("the customer export masks contact details and is not public", customer_export_masks)):
        runner.run(name, function)

    return _report()


def _report() -> int:
    print("\n" + "=" * 68)
    print(f"passed: {len(PASSED)}   failed: {len(FAILED)}   skipped: {len(SKIPPED)}")
    for failure in FAILED:
        print(f"  FAILED  {failure}")
    for skip in SKIPPED:
        print(f"  SKIPPED {skip}")
    print("=" * 68)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
