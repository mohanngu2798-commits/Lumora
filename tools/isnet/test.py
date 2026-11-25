import numpy as np, onnxruntime as ort
from PIL import Image
from ai_edge_litert.interpreter import Interpreter
for name in ['ai_face','business','fashion']:
    im=Image.open(f'/home/user/src/lumora/app/src/main/res/drawable-nodpi/sample_{name}.webp').convert('RGB')
    x=np.asarray(im.resize((1024,1024),Image.BILINEAR),np.float32)/255-0.5
    o=ort.InferenceSession('isnet.onnx').run(['output_image'],{'input_image':x.transpose(2,0,1)[None]})[0][0,0]
    it=Interpreter('isnet_fp16.tflite',num_threads=4); it.allocate_tensors()
    it.set_tensor(it.get_input_details()[0]['index'],x[None]); it.invoke()
    t=it.get_tensor(it.get_output_details()[0]['index'])[0,:,:,0]
    print(name, 'maxdiff',np.abs(o-t).max(), 'range', t.min(), t.max())
    a=(t-t.min())/(t.max()-t.min()+1e-8)
    m=Image.fromarray((a*255).astype(np.uint8)).resize(im.size)
    rgba=im.copy(); rgba.putalpha(m)
    bg=Image.new('RGB',im.size,(255,255,255)); bg.paste(rgba,mask=m)
    w=Image.new('RGB',(im.width*2,im.height)); w.paste(im,(0,0)); w.paste(bg,(im.width,0)); w.save(f'out_{name}.jpg')
