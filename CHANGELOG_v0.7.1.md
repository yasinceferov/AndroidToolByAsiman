# AndroidToolByAsiman v0.7.1

## Firmware lookup now targets the connected phone

- Firmware search is enabled only when ADB data from the connected USB phone is present.
- The screen shows manufacturer, model, device codename, product, build ID, and build fingerprint from the connected phone.
- Search queries include the connected phone's identity and exact build information.
- Added separate searches by device identity, exact build fingerprint, and Xiaomi official sources.
- Removed the silent fallback to the host phone from the firmware lookup screen.
- Search results open in the browser; downloads and flashing are not automated.

## Important
ADB debugging must be enabled and authorized on the target phone. This feature is a targeted web search, not a firmware compatibility guarantee.
