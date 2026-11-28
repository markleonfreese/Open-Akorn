# Char-Griller app feature parity and local-server feasibility

Updated 28 November 2026. This audit uses public vendor documentation, the public App Store listing, the vendor's regional app article, and the pinned open-source `cg980app/chargrillerd` repository. It does not inspect or redistribute a proprietary APK or private traffic capture.

## What the old app did

The public [App Store listing](https://apps.apple.com/nz/app/char-griller/id1551650169) describes five core features: set the grill target, monitor food-probe temperatures, run a cook timer, graph grill/probe temperatures, and manage multiple Char-Griller grills from one phone. The listing also says the app used local Wi-Fi and Bluetooth and that v2.0.1 improved session graphing. The official [Australian how-to page](https://chargrilleraustralia.com.au/how-to-use-char-griller-app/) documents choosing a connection, a timer UI, setting pit/probe targets, status icons for the doors and fan, and a Session tab whose sessions for the day cannot be reset. These are claims about the old app; they are not claims about Open-Akorn compatibility. We have not inspected or redistributed the proprietary APK, so features outside public descriptions remain unknown.

The current Android/PWA build displays live or simulated pit/probe readings, reported targets, connection freshness, reported fan mode and alarm state. Android offers explicit TCP/Bluetooth setup, a configurable simulator, a local timer, a bounded graph, locally saved grill profiles and local cook-session history. The PWA reads one loopback gateway at a time and keeps bounded session history in that browser on that device. Neither app has an outbound grill command path. Door state is absent from the current shared status contract.

| Legacy capability | Open-Akorn today | Safe parity path |
| --- | --- | --- |
| Live pit and two food-probe readings | Implemented for simulation and read-only TCP/BLE code; physical compatibility unverified | Keep source, freshness and simulation state explicit |
| Show current pit/probe targets | Implemented as reported values | Preserve as read-only observed state |
| Change pit/probe targets | Not implemented | Requires completed supervised cold-grill acceptance and separate explicit authorization before any command implementation |
| Cook timer | Implemented locally (1–1440 minutes), with no grill command. Android optionally requests notification permission and schedules an inexact local alarm; the PWA optionally shows a browser notification only when its page timer is running and the service worker is available. Android may delay alerts or lose them after reboot/force-stop; a suspended/closed PWA page can delay or miss them | Treat as a convenience timer only; use a separate reliable timer when unattended. Neither notification is a food-safety alarm |
| Temperature graph and sessions | Current graph uses fresh °C samples; both apps keep up to 8 local sessions with at most 720 samples each, recording about once per minute. Old graphs can be reopened; the user can clear history. Simulations are excluded. Storage stays on the device and saves no grill address/name | Local session graph parity is implemented. No export, replay controls, zoom or pan. Browser storage may be cleared by the browser; app uninstall removes Android app data |
| Multiple grills | Android stores up to 12 named Wi-Fi/Bluetooth profiles on the device. Selecting one only loads details; the user must tap Connect. Connecting closes the current transport first. PWA uses one gateway target. No simultaneous grill connections | Profiles contain only user label and private IPv4 or advertised BLE name; never store BLE MACs or connect automatically |
| Door/fan status | Fan/alarm reported; door status unavailable in current contract | Show only fields the selected, verified model actually reports |
| Remote/cloud access | Not implemented | Keep no-cloud. Consider a private authenticated gateway only as a separate approved design |

## Did Char-Griller close servers that need impersonating?

The vendor's [US app announcement](https://www.chargriller.com/blogs/tips-and-care/how-to-install-the-char-griller-app) says its mobile-app service is being discontinued in the United States and that the grill continues to function using its built-in controls. The same page says support in Australia and New Zealand remains for the time being. As of 28 November 2026, this does not establish that every vendor backend is offline everywhere, whether the discontinued Android builds require a vendor API for any function, or whether basic telemetry used those backends.

The public [App Store listing](https://apps.apple.com/nz/app/char-griller/id1551650169) describes local Wi-Fi and Bluetooth connections. The public [community implementation](https://github.com/grzbiet/cg980app/tree/2236faf93f3d2b0663764a0379131d97aef77c2e) connects directly to the grill's local Bluetooth and TCP interfaces, then serves its own dashboard. It also provides local graphs, cook sessions, browser alarms and CSV history. Therefore a vendor-server clone is not needed for local monitoring, and available evidence points to the controller's local interface as the useful replacement boundary.

A local process could theoretically emulate a vendor API if its exact hostnames, request/response semantics, authentication, certificate expectations and legacy-client configuration were known. We have not established those details, and have not verified that the retired app requires those services for local monitoring. The public evidence instead says local Wi-Fi/Bluetooth and the community implementation directly reads grill TCP/BLE. DNS/TLS impersonation or patching the old client would add fragile trust and credential risks while duplicating capabilities Open-Akorn can own directly. **There is no evidence-backed reason to impersonate Char-Griller servers or redirect the old client.** The practical alternative is Open-Akorn's own client and local-only gateway, talking directly to the controller with its documented/community-reported local protocol.

The existing Python gateway binds to `127.0.0.1`, serves a normalized read-only `/api/status`, and does not cache telemetry in the PWA service worker. Android connects directly to the selected controller. Moving the gateway to a LAN interface, adding multiple-device sharing, or adding authentication remains a separate review and approval gate; no network exposure is included here.

## Community code boundary

The pinned community repository is MIT-licensed and its README documents status monitoring, graphs, sessions, local alarms, and command features. Its own dashboard defaults to all-interface binding, and it includes provisioning and command paths. Open-Akorn has not copied this code. If a future change reuses any portion, preserve the MIT notice and attribution, review the exact code and dependencies, and omit write, provisioning, reset, credential and broad-network-exposure paths. Public protocol documentation and synthetic fixtures are the preferred reusable interface.

## Remaining implementation order

1. Validate profile switching, session recording, retention and deletion with the owner on the actual phone/grill; source/build tests do not establish phone storage behavior or firmware compatibility.
2. Keep session export/replay, richer cook journals and recipe tracking as separate future scope.
3. Reassess target-write parity only after the physical acceptance gate and a separate explicit authorization. Until then, retain read-only behavior and state this limitation in the UI.

No account, cloud service, vendor endpoint emulation, automatic provisioning, fan override, or grill command is part of this parity work.
