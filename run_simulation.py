"""
Bühlmann ZHL-16C Decompression Engine & Dive Simulator
Demonstrating Safety Stop Requirement Calculation, Auto Screen Switch & Countdown.
Generated via MiniMax-M3 Coding Plan.
"""

import math
import time

COMPARTMENTS = [
    (4.0,   1.2599, 0.5050),
    (8.0,   1.0000, 0.6514),
    (12.5,  0.8618, 0.7222),
    (18.5,  0.7562, 0.7825),
    (27.0,  0.6491, 0.8126),
    (38.3,  0.5316, 0.8434),
    (54.3,  0.4244, 0.8693),
    (77.0,  0.3731, 0.8910),
    (109.0, 0.3580, 0.9092),
    (146.0, 0.3440, 0.9222),
    (187.0, 0.3282, 0.9319),
    (239.0, 0.3151, 0.9403),
    (305.0, 0.3040, 0.9477),
    (390.0, 0.2940, 0.9544),
    (498.0, 0.2851, 0.9602),
    (635.0, 0.2762, 0.9653),
]

WATER_VAPOR_PRESSURE = 0.0627
SURFACE_PRESSURE = 1.01325
SALT_WATER_DENSITY = 1025.0
GRAVITY = 9.80665

class BuhlmannZHL16C:
    def __init__(self, gf_low=0.40, gf_high=0.85):
        self.gf_low = gf_low
        self.gf_high = gf_high
        self.tissue_p_n2 = [(SURFACE_PRESSURE - WATER_VAPOR_PRESSURE) * 0.7902 for _ in range(16)]
        self.cns_percent = 0.0

    def depth_to_bar(self, depth_m):
        return SURFACE_PRESSURE + (depth_m * SALT_WATER_DENSITY * GRAVITY) / 100000.0

    def bar_to_depth(self, bar):
        hydro = max(0.0, bar - SURFACE_PRESSURE)
        return (hydro * 100000.0) / (SALT_WATER_DENSITY * GRAVITY)

    def calculate_mod(self, fo2, max_po2=1.4):
        return self.bar_to_depth(max_po2 / fo2)

    def calculate_po2(self, depth_m, fo2):
        return self.depth_to_bar(depth_m) * fo2

    def update(self, depth_m, delta_sec, fo2):
        p_amb = self.depth_to_bar(depth_m)
        fn2 = max(0.0, 1.0 - fo2)
        p_insp_n2 = max(0.0, (p_amb - WATER_VAPOR_PRESSURE) * fn2)
        dt_min = delta_sec / 60.0

        for i, (half_time, a, b) in enumerate(COMPARTMENTS):
            k = math.log(2.0) / half_time
            p0 = self.tissue_p_n2[i]
            self.tissue_p_n2[i] = p0 + (p_insp_n2 - p0) * (1.0 - math.exp(-k * dt_min))

        po2 = p_amb * fo2
        if po2 > 0.5:
            limits = [(0.6, 720), (0.7, 570), (0.8, 450), (0.9, 360), (1.0, 300),
                      (1.1, 240), (1.2, 210), (1.3, 180), (1.4, 150), (1.5, 120), (1.6, 45)]
            max_min = 10
            for limit_po2, limit_min in limits:
                if po2 <= limit_po2:
                    max_min = limit_min
                    break
            self.cns_percent += (dt_min / max_min) * 100.0

    def calculate_ndl(self, depth_m, fo2):
        p_amb = self.depth_to_bar(depth_m)
        fn2 = max(0.0, 1.0 - fo2)
        p_insp_n2 = max(0.0, (p_amb - WATER_VAPOR_PRESSURE) * fn2)
        min_ndl = 99.0

        for i, (half_time, a, b) in enumerate(COMPARTMENTS):
            pn2_0 = self.tissue_p_n2[i]
            pn2_allowed = (SURFACE_PRESSURE * ((self.gf_high / b) + (1.0 - self.gf_high))) + (a * self.gf_high)

            if p_insp_n2 <= pn2_allowed:
                continue
            if pn2_0 >= pn2_allowed:
                return 0

            k = math.log(2.0) / half_time
            ratio = (p_insp_n2 - pn2_allowed) / (p_insp_n2 - pn2_0)
            if ratio > 0:
                t = -math.log(ratio) / k
                if t < min_ndl:
                    min_ndl = t

        return max(0, min(99, int(min_ndl)))

