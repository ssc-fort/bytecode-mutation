## Data exfiltration / Network IO


### Structure

Each code injection pattern is described by a short code snippet. The code snippet may include variables, such as `<name>`. 

Those are to be instantiated as follows:

- `<name>` can be a constant, such as `"foo.txt"`
- `<name>` can be a string method argument, such as `$1` or `$2`. This should be informed by the signature of the method to be mutated. 
- `<name>` is a non-string method argument, such as  `$0.toString()` (if `$0` is an object) or `$1.get(0).toString()` (if `$1` is a list) etc
- `<name>` can be a heap reference (i.e. reference to a field), such as  `$0.name`

Note that for constant generation, we may want to use a [quickcheck - style generator](https://pholser.github.io/junit-quickcheck/site/1.0/usage/other-types.html) (and can reuse existing generators from [this library](https://pholser.github.io/junit-quickcheck)).  I.e. we would have generators for "file names", "IP addresses" etc.  

We should avoid to always wrap code in `try-catch` as this may lead to overfitting when we use this for training data. However, there are operations that may run into a `SecurityException` like `File::delete`. Here it would make some sense to sometimes catch those, or some supertype. This could be another variation point. 

Code snippets should always use fully qualified type names as we will compile them into bytecode anyway.

The first line in each code snippet on a comment with an generated id for this snippet.


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





### Crypto API usage



### System/Environment Reconnaissance 



### Process/Memory Manipulation



### Reflection/Classloading



### Obfuscation

