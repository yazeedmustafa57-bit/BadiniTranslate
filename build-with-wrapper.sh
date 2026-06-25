#!/bin/bash
set -e

export JAVA_HOME=/root/tools/jdk
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/root/tools/android

echo "=== Step 1: Wrap all aapt2 binaries in Gradle cache ==="

# Create the universal wrapper script
cat > /tmp/aapt2-manual-wrapper.sh << 'WRAPPER'
#!/bin/bash
# universal aapt2 wrapper for QEMU ARM64
AAPT2_REAL="$(dirname "$0")/aapt2.orig"
export LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64
QEMU_BASE="/usr/bin/qemu-x86_64 -E LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64 -L /root/tools/x86_64-sysroot"

# daemon mode: use Python daemon
if [ "${1:-}" = "daemon" ]; then
    exec python3 -c "
import sys, os, struct, subprocess

def read_n(n):
    b = b''
    while len(b) < n:
        c = os.read(sys.stdin.buffer.fileno(), n - len(b))
        if not c: raise EOFError()
        b += c
    return b

sys.stdout.buffer.write(b'Ready\n')
sys.stdout.buffer.flush()

while True:
    try:
        h = read_n(4)
        l = struct.unpack('<I', h)[0]
        if l == 0 or l > 1048576: break
        cmd = read_n(l).decode('utf-8', errors='replace').strip()
        if not cmd or cmd == 'quit': break
        
        parts = cmd.split()
        qemu_r = '/root/tools/android/build-tools/35.0.0/aapt2'
        qemu = ['/usr/bin/qemu-x86_64', '-E', 'LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64', '-L', '/root/tools/x86_64-sysroot', qemu_r]
        r = subprocess.run(qemu + parts, capture_output=True, timeout=120)
        
        resp = struct.pack('<III', len(r.stdout), len(r.stderr), r.returncode)
        sys.stdout.buffer.write(resp)
        if r.stdout: sys.stdout.buffer.write(r.stdout)
        if r.stderr: sys.stdout.buffer.write(r.stderr)
        sys.stdout.buffer.flush()
    except: break

sys.stdout.buffer.write(b'Exiting daemon\n')
sys.stdout.buffer.flush()
"
fi

# direct mode
exec $QEMU_BASE "$AAPT2_REAL" "$@"
WRAPPER
chmod +x /tmp/aapt2-manual-wrapper.sh

# Now wrap ALL aapt2 binaries found in cache
WRAPPED=0
find /root/.gradle/caches -name "aapt2" -type f 2>/dev/null | while read f; do
    if file "$f" 2>/dev/null | grep -q "x86-64"; then
        dir=$(dirname "$f")
        if [ -f "$dir/aapt2.orig" ]; then
            echo "  Already has orig: $f"
        else
            mv "$f" "$dir/aapt2.orig"
            cp /tmp/aapt2-manual-wrapper.sh "$f"
            chmod +x "$f"
            echo "  Wrapped: $f"
        fi
    fi
done

echo "=== Step 2: Build APK ==="
cd /root/BadiniTranslate
rm -rf app/build

./gradlew assembleRelease -x lint --no-daemon 2>&1

echo "=== Step 3: Check result ==="
find app/build/outputs -name "*.apk" 2>/dev/null
