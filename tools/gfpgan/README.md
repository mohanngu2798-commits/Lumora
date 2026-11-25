# GFPGAN v1.4 to LiteRT

How `models/gfpgan_v1_4.tflite.part*` was made. `gfparch.py` and `sg2.py` are the GFPGAN "clean" architecture
(TencentARC/GFPGAN, Apache 2.0) with export-friendly changes: modulation is applied to the input instead of the
weights (mathematically the same), and the fixed noise injection is skipped (under 1 grey level of difference).

1. Download `GFPGANv1.4.pth` from the GFPGAN v1.3.0 release into the parent folder.
2. `pip install torch litert-torch ai-edge-quantizer`
3. `python convert2.py` (float32 model), then `python fp16.py` (float16 weights, half the size).
4. `split -b 90m -d -a 1 gfpgan_fp16.tflite ../../models/gfpgan_v1_4.tflite.part`

Model input and output: NHWC `[1, 512, 512, 3]`, RGB in -1..1, face aligned to the FFHQ 512 template.
