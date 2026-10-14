package repro;

/**
 * The only type that gets redefined. run.sh compiles a second copy where
 * {@code return 1} is {@code return 2}, so the old method is freed.
 */
public interface A {
  default int value() { return 1; }
}
