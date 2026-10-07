package ru.opengd77.satupdate;

/** Process-local handoff; the project is also atomically saved to private app storage. */
final class CodeplugSession {
    private CodeplugSession() {}
    static volatile CodeplugModel current;
    static volatile CodeplugProject project;
    static void install(CodeplugProject value){project=value;current=value==null?null:value.model();}
}
