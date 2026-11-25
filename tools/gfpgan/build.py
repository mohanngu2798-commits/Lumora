import sys, torch, numpy as np
sys.path.insert(0, '.')
from gfparch import GFPGANv1Clean
net = GFPGANv1Clean(out_size=512, num_style_feat=512, channel_multiplier=2, decoder_load_path=None,
    fix_decoder=False, num_mlp=8, input_is_latent=True, different_w=True, narrow=1, sft_half=True)
ck = torch.load('../GFPGANv1.4.pth', map_location='cpu', weights_only=True)
sd = ck['params_ema'] if 'params_ema' in ck else ck['params']
missing, unexpected = net.load_state_dict(sd, strict=False)
print('missing', missing[:5], len(missing), 'unexpected', unexpected[:5], len(unexpected))
net.eval()
from sg2 import ModulatedConv2d
with torch.no_grad():
    for mod in net.modules():
        if type(mod).__name__ == 'StyleConv': mod.skip_noise = True
        if isinstance(mod, ModulatedConv2d): mod.register_buffer('w2', (mod.weight[0] ** 2).sum([2, 3]).clone())
class Wrap(torch.nn.Module):
    def __init__(s, n): super().__init__(); s.n = n
    def forward(s, x):  # NHWC [-1,1]
        y = s.n(x.permute(0, 3, 1, 2), return_rgb=False, randomize_noise=False)[0]
        return y.clamp(-1, 1).permute(0, 2, 3, 1)
w = Wrap(net).eval()
torch.save(w.state_dict(), 'wrap.pt')
print('params', sum(p.numel() for p in w.parameters())/1e6, 'M')
