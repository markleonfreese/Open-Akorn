# Verification — 26 Nov 2026 AWST

## Exact-head code checks

- Implementation commit tested: `faf8d7b608002ed760d38b9c18b0b4e0fb55fb1d` on the existing draft PR #1 branch `poc/local-monitor`.
- Python 3.14.6: `python3 -m unittest discover -s tests -v` — 22 tests passed.
- `python3 -m compileall -q monitor.py ha_mqtt.py tests` — passed.
- `git diff --check` — passed.
- The existing GitHub PR currently reports no checks. No hosted job was started by this run.

## MQTT adapter scope

The in-memory publisher tests cover retained sensor discovery and availability, independent gateway/device/probe status, freshness expiry, disconnected probe sentinels, malformed values, demo suppression and non-retained telemetry. `ha_mqtt.py` only builds and forwards messages to a supplied callable. It does not connect to a broker, subscribe, configure a Last Will on a client, integrate into `monitor.py`, or publish to Home Assistant. No credentials or network were used.

## File hashes at the tested implementation commit

- `monitor.py` SHA-256 `4433633a18cbb3d97531d21c9e15a2a1e4eb4acfc6f7a35553b11c1172e4c3b7`
- `ha_mqtt.py` SHA-256 `cddcaf3e4f47bee74b3dc77c78fc3519a71ae101f8665997b600fb5f2ea37c52`
- `tests/test_monitor.py` SHA-256 `39f288ee2a3adc666ce54e4614fa60bd7fef0598c2637d432a5cb394a90c07e8`
- `tests/test_ha_mqtt.py` SHA-256 `c74dacd1526a235fe7b235be76fc3519a71ae101f8665997b600fb5f2ea37c52`

## Not verified

No MQTT broker, Home Assistant instance, live TCP grill, BLE device, browser rendering, physical compatibility or cold-grill acceptance was tested. No app startup, device command, provisioning, deployment, merge or paid service was used. Treat readings as experimental and never as safety guidance.

## Deep Research follow-up

Inspected head 8aafe7acda7275d59f3d77fb2d7f1ecc005df3b5 and existing 22-test report above; did not independently execute Python in this session. Added five read-only regression tests (e82eca256d3433e0d471d9d5b0a3c36c5db84b94), including sentinel coverage, alarm/reserved byte, one example of misalignment rejection, stale data suppression and localhost HTTP POST rejection. No runtime logic changed. Added bounded PR-only CI (5d1b4adada91ce23e5ed02ebaac4f35ee1835f85). GitHub workflow query at 1c0e9b45db7c7c8060a78cf5e3d2242086330303 returned no runs; new tests and CI are unverified, not passed. Run current suite on Python 3.11+ and inspect actual CI logs before merging. Research and owner quick-start now linked in README. Hardware, broker, BLE and browser acceptance still pending. No grill commands, deployment or merge.
