# Background removal model

`ai_models/src/main/assets/isnet_general_fp16.tflite` is IS-Net "general use" (DIS, Apache-2.0,
https://github.com/xuebinqin/DIS), converted from the ONNX export published by rembg.

1. Download `isnet-general-use.onnx` (rembg GitHub release v0.0.0) as `isnet.onnx`.
2. Fold the dynamic shapes and keep only the final output: `onnxsim` with output `output_image` → `isnet_s.onnx`.
3. `python conv.py` (onnx2torch + litert-torch) → `isnet_fp32.tflite`, input NHWC 1×1024×1024×3 (RGB/255 − 0.5).
4. `python fp16.py` (ai-edge-quantizer float casting) → fp16, 88 MB.
5. `python test.py` checks the TFLite output against onnxruntime (max diff ≈ 0.0015).