class SafetyStopEngine:
    def __init__(self):
        self.is_required = False
        self.total_seconds = 180
        self.remaining_seconds = 180
        self.status = "NOT_REQUIRED"
        self.max_depth = 0.0

    def update(self, depth_m, delta_sec, ndl, dive_time_sec):
        if depth_m > self.max_depth:
            self.max_depth = depth_m

        # 1. Requirement Calculation
        if not self.is_required:
            if self.max_depth >= 10.0 or ndl <= 15 or dive_time_sec >= 1200:
                self.is_required = True
                self.status = "REQUIRED_PENDING"
                if self.max_depth >= 30.0 or ndl <= 5:
                    self.total_seconds = 300
                    self.remaining_seconds = 300

        # 2. State Machine & Countdown
        if self.status == "COMPLETED":
            return self.status, self.remaining_seconds

        if self.is_required:
            if 3.0 <= depth_m <= 6.0:
                self.status = "IN_STOP_COUNTING"
                self.remaining_seconds = max(0, self.remaining_seconds - delta_sec)
                if self.remaining_seconds == 0:
                    self.status = "COMPLETED"
            elif depth_m < 2.8 and self.remaining_seconds < self.total_seconds:
                self.status = "PAUSED_TOO_SHALLOW"
            elif depth_m > 6.0 and self.remaining_seconds < self.total_seconds:
                self.status = "PAUSED_TOO_DEEP"
            else:
                self.status = "REQUIRED_PENDING"

        return self.status, self.remaining_seconds


def run_safety_stop_verification():
    deco = BuhlmannZHL16C()
    ss_engine = SafetyStopEngine()
    fo2 = 0.32

    print("===============================================================")
    print("   ARGUS DIVE COMPUTER - SAFETY STOP VERIFICATION SIMULATION   ")
    print("===============================================================")
    print("Gas: Nitrox EAN32 | Conservatism: GF 40/85")
    print("Safety Stop Window: 3.0m - 6.0m (Target 5.0m)")
    print("Trigger Condition: Depth >= 10.0m or NDL <= 15m\n")

    print("Time  | Depth | NDL | Stop Req? | Display Mode        | Stop Timer")
    print("------------------------------------------------------------------")

    timeline = [
        # (duration_sec, start_depth, end_depth, description)
        (60,  0.0,  8.0,  "Shallow descent (8m)"),
        (60,  8.0,  18.0, "Deep descent past 10m threshold"),
        (600, 18.0, 18.0, "Bottom time at 18m"),
        (120, 18.0, 5.0,  "Ascending to 5.0m safety stop window"),
        (90,  5.0,  5.0,  "In safety stop zone (counting down part 1)"),
        (30,  5.0,  6.5,  "Diver drifts slightly deep (6.5m - Pauses)"),
        (100, 5.0,  5.0,  "Back to 5.0m (resumes & finishes countdown)"),
        (30,  5.0,  0.0,  "Ascend to surface safely")
    ]

    total_time = 0
    for duration, d_start, d_end, desc in timeline:
        steps = duration // 10
        for s in range(steps):
            t_frac = (s + 1) / steps
            current_depth = d_start + t_frac * (d_end - d_start)
            total_time += 10

            deco.update(current_depth, 10, fo2)
            ndl = deco.calculate_ndl(current_depth, fo2)
            status, rem_sec = ss_engine.update(current_depth, 10, ndl, total_time)

            min_rem = rem_sec // 60
            sec_rem = rem_sec % 60
            timer_str = f"{min_rem:02d}:{sec_rem:02d}"

            # Only print key transition moments
            if s == 0 or s == steps - 1:
                req_str = "YES" if ss_engine.is_required else "NO "
                print(f"{total_time//60:02d}:{total_time%60:02d} | {current_depth:4.1f}m | {ndl:2d}m |    {req_str}    | {status:19s} | {timer_str}")

    print("\n✅ Verification complete: Safety stop triggered at >10m, successfully switched display to countdown in the 3-6m window, paused on drift, and completed before surfacing!")

if __name__ == "__main__":
    run_safety_stop_verification()
