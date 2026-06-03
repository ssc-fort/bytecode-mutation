| Code-Level Property | XZ Utils | SolarWinds | Shai-Hulud | event-stream | Axios |
|---|---|---|---|---|---|
| **1. Data exfil / Network IO** | Passive (via SSH) | DNS DGA + HTTP C2 | **GitHub API + HTTP** | **HTTP POST** | curl/PS download + RAT C2 |
| **2. File system access** | Build-time only | Registry R/W, timestamps | **.npmrc, .env, .git-creds** | Wallet data, module injection | Temp files, self-cleanup, dir enum |
| **3. Crypto API usage** | **ChaCha20 + Ed448** | **FNV-1a + XOR hashing** | XOR + Base64 (wave 2+) | AES256 (targeting) | XOR + reversed Base64 |
| **4. System/env recon** | Arch, toolchain, **env vars** | Hostname, WMI, processes, registry | CI env vars, TruffleHog | npm*package*description, argv | **os.platform()** |
| **5. Process/memory manip** | **IFUNC hooking, system().** | Process monitoring, source swap | child_process spawn | eval() | execSync, in-memory PE inject |
| **6. Reflection/classloading** | **IFUNC (analogous)** | **.NET assembly loading** | **eval(), dynamic require()** | **Multi-stage eval()** | Obfuscated require(), indirect eval |
| **7. Obfuscation** | Test file disguise, stego | Naming mimicry, traffic blend | **Base64+XOR, split atob** | Minification, silent errors | **Reversed B64+XOR** |