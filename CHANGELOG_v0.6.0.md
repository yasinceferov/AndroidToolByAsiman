# AndroidToolByAsiman v0.6.0

## Changes
- USB screen now explicitly describes the external USB device, separately from the phone running the app.
- USB attach/detach events refresh the device list.
- Added USB manufacturer/product strings, VID/PID, interface and endpoint details, plus heuristic vendor hints.
- Security screen includes Xiaomi's official bootloader information and Mi Unlock links.
- Updated GitHub Actions Android SDK setup to setup-android@v4 and explicit SDK package installation.

## Important limitations
- This release does not implement ADB Host or Fastboot Host. It cannot yet query remote Android Build.MODEL, Android version, serial, or properties over USB.
- This release does not write firmware partitions or flash devices.
- Bootloader bypass, FRP bypass, screen-lock bypass, and exploit-based unlocking are not included. Use only the manufacturer's authorized unlock procedure.

## Build
Push the project to GitHub `main`, then open Actions and download the `AndroidToolByAsiman-v0.6.0-debug` artifact after a successful run.
