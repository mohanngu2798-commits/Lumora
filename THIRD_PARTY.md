# Third-party components

- **ESRGAN super-resolution model** (`app/src/main/assets/esrgan_x4.tflite`), from the TensorFlow Lite
  super-resolution example (https://www.tensorflow.org/lite/examples/super_resolution/overview).
  Apache License 2.0. Based on "ESRGAN: Enhanced Super-Resolution Generative Adversarial Networks", Wang et al.
- **TensorFlow Lite** runtime (`org.tensorflow:tensorflow-lite`). Apache License 2.0.
- **BlazeFace face detector, full range** (`app/src/main/assets/face_detector.tflite`), from Google MediaPipe
  (https://github.com/google-ai-edge/mediapipe). Apache License 2.0.
- **GFPGAN v1.4 face restoration model** (`app/src/main/assets/gfpgan_v1_4.tflite`), converted on our side from the
  official weights of TencentARC/GFPGAN (https://github.com/TencentARC/GFPGAN). Apache License 2.0.
  GFPGAN builds on StyleGAN2 and was trained on the FFHQ dataset; see the GFPGAN repository for their licence notes.
- **IS-Net background removal model** (`ai_models/src/main/assets/isnet_general_fp16.tflite`), the "general use"
  weights of DIS by Xuebin Qin et al. (https://github.com/xuebinqin/DIS), converted on our side to TFLite fp16.
  Apache License 2.0. Steps in `tools/isnet/`.
