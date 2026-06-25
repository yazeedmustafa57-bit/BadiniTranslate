#!/bin/bash
# Wrapper script for aapt2 on ARM64
AAPT2_REAL="$(dirname "$0")/aapt2.orig"
export LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64
QEMU="/usr/bin/qemu-x86_64 -E LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64 -L /root/tools/x86_64-sysroot"

if [ "${1:-}" = "daemon" ]; then
    exec python3 -c "
import sys,os,struct,subprocess as sp
def rn(n):
 b=b''
 while len(b)<n:
  c=os.read(sys.stdin.buffer.fileno(),n-len(b))
  if not c: raise EOFError()
  b+=c
 return b
sys.stdout.buffer.write(b'Ready\n')
sys.stdout.buffer.flush()
while True:
 try:
  h=rn(4)
  l=struct.unpack('<I',h)[0]
  if l<1 or l>1048576: break
  cmd=rn(l).decode('utf-8',errors='replace').strip()
  if not cmd or cmd=='quit': break
  r=sp.run(['/usr/bin/qemu-x86_64','-E','LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64','-L','/root/tools/x86_64-sysroot','/root/tools/android/build-tools/35.0.0/aapt2']+cmd.split(),capture_output=True,timeout=120)
  sys.stdout.buffer.write(struct.pack('<III',len(r.stdout),len(r.stderr),r.returncode))
  if r.stdout: sys.stdout.buffer.write(r.stdout)
  if r.stderr: sys.stdout.buffer.write(r.stderr)
  sys.stdout.buffer.flush()
 except: break
sys.stdout.buffer.write(b'Exiting daemon\n')
sys.stdout.buffer.flush()
"
fi

exec $QEMU "$AAPT2_REAL" "$@"
