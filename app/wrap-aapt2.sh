#!/bin/bash
GRADLE_USER_HOME="${HOME}/.gradle"
find "$GRADLE_USER_HOME/caches" -name "aapt2" -path "*/aapt2-*-linux/aapt2" -type f 2>/dev/null | while read AAPT2; do
  REAL="${AAPT2}.real"
  if [ -f "$AAPT2" ] && [ ! -f "$REAL" ] && ! head -1 "$AAPT2" | grep -q "qemu"; then
    mv "$AAPT2" "$REAL"
    cat > "$AAPT2" << 'WRAPPER'
#!/bin/bash
DIR="$(dirname "$0")"
REAL="${DIR}/aapt2.real"
export LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64
exec /usr/bin/qemu-x86_64 -E LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64 -L /root/tools/x86_64-sysroot "$REAL" "$@"
WRAPPER
    chmod +x "$AAPT2"
    echo "Wrapped: $AAPT2"
  fi
done
