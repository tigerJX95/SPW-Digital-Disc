package io.github.tigerjx95.spwcdprobe;
/** Native-pixel boundary double. Never packaged in the plugin. */
final class DiscImage implements AutoCloseable {
 int closes;
 public void close(){if(++closes>1)throw new AssertionError("double close");}
}
