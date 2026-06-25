// Task to wrap all aapt2 binaries in Gradle cache before resource processing
tasks.register("wrapAapt2") {
    doLast {
        val cacheDirs = listOf(
            file("/root/.gradle/caches/transforms-3"),
            file("/root/.gradle/caches/8.4/transforms")
        )
        
        for (cacheDir in cacheDirs) {
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().forEach { file ->
                    if (file.name == "aapt2" && file.isFile && file.canExecute()) {
                        val firstBytes = file.readBytes().take(4).toByteArray()
                        // Check if it's an ELF file (starts with 0x7f 'E' 'L' 'F')
                        if (firstBytes.size == 4 && firstBytes[0] == 0x7f.toByte() && 
                            firstBytes[1] == 'E'.code.toByte() && firstBytes[2] == 'L'.code.toByte()) {
                            
                            val origFile = file.resolveSibling("aapt2.orig")
                            if (!origFile.exists()) {
                                file.copyTo(origFile)
                                println("Backed up: ${file.path}")
                                
                                // Write wrapper script
                                val wrapperScript = """#!/bin/bash
AAPT2_REAL="$(dirname "$$0")/aapt2.orig"
export LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64
QEMU="/usr/bin/qemu-x86_64 -E LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64 -L /root/tools/x86_64-sysroot"

if [ "$${1:-}" = "daemon" ]; then
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
        r=sp.run(['$QEMU_BASE'.split() if False else '/usr/bin/qemu-x86_64','-E','LD_LIBRARY_PATH=/root/tools/x86_64-sysroot/lib64','-L','/root/tools/x86_64-sysroot',
            '/root/tools/android/build-tools/35.0.0/aapt2']+cmd.split(),capture_output=True,timeout=120)
        sys.stdout.buffer.write(struct.pack('<III',len(r.stdout),len(r.stderr),r.returncode))
        if r.stdout: sys.stdout.buffer.write(r.stdout)
        if r.stderr: sys.stdout.buffer.write(r.stderr)
        sys.stdout.buffer.flush()
    except: break
sys.stdout.buffer.write(b'Exiting daemon\n')
sys.stdout.buffer.flush()
"
fi

exec $$QEMU "$$AAPT2_REAL" "$$@"
"""
                                file.writeBytes(wrapperScript.toByteArray())
                                file.setExecutable(true)
                                println("Wrapped: ${file.path}")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Make sure our task runs before resource merging
tasks.matching { it.name.contains("mergeReleaseResources") }.configureEach {
    dependsOn("wrapAapt2")
}
