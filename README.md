# Default methods JVM crash reproducer

Requires JDK 21. From this directory:

```bash
./run.sh
```

The script builds `out/repro.jar` and runs it for up to 60 seconds. A successful reproduction is a `SIGSEGV` in `DefaultMethods::generate_default_methods`. The crash log is written to `out/logs/`. Exit code 0 with the message `no crash` means the bug did not reproduce.
