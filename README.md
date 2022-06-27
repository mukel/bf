# bf
Truffle Brainf*ck (bytecode) interpreter.

### Build
A few configurations are provided: `jvm`, `jvm-ce`, `jvm-ee`, `native-ce` `native-ee`.
The `jvm-*` run on `jargraal` instead of `libgral` to avoid re-building `libjvmcicompiler.so` every time.

```bash
mx --env native-ee build
```

### Run
```bash
mx --env native-ee bf --experimental-options --log.level=ALL --engine.Mode=latency --engine.BackgroundCompilation=false --engine.CompileImmediately=true --engine.TraceCompilationDetails demos/mandelbrot.bf
```

`--engine.BackgroundCompilation=false --engine.CompileImmediately=true` ensures that BF programs are fully compiled before execution.  
`--engine.Mode=latency` is **strongly recommended** to avoid abysmal compilation times due to optimization phases with pathological non-linear complexity.  
`--log.level=ALL` logs the time taken to execute a BF program.
`--engine.TraceCompilationDetails` logs compilation events including time taken by partial evaluation and compilation.  
`--engine.Compilation=false` disables compilation, useful to test interpreter performance.

### Debug
To attach the debugger to run in JVM mode as follows: 
```bash
mx --env jvm-ee build
mx --env jvm-ee bf --experimental-options --vm.agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y --log.level=ALL --engine.Mode=latency --engine.BackgroundCompilation=false --engine.CompileImmediately=true --engine.TraceCompilationDetails demos/mandelbrot.bf
```