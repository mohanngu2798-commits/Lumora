"""Generates the app's example pictures with an open text-to-image model (LCM Dreamshaper v7, MIT licence).
Every picture is AI generated; none shows a real person. Run by .github/workflows/samples.yml."""
import sys, torch
from pathlib import Path
from diffusers import DiffusionPipeline

out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
pipe = DiffusionPipeline.from_pretrained("SimianLuo/LCM_Dreamshaper_v7", torch_dtype=torch.float32, safety_checker=None)
pipe.set_progress_bar_config(disable=True)
torch.set_num_threads(4)
neg = "text, watermark, logo, deformed, extra fingers"
for line in Path(sys.argv[2]).read_text().splitlines():
    if not line.strip() or line.startswith("#"): continue
    name, prompt = [x.strip() for x in line.split("|", 1)]
    g = torch.Generator().manual_seed(abs(hash(name)) % 10000)
    img = pipe(prompt + ", highly detailed, professional photography", num_inference_steps=6, guidance_scale=8.0,
               width=512, height=640, generator=g).images[0]
    img.save(out / f"sample_{name}.webp", quality=82)
    print("made", name, flush=True)
