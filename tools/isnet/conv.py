import onnx, torch, numpy as np
from onnx2torch import convert
m=onnx.load('isnet_s.onnx')
print([(i.name,[d.dim_value for d in i.type.tensor_type.shape.dim]) for i in m.graph.input])
print([(o.name,[d.dim_value for d in o.type.tensor_type.shape.dim]) for o in m.graph.output])
net=convert(m).eval()
import torch.nn.functional as F
from onnx2torch.node_converters.resize import OnnxResize
rs=[mod for mod in net.modules() if isinstance(mod,OnnxResize)]
def rec(mod,args,out): mod.size_=tuple(out.shape[-2:])
hs=[r.register_forward_hook(rec) for r in rs]
with torch.no_grad(): net(torch.zeros(1,3,1024,1024))
for h in hs: h.remove()
def fwd(self,x,*a,**k): return F.interpolate(x,size=self.size_,mode='bilinear',align_corners=False)
OnnxResize.forward=fwd
print('resizes',len(rs))
class W(torch.nn.Module):
    def __init__(s,n): super().__init__(); s.n=n
    def forward(s,x):  # x: NHWC float 0..1 minus 0.5 (rembg normalisation: mean .5, std 1)
        o=s.n(x.permute(0,3,1,2))
        o=o[0] if isinstance(o,(list,tuple)) else o
        return o.permute(0,2,3,1)
w=W(net).eval()
x=torch.rand(1,1024,1024,3)-0.5
with torch.no_grad(): y=w(x)
print(y.shape, y.min(), y.max())
import litert_torch
t=litert_torch.convert(w,(x,)); t.export('isnet_fp32.tflite'); print('ok')
