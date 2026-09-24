package ru.opengd77.satupdate;

/** Process-local handoff to the read-only viewer. */
final class CodeplugSession {
    private CodeplugSession() {}
    static volatile CodeplugModel current;
}
