import os
from PIL import Image

src = r'C:\Users\Administrator\.gemini\antigravity\brain\4bc90ebd-04a2-4cb1-bb9d-d7c1e1e232af\media__1778436768278.png'
base_res = r'd:\AttendanceSystem\app\src\main\res'

sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

img = Image.open(src).convert('RGBA')

# Replace legacy icons
for density, size in sizes.items():
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    out_dir = os.path.join(base_res, f'mipmap-{density}')
    os.makedirs(out_dir, exist_ok=True)
    
    resized.save(os.path.join(out_dir, 'ic_launcher.webp'), 'WEBP')
    resized.save(os.path.join(out_dir, 'ic_launcher_round.webp'), 'WEBP')

# For adaptive icons, we need foreground and background.
# Let's scale the image down to 70% to fit within the adaptive icon safe zone (which is ~66% of the 108dp area)
# Actually, the adaptive icon full size is 108dp, safe zone is 72dp. 72/108 = 0.66.
# If we scale the original 1000x1000 image to take up 66% of the new 108x108 area, it will never be cropped.
fg_img = Image.new('RGBA', (1000, 1000), (0, 0, 0, 0))
# resize original to 660x660
scaled = img.resize((660, 660), Image.Resampling.LANCZOS)
fg_img.paste(scaled, (170, 170), scaled)

for density, size in sizes.items():
    # Adaptive icons scale identically to legacy ones (108dp instead of 48dp)
    # mdpi = 108px, hdpi = 162px, xhdpi = 216px, xxhdpi = 324px, xxxhdpi = 432px
    adaptive_sizes = {
        'mdpi': 108,
        'hdpi': 162,
        'xhdpi': 216,
        'xxhdpi': 324,
        'xxxhdpi': 432
    }
    ad_size = adaptive_sizes[density]
    
    out_dir = os.path.join(base_res, f'mipmap-{density}')
    os.makedirs(out_dir, exist_ok=True)
    
    resized_fg = fg_img.resize((ad_size, ad_size), Image.Resampling.LANCZOS)
    resized_fg.save(os.path.join(out_dir, 'ic_launcher_foreground.webp'), 'WEBP')

print("Done")
