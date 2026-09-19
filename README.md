# 🌊 Argus Dive Computer for Samsung Galaxy Watch Ultra 2

A Garmin Descent Mk3 / Shearwater-inspired dive computer app for **Samsung Galaxy Watch Ultra 2** (Wear OS). Powered by the gold-standard **Bühlmann ZHL-16C decompression algorithm with Gradient Factors (GF Low / GF High)**.

*Core decompression algorithm and mathematical verification generated via MiniMax-M3 Coding Plan.*

---

## 🤿 Core Features

- **Decompression Algorithm**: Full Bühlmann ZHL-16C with 16 tissue compartments (N2 half-times 4.0 to 635.0 min).
- **Conservatism**: User-adjustable Gradient Factors (Default **GF 40/85** for conservative recreational diving).
- **Nitrox (EANx) Support**: Configurable 21% to 40% O2 with real-time PO2 and MOD (Maximum Operating Depth for 1.4 / 1.6 bar).
- **CNS Oxygen Toxicity**: Real-time NOAA single-exposure limit percentage tracking.
- **Safety Stop Countdown**: Automatic 3-minute safety stop at 3.0m - 5.5m depth when ascending from deeper than 10m.
- **Ascent Rate Monitor**: Real-time m/min calculation with Garmin-style color arcs (Green safe <9m/min, Amber 9-10m/min, Red haptic alarm >10m/min).
- **Tactical OLED UI**: High-contrast dark display tailored for the Samsung Galaxy Watch Ultra 2's high-brightness AMOLED screen.
- **Subsurface-Compatible Dive Log**: Exports dives to **UDDF 3.2.0 (Universal Dive Data Format XML)** and JSON for syncing with Subsurface, MacDive, and Divelogs.de.

---

## 📁 Project Structure

- [`BuhlmannZHL16C.kt`](file:///C:/Users/Jonas/.gemini/antigravity-cli/scratch/galaxy-dive-computer/BuhlmannZHL16C.kt): Complete 16-compartment Bühlmann decompression solver.
- [`DiveStateManager.kt`](file:///C:/Users/Jonas/.gemini/antigravity-cli/scratch/galaxy-dive-computer/DiveStateManager.kt): Central state flow tracking depth, dive time, ascent rates, and safety stop states.
- [`DiveLogManager.kt`](file:///C:/Users/Jonas/.gemini/antigravity-cli/scratch/galaxy-dive-computer/DiveLogManager.kt): On-device dive storage and UDDF/JSON export engine.
- [`GarminDiveScreen.kt`](file:///C:/Users/Jonas/.gemini/antigravity-cli/scratch/galaxy-dive-computer/GarminDiveScreen.kt): Jetpack Compose for Wear OS tactical dive display.
- [`run_simulation.py`](file:///C:/Users/Jonas/.gemini/antigravity-cli/scratch/galaxy-dive-computer/run_simulation.py): Standalone dive simulator verifying real-time decompression curves.
- [`dive_log_sample.uddf`](file:///C:/Users/Jonas/.gemini/antigravity-cli/scratch/galaxy-dive-computer/dive_log_sample.uddf): Sample exported dive log ready for Subsurface import.

---

## 🚀 Running the Simulation

You can test the decompression algorithm and dive telemetry directly via Python:
```bash
python run_simulation.py
```
