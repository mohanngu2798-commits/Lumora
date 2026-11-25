import sys, torch, numpy as np, time
from PIL import Image
sys.path.insert(0,'.')
exec(open('build.py').read().split("torch.save")[0])
x=(np.asarray(Image.open('crop_00.png').convert('RGB'),np.float32)/127.5-1)[None]
import litert_torch
t=time.time()
m=litert_torch.convert(w,(torch.from_numpy(x),))
m.export('gfpgan_fp32.tflite'); print('fp32 exported', time.time()-t, flush=True)
