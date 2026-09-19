import os
import shutil

source_dir = r"C:\Users\Jonas\.gemini\antigravity-cli\scratch\galaxy-dive-computer"
targets = [
    r"H:\My Drive\Galaxy-Watch-Dive-Computer-V1",
    r"D:\Development\Galaxy-Watch-Dive-Computer-V1"
]

mockups = [
    (r"C:\Users\Jonas\.gemini\antigravity-cli\brain\3cfccf54-506f-490d-b8d3-c39a0a376710\galaxy_dive_mockup_1789781959775.jpg", "mockup_safety_stop.jpg"),
    (r"C:\Users\Jonas\.gemini\antigravity-cli\brain\3cfccf54-506f-490d-b8d3-c39a0a376710\galaxy_dive_compass_1789789137086.jpg", "mockup_tactical_compass.jpg")
]

for target in targets:
    try:
        os.makedirs(target, exist_ok=True)
        # Copy tree
        for item in os.listdir(source_dir):
            s = os.path.join(source_dir, item)
            d = os.path.join(target, item)
            if os.path.isdir(s):
                if os.path.exists(d):
                    shutil.rmtree(d)
                shutil.copytree(s, d)
            else:
                shutil.copy2(s, d)
        
        # Copy mockups
        for src_mockup, dest_name in mockups:
            if os.path.exists(src_mockup):
                shutil.copy2(src_mockup, os.path.join(target, dest_name))
        
        print(f"Successfully copied to: {target}")
        print("Contents:", os.listdir(target))
    except Exception as e:
        print(f"Error copying to {target}: {e}")
