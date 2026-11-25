from ai_edge_quantizer import quantizer
r=[{"regex": ".*", "operation": "*", "algorithm_key": "float_casting",
    "op_config": {"weight_tensor_config": {"num_bits": 16, "dtype": "FLOAT"}, "compute_precision": "FLOAT", "explicit_dequantize": True}}]
qt=quantizer.Quantizer('gfpgan_fp32.tflite'); qt.load_quantization_recipe(r)
res=qt.quantize(); res.export_model('gfpgan_fp16.tflite', overwrite=True); print('ok')
