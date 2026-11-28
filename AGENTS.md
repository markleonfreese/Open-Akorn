# Open-Akorn agent instructions

- Canonical source: this repository. Personal coordination capsule: Agent-Vault / 01 Projects/open-akorn/AV.md.
- Start with docs/research.md and docs/bench.md. Treat upstream hardware claims as reported, not our verification.
- Prefer a single local TCP connection; verify BLE and provisioning as separately bounded tasks. Do not assume shared app means identical firmware.
- Keep the original temperature regulation and interlocks. Monitoring only until a documented, supervised cold-grill acceptance gate and explicit user approval for actuation. Do not implement automatic retries of writes, fan/power/timer commands or safety overrides.
- No proprietary APK, decompiled code, Wi-Fi passwords, PINs, MAC/IP identifiers or private captures in the public repo. Publish protocol facts, synthetic fixtures and sanitised evidence only. Preserve upstream MIT attribution if code is reused.
- Use deterministic tests and a simulated device. Do not claim hardware success from tests or a PR. Do not expose unauthenticated control services, merge, deploy or incur paid usage automatically.
- GitHub Copilot is the sole routine automated PR reviewer. Record evidence and next action in Agent Vault.
