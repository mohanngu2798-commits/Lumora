"""Generates the app's example pictures with an open photo-realistic text-to-image model
(RealVisXL V4.0 Lightning, an SDXL fine-tune under the CreativeML Open RAIL++-M licence, which allows commercial use).
Every picture is AI generated; none shows a real person. Run by .github/workflows/samples.yml.
A picture is made again unless its name is listed in done.txt for the current MODEL, so switching models redoes the set."""
import sys, torch
from pathlib import Path
from diffusers import StableDiffusionXLPipeline, DPMSolverMultistepScheduler

MODEL = "SG161222/RealVisXL_V4.0_Lightning"
out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
done_file = Path(sys.argv[2]).with_name("done.txt")
done = set(done_file.read_text().split()) if done_file.exists() else set()

pipe = StableDiffusionXLPipeline.from_pretrained(MODEL, torch_dtype=torch.float32)
pipe.scheduler = DPMSolverMultistepScheduler.from_config(
    pipe.scheduler.config, algorithm_type="sde-dpmsolver++", use_karras_sigmas=True)
pipe.set_progress_bar_config(disable=True)
torch.set_num_threads(4)

STYLE = ", photorealistic, RAW photo, natural skin texture, sharp focus, high detail, shot on a full-frame camera, soft natural light"
NEGATIVE = ("deformed, extra fingers, extra limbs, mutated hands, bad anatomy, disfigured, blurry, lowres, cartoon, "
            "illustration, 3d render, text, watermark, logo, signature, frame, oversaturated")
key = lambda name: f"{MODEL.split('/')[-1]}:{name}"

for line in Path(sys.argv[2]).read_text().splitlines():
    if not line.strip() or line.startswith("#"): continue
    name, prompt = [x.strip() for x in line.split("|", 1)]
    if key(name) in done: continue
    g = torch.Generator().manual_seed(abs(hash(name)) % 10000)
    img = pipe(prompt + STYLE, negative_prompt=NEGATIVE, num_inference_steps=6, guidance_scale=1.5,
               width=832, height=1040, generator=g).images[0]
    img.resize((640, 800), resample=3).save(out / f"sample_{name}.webp", quality=84)
    done.add(key(name))
    done_file.write_text("\n".join(sorted(done)) + "\n")
    print("made", name, flush=True)
