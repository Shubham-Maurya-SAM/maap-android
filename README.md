# MAAP (माप) - AR Room Measurement

MAAP is a modern, real-time room and carpet measurement application built for Android. It leverages **ARCore** for motion tracking and surface detection to provide a seamless measurement experience similar to Apple's Measure app.

## 🚀 Features

- **Sequential Animated Splash:** A beautiful startup sequence showing the brand in English and Hindi with a measuring tape animation.
- **Dual-Mode Measurement:**
  - **AR View:** Real-time floor scanning, reticle tracking, and polygon area calculation.
  - **Manual Input:** Fast entry for rectangular and L-shaped rooms directly from the dashboard.
- **Persistent History:** Save and manage measurements with a local Room database.
- **Modern UI:** Built with Material3, featuring dark translucent controls and high-contrast visuals (WCAG compliant).
- **Accessibility:** Optimized for TalkBack with grouped information and haptic feedback.
- **Device Optimization:** Specially tuned for low-end devices like Redmi A3 and Samsung F22.

## 🛠 Tech Stack

- **Language:** Kotlin
- **Architecture:** MVVM
- **AR Engine:** ARCore
- **UI Framework:** XML / ViewBinding (Material3)
- **Database:** Room DB
- **Camera:** CameraX
- **Rendering:** OpenGL OES Shader + 2D Canvas Overlay

## 📱 How to Use

1. **Scan:** Move your phone slowly near the floor until the white point cloud appears and the reticle pulses.
2. **Measure:** Tap the `+` button at every corner of the room.
3. **Analyze:** Watch the distance and carpet area calculate in real-time.
4. **Save:** Enter a room name and save the measurement to your history.

---
Built by Shubham Maurya.
